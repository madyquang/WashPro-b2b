package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Quy trình mới: một đơn đi suốt từ lúc khách yêu cầu lấy đồ đến khi hoàn thành.
 * Chờ lấy đồ -> Tài xế đang đến lấy -> Đã tiếp nhận -> Đang giặt -> Đã giặt xong -> Đang giao trả -> Hoàn thành.
 *
 * - Bảng orders có thêm thông tin yêu cầu lấy đồ, tài xế giao trả, lúc tiếp nhận, lý do hủy.
 * - Yêu cầu lấy đồ đã tạo đơn: chép thông tin yêu cầu vào đơn đó; lúc tạo đơn = lúc khách gửi yêu cầu.
 * - Yêu cầu chưa lấy / đã hủy: thành đơn ở trạng thái chờ lấy (đơn hủy là đơn đã xóa mềm, giữ lý do).
 * - Đơn đang chờ khách xác nhận / có sai lệch: chuyển sang "Đã tiếp nhận" (khách không còn phải xác nhận trên ứng
 *   dụng; tài xế xác nhận số lượng khi nhận đồ, xưởng bấm "Bắt đầu giặt").
 * Sau khi chép hết, bỏ bảng pickup_requests.
 */
public class V10__gop_yeu_cau_lay_do_vao_don extends BaseJavaMigration {

    private static final String SYSTEM = "he-thong";

    @Override
    public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        try (Statement st = c.createStatement()) {
            st.execute("""
                    ALTER TABLE orders
                      MODIFY driver_id bigint NULL,
                      ADD COLUMN delivery_driver_id bigint NULL,
                      ADD COLUMN requested_by varchar(50) COLLATE utf8mb4_unicode_ci NULL,
                      ADD COLUMN preferred_date date NULL,
                      ADD COLUMN time_slot varchar(20) COLLATE utf8mb4_unicode_ci NULL,
                      ADD COLUMN estimated_bags int NULL,
                      ADD COLUMN pickup_note varchar(500) COLLATE utf8mb4_unicode_ci NULL,
                      ADD COLUMN received_at datetime(6) NULL,
                      ADD COLUMN cancel_reason varchar(500) COLLATE utf8mb4_unicode_ci NULL,
                      ADD COLUMN cancelled_by varchar(50) COLLATE utf8mb4_unicode_ci NULL,
                      ADD COLUMN cancelled_at datetime(6) NULL,
                      ADD KEY idx_orders_delivery_driver (delivery_driver_id),
                      ADD KEY idx_orders_status (status),
                      ADD CONSTRAINT fk_orders_delivery_driver FOREIGN KEY (delivery_driver_id) REFERENCES users (id)
                    """);

            // Đơn cũ: tài xế tạo đơn lúc nhận đồ, cùng tài xế giao trả
            st.executeUpdate("UPDATE orders SET received_at = created_at");
            st.executeUpdate("""
                    UPDATE orders SET delivery_driver_id = driver_id
                    WHERE status IN ('DELIVERING', 'DELIVERY_ISSUE', 'COMPLETED')
                    """);
            st.executeUpdate("""
                    INSERT INTO order_history (order_id, from_status, to_status, actor, note, created_at)
                    SELECT id, status, 'RECEIVED', '%s',
                           'Chuyển sang quy trình mới: không cần khách xác nhận trên ứng dụng, xưởng bấm "Bắt đầu giặt" khi đồ về xưởng',
                           NOW(6)
                    FROM orders WHERE status IN ('PENDING_CONFIRM', 'DISCREPANCY')
                    """.formatted(SYSTEM));
            st.executeUpdate("UPDATE orders SET status = 'RECEIVED' WHERE status IN ('PENDING_CONFIRM', 'DISCREPANCY')");

            // Yêu cầu đã tạo đơn: thông tin yêu cầu chép vào đơn
            st.executeUpdate("""
                    UPDATE orders o JOIN pickup_requests p ON p.order_id = o.id
                    SET o.requested_by = p.requested_by, o.preferred_date = p.preferred_date,
                        o.time_slot = p.time_slot, o.estimated_bags = p.estimated_bags, o.pickup_note = p.note,
                        o.created_at = LEAST(o.created_at, p.created_at)
                    """);
        }
        movePendingPickups(c);
        try (Statement st = c.createStatement()) {
            st.execute("DROP TABLE pickup_requests");
        }
    }

    private record Pickup(long id, long customerId, Long driverId, String status, String requestedBy,
            java.sql.Date preferredDate, String timeSlot, Integer estimatedBags, String note, String cancelReason,
            String cancelledBy, Timestamp createdAt, Timestamp updatedAt) {
    }

    /** Yêu cầu chưa lấy / đã hủy thành đơn chờ lấy, kèm một dòng lịch sử. */
    private static void movePendingPickups(Connection c) throws Exception {
        List<Pickup> rows = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("""
                SELECT id, customer_id, driver_id, status, requested_by, preferred_date, time_slot, estimated_bags,
                       note, cancel_reason, cancelled_by, created_at, updated_at
                FROM pickup_requests WHERE order_id IS NULL AND status IN ('OPEN', 'ACCEPTED', 'CANCELLED')
                ORDER BY id
                """)) {
            while (rs.next()) {
                long driver = rs.getLong("driver_id");
                Integer bags = rs.getInt("estimated_bags");
                if (rs.wasNull()) {
                    bags = null;
                }
                rows.add(new Pickup(rs.getLong("id"), rs.getLong("customer_id"), driver == 0 ? null : driver,
                        rs.getString("status"), rs.getString("requested_by"), rs.getDate("preferred_date"),
                        rs.getString("time_slot"), bags, rs.getString("note"), rs.getString("cancel_reason"),
                        rs.getString("cancelled_by"), rs.getTimestamp("created_at"), rs.getTimestamp("updated_at")));
            }
        }

        try (PreparedStatement order = c.prepareStatement("""
                INSERT INTO orders (customer_id, driver_id, status, is_deleted, requested_by, preferred_date, time_slot,
                                    estimated_bags, pickup_note, cancel_reason, cancelled_by, cancelled_at,
                                    created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS);
                PreparedStatement history = c.prepareStatement("""
                        INSERT INTO order_history (order_id, from_status, to_status, actor, note, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
            for (Pickup p : rows) {
                boolean cancelled = p.status().equals("CANCELLED");
                String status = p.driverId() == null ? "PICKUP_PENDING" : "PICKUP_ASSIGNED";
                order.setLong(1, p.customerId());
                order.setObject(2, p.driverId());
                order.setString(3, status);
                order.setBoolean(4, cancelled);
                order.setString(5, p.requestedBy());
                order.setDate(6, p.preferredDate());
                order.setString(7, p.timeSlot());
                order.setObject(8, p.estimatedBags());
                order.setString(9, p.note());
                order.setString(10, p.cancelReason());
                order.setString(11, p.cancelledBy());
                order.setTimestamp(12, cancelled ? p.updatedAt() : null);
                order.setTimestamp(13, p.createdAt());
                order.setTimestamp(14, p.updatedAt());
                order.executeUpdate();
                long orderId;
                try (ResultSet keys = order.getGeneratedKeys()) {
                    keys.next();
                    orderId = keys.getLong(1);
                }

                history.setLong(1, orderId);
                history.setString(2, null);
                history.setString(3, "PICKUP_PENDING");
                history.setString(4, p.requestedBy() != null ? p.requestedBy() : SYSTEM);
                history.setString(5, "Yêu cầu lấy đồ (chuyển từ yêu cầu #" + p.id() + ")");
                history.setTimestamp(6, p.createdAt());
                history.executeUpdate();
                if (p.driverId() != null) {
                    history.setLong(1, orderId);
                    history.setString(2, "PICKUP_PENDING");
                    history.setString(3, "PICKUP_ASSIGNED");
                    history.setString(4, SYSTEM);
                    history.setString(5, "Đã có tài xế nhận việc lấy đồ");
                    history.setTimestamp(6, p.updatedAt());
                    history.executeUpdate();
                }
                if (cancelled) {
                    history.setLong(1, orderId);
                    history.setString(2, status);
                    history.setString(3, status);
                    history.setString(4, p.cancelledBy() != null ? p.cancelledBy() : SYSTEM);
                    history.setString(5, "Hủy yêu cầu lấy đồ. Lý do: "
                            + (p.cancelReason() != null ? p.cancelReason() : "Không nêu lý do"));
                    history.setTimestamp(6, p.updatedAt());
                    history.executeUpdate();
                }
            }
        }
    }
}

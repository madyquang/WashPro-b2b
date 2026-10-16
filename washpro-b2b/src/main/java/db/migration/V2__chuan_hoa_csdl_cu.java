package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Đưa CSDL tạo bởi Hibernate (ddl-auto=update, trước khi dùng Flyway) về đúng cấu trúc V1.
 * Viết bằng Java vì tên ràng buộc CHECK do MySQL tự đặt, có thể khác nhau giữa các máy, và có thể đã bị gỡ một phần.
 * Chạy được nhiều lần, không ảnh hưởng CSDL tạo mới từ V1 (không có gì để gỡ). Thay cho LegacyDataRunner trước đây.
 */
public class V2__chuan_hoa_csdl_cu extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement st = context.getConnection().createStatement()) {
            // 1. Gỡ mọi ràng buộc CHECK Hibernate từng sinh cho cột enum: chúng chặn việc thêm trạng thái mới
            //    (đã từng làm hỏng việc ghi đơn "Sự cố giao trả"). Giá trị do enum trong ứng dụng kiểm soát.
            List<String[]> checks = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("""
                    SELECT TABLE_NAME, CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS
                    WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_TYPE = 'CHECK'
                    """)) {
                while (rs.next()) {
                    checks.add(new String[] { rs.getString(1), rs.getString(2) });
                }
            }
            for (String[] c : checks) {
                st.execute("ALTER TABLE `" + c[0] + "` DROP CHECK `" + c[1] + "`");
            }

            // 2. Vai trò lưu dạng chữ (trước là kiểu ENUM của MySQL): đổi / thêm vai trò không cần sửa kiểu cột
            st.execute("ALTER TABLE users MODIFY role VARCHAR(30) NOT NULL");

            // 3. Đơn hoàn thành từ trước khi có cột completed_at: lấy mốc hoàn thành từ nhật ký đơn
            st.executeUpdate("""
                    UPDATE orders o
                    JOIN (SELECT order_id, MAX(created_at) AS done_at FROM order_history
                          WHERE to_status = 'COMPLETED' GROUP BY order_id) h ON h.order_id = o.id
                    SET o.completed_at = h.done_at
                    WHERE o.status = 'COMPLETED' AND o.completed_at IS NULL
                    """);
        }
    }
}

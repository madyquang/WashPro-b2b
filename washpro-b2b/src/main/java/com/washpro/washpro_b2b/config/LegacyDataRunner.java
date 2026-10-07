package com.washpro.washpro_b2b.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Bổ sung dữ liệu cho đơn tạo từ phiên bản cũ, để người dùng không phải tự chạy thêm script SQL.
 * Chỉ đụng tới các dòng còn trống nên chạy mỗi lần khởi động cũng an toàn.
 */
@Component
@Order(1)
public class LegacyDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyDataRunner.class);

    private final JdbcTemplate jdbc;

    public LegacyDataRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        dropOutdatedStatusChecks();
        // Mốc hoàn thành (để tính hạn thanh toán, tuổi nợ) lấy từ nhật ký lúc đơn chuyển sang COMPLETED
        int filled = jdbc.update("""
                UPDATE orders o
                JOIN (SELECT order_id, MAX(created_at) AS done_at FROM order_history
                      WHERE to_status = 'COMPLETED' GROUP BY order_id) h ON h.order_id = o.id
                SET o.completed_at = h.done_at
                WHERE o.status = 'COMPLETED' AND o.completed_at IS NULL
                """);
        if (filled > 0) {
            log.info("Đã bổ sung ngày hoàn thành cho {} đơn cũ", filled);
        }
    }

    /**
     * Hibernate từng tạo ràng buộc CHECK cho cột trạng thái với danh sách trạng thái cũ, và không tự cập nhật khi
     * thêm trạng thái mới (DELIVERY_ISSUE) → ghi đơn "Sự cố giao trả" bị CSDL từ chối. Gỡ các ràng buộc lỗi thời đó;
     * giá trị trạng thái vẫn được kiểm soát bởi enum OrderStatus trong ứng dụng.
     */
    private void dropOutdatedStatusChecks() {
        try {
            List<Map<String, Object>> checks = jdbc.queryForList("""
                    SELECT tc.TABLE_NAME AS t, tc.CONSTRAINT_NAME AS c, cc.CHECK_CLAUSE AS clause
                    FROM information_schema.TABLE_CONSTRAINTS tc
                    JOIN information_schema.CHECK_CONSTRAINTS cc
                      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                    WHERE tc.CONSTRAINT_SCHEMA = DATABASE() AND tc.CONSTRAINT_TYPE = 'CHECK'
                      AND tc.TABLE_NAME IN ('orders', 'order_history')
                    """);
            for (Map<String, Object> row : checks) {
                String clause = String.valueOf(row.get("clause"));
                if (clause.contains("PENDING_CONFIRM") && !clause.contains("DELIVERY_ISSUE")) {
                    jdbc.execute("ALTER TABLE `" + row.get("t") + "` DROP CHECK `" + row.get("c") + "`");
                    log.info("Đã gỡ ràng buộc trạng thái cũ {}.{}", row.get("t"), row.get("c"));
                }
            }
        } catch (DataAccessException e) {
            // CSDL không hỗ trợ CHECK (MySQL < 8.0.16) thì cũng không có ràng buộc cần gỡ
            log.debug("Bỏ qua kiểm tra ràng buộc CHECK: {}", e.getMessage());
        }
    }
}

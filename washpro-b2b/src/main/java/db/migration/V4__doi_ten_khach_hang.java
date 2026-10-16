package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Đổi tên "restaurant" thành "customer" (khách hàng gồm nhà hàng, khách sạn, spa, công ty...):
 * bảng restaurants -> customers, cột restaurant_id -> customer_id, vai trò RESTAURANT_* -> CUSTOMER_*.
 * Khóa ngoại và chỉ mục đi theo bảng / cột nên không phải tạo lại. Mọi phiên đăng nhập cũ bị thu hồi
 * (giao diện cũ còn lưu mã vai trò cũ), người dùng đăng nhập lại.
 * Viết bằng Java vì CSDL cũ (tạo bởi Hibernate trước khi dùng Flyway) có thể không có chỉ mục duy nhất đặt tên sẵn.
 */
public class V4__doi_ten_khach_hang extends BaseJavaMigration {

    private static final String[] TABLES_WITH_CUSTOMER = { "users", "customer_prices", "orders", "payments",
            "pickup_requests" };

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement st = context.getConnection().createStatement()) {
            st.execute("RENAME TABLE restaurants TO customers");
            for (String table : TABLES_WITH_CUSTOMER) {
                st.execute("ALTER TABLE " + table + " RENAME COLUMN restaurant_id TO customer_id");
            }
            if (indexExists(st, "customer_prices", "uk_customer_prices_restaurant_price")) {
                st.execute("ALTER TABLE customer_prices RENAME INDEX uk_customer_prices_restaurant_price"
                        + " TO uk_customer_prices_customer_price");
            }
            st.executeUpdate("UPDATE users SET role = 'CUSTOMER_STAFF' WHERE role = 'RESTAURANT_STAFF'");
            st.executeUpdate("UPDATE users SET role = 'CUSTOMER_MANAGER' WHERE role = 'RESTAURANT_MANAGER'");
            st.executeUpdate("UPDATE users SET token_version = token_version + 1");
        }
    }

    private static boolean indexExists(Statement st, String table, String index) throws Exception {
        try (ResultSet rs = st.executeQuery("""
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '%s' AND INDEX_NAME = '%s'
                """.formatted(table, index))) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }
}

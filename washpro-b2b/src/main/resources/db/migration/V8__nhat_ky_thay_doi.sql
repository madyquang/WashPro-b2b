-- Nhật ký thay đổi: ai đổi gì (giá, tiền, hạn, hủy, khách hàng, tài khoản), lúc nào, giá trị cũ / mới.
-- Không có khóa ngoại: nhật ký giữ nguyên kể cả khi dữ liệu gốc thay đổi về sau. Chỉ thêm, không sửa / xóa.
CREATE TABLE `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `actor` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_role` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `action` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_id` bigint DEFAULT NULL,
  `customer_id` bigint DEFAULT NULL,
  `summary` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `details` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_log_created` (`created_at`),
  KEY `idx_audit_log_category_created` (`category`, `created_at`),
  KEY `idx_audit_log_customer` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

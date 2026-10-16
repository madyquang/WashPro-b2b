-- Ảnh / chữ ký đính kèm đơn (khi lấy đồ, khi giao trả, đồ thiếu hỏng). File lưu trên đĩa (app.uploads.dir),
-- bảng chỉ giữ thông tin file. Không bắt buộc với đơn nào.
CREATE TABLE `order_attachments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `kind` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `stored_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `size_bytes` bigint NOT NULL,
  `uploaded_by` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_attachments_order` (`order_id`),
  CONSTRAINT `fk_order_attachments_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

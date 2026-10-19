-- Số lượng khách tự khai khi gửi yêu cầu lấy đồ. Giữ nguyên để đối chiếu; số thực nhận (tài xế soát tại chỗ)
-- nằm ở order_items và là số dùng để giặt, tính tiền.
CREATE TABLE `order_declared_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `service_price_id` bigint DEFAULT NULL,
  `item_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `service_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'WASH',
  `unit` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PIECE',
  `quantity` decimal(12,2) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_declared_items_order` (`order_id`),
  CONSTRAINT `fk_order_declared_items_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_order_declared_items_price` FOREIGN KEY (`service_price_id`) REFERENCES `service_prices` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

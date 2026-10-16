-- Cấu trúc ban đầu (khớp các entity JPA tại thời điểm chuyển sang Flyway).
-- CSDL đã có dữ liệu từ trước được đánh dấu baseline = 1 (không chạy lại file này), xem application.properties.
-- Không dùng ràng buộc CHECK cho cột trạng thái / loại: giá trị do enum trong ứng dụng kiểm soát,
-- để thêm trạng thái mới không bị CSDL từ chối.

CREATE TABLE `restaurants` (
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `payment_term_days` int NOT NULL DEFAULT '0',
  `turnaround_hours` int NOT NULL DEFAULT '48',
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `customer_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RESTAURANT',
  `contact_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `users` (
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `restaurant_id` bigint DEFAULT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`),
  KEY `FKsd7jrkn6c8wb1y8ffbdn3krel` (`restaurant_id`),
  CONSTRAINT `FKsd7jrkn6c8wb1y8ffbdn3krel` FOREIGN KEY (`restaurant_id`) REFERENCES `restaurants` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `service_prices` (
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `unit_price` decimal(15,2) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `unit` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OTHER',
  `service_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'WASH',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_prices_name_service` (`name`,`service_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `customer_prices` (
  `unit_price` decimal(15,2) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `restaurant_id` bigint NOT NULL,
  `service_price_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_prices_restaurant_price` (`restaurant_id`,`service_price_id`),
  KEY `FK4073nhs5tssolauew4akgwbh0` (`service_price_id`),
  CONSTRAINT `FK4073nhs5tssolauew4akgwbh0` FOREIGN KEY (`service_price_id`) REFERENCES `service_prices` (`id`),
  CONSTRAINT `FK4sjo4pf3ilsm8rq53abcds3h1` FOREIGN KEY (`restaurant_id`) REFERENCES `restaurants` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `orders` (
  `is_deleted` bit(1) DEFAULT NULL,
  `total_bags` int DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `driver_id` bigint NOT NULL,
  `due_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `restaurant_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `FK1ehpd5jgscedxal38ltsqj4h6` (`driver_id`),
  KEY `FK2m9qulf12xm537bku3jnrrbup` (`restaurant_id`),
  CONSTRAINT `FK1ehpd5jgscedxal38ltsqj4h6` FOREIGN KEY (`driver_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK2m9qulf12xm537bku3jnrrbup` FOREIGN KEY (`restaurant_id`) REFERENCES `restaurants` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `order_items` (
  `quantity` decimal(12,2) NOT NULL,
  `unit_price` decimal(15,2) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `service_price_id` bigint DEFAULT NULL,
  `unit` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PIECE',
  `category` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OTHER',
  `service_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'WASH',
  `item_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKbioxgbv59vetrxe0ejfubep1w` (`order_id`),
  KEY `FK4d2s11h4dal193lrd7kya0skq` (`service_price_id`),
  CONSTRAINT `FK4d2s11h4dal193lrd7kya0skq` FOREIGN KEY (`service_price_id`) REFERENCES `service_prices` (`id`),
  CONSTRAINT `FKbioxgbv59vetrxe0ejfubep1w` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `order_history` (
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `from_status` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `to_status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `note` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `FKnw2ljd8jnpdc9y2ild52e79t2` (`order_id`),
  CONSTRAINT `FKnw2ljd8jnpdc9y2ild52e79t2` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payments` (
  `amount` decimal(15,2) NOT NULL,
  `paid_date` date NOT NULL,
  `voided` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `restaurant_id` bigint NOT NULL,
  `voided_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `voided_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `void_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKpl4y1ws7g86t91uh5dftfhl1o` (`restaurant_id`),
  CONSTRAINT `FKpl4y1ws7g86t91uh5dftfhl1o` FOREIGN KEY (`restaurant_id`) REFERENCES `restaurants` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pickup_requests` (
  `estimated_bags` int DEFAULT NULL,
  `preferred_date` date NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `driver_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint DEFAULT NULL,
  `restaurant_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `time_slot` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `cancelled_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `requested_by` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `cancel_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK9d5cmrrodaeg7d2vgln88yqko` (`order_id`),
  KEY `FKslsw4nso5jpikkrh91bn974e4` (`driver_id`),
  KEY `FKboowuxugott7gw5bigh23up15` (`restaurant_id`),
  CONSTRAINT `FKboowuxugott7gw5bigh23up15` FOREIGN KEY (`restaurant_id`) REFERENCES `restaurants` (`id`),
  CONSTRAINT `FKnala6eadwt1icax0jie5wx5jv` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `FKslsw4nso5jpikkrh91bn974e4` FOREIGN KEY (`driver_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

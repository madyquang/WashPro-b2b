-- Thông báo đẩy (Web Push): thiết bị đã bật nhận thông báo của từng người dùng.
CREATE TABLE `push_subscriptions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `endpoint` varchar(700) COLLATE utf8mb4_unicode_ci NOT NULL,
  `p256dh` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `auth` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_agent` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_push_subscriptions_endpoint` (`endpoint`),
  KEY `idx_push_subscriptions_user` (`user_id`),
  CONSTRAINT `fk_push_subscriptions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Khóa / bí mật do hệ thống tự sinh và cần giữ nguyên qua các lần khởi động (VD khóa VAPID của Web Push).
CREATE TABLE `app_secrets` (
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `secret_value` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

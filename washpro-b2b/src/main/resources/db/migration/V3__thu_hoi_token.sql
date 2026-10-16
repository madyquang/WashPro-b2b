-- Phiên bản token của từng tài khoản: tăng khi đổi / đặt lại mật khẩu hoặc "đăng xuất mọi thiết bị",
-- mọi token phát ra trước đó (mang số cũ) lập tức hết hiệu lực.
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;

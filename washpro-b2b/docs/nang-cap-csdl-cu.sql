-- Chạy MỘT LẦN nếu bạn đang dùng CSDL cũ (đã có dữ liệu từ phiên bản trước), TRƯỚC khi khởi động ứng dụng.
-- Nếu CSDL còn trống/dữ liệu thử nghiệm, cách đơn giản hơn là xóa database và để ứng dụng tự tạo lại.
-- Các câu lệnh viết để chạy lại nhiều lần cũng không gây lỗi.

-- Chọn database của ứng dụng (đổi tên nếu DB_URL trong .env dùng database khác).
USE washpro_b2b;

-- MySQL Workbench mặc định bật "safe update" (chặn UPDATE không lọc theo khóa chính, lỗi 1175).
-- Tắt tạm trong phiên này, bật lại ở cuối script.
SET SQL_SAFE_UPDATES = 0;

-- ===== Phiên bản 2: trạng thái đơn =====

-- 1) Cột trạng thái đơn trước đây là kiểu ENUM của MySQL, không chứa trạng thái mới DELIVERING.
ALTER TABLE orders MODIFY status VARCHAR(30) NOT NULL;

-- 2) Đơn cũ ở trạng thái DRAFT (nếu có) không còn được dùng: chuyển về chờ xác nhận.
UPDATE orders SET status = 'PENDING_CONFIRM' WHERE status = 'DRAFT';

-- ===== Phiên bản 3: nhiều loại khách hàng, chăn ga / đồng phục, tính theo kg =====

-- 3) Số lượng đồ giặt cho phép số lẻ (tính theo kg). Hibernate không tự đổi kiểu cột đã có.
ALTER TABLE order_items MODIFY quantity DECIMAL(12,2) NOT NULL;

-- 4) Bảng giá: trước đây tên là duy nhất; nay cùng một tên được có nhiều dịch vụ
--    (ví dụ "Ga giường đôi" giặt sấy và giặt + là ủi). Bỏ ràng buộc duy nhất cũ trên riêng cột name.
SET @idx := (SELECT s.INDEX_NAME FROM information_schema.STATISTICS s
             WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'service_prices'
               AND s.NON_UNIQUE = 0 AND s.INDEX_NAME <> 'PRIMARY'
               AND s.INDEX_NAME <> 'uk_service_prices_name_service'
             GROUP BY s.INDEX_NAME
             HAVING COUNT(*) = 1 AND MAX(s.COLUMN_NAME) = 'name'
             LIMIT 1);
SET @sql := IF(@idx IS NULL, 'SELECT 1', CONCAT('ALTER TABLE service_prices DROP INDEX `', @idx, '`'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5) Đơn vị tính trước đây là chữ tự do ("cái", "kg"...). Ứng dụng vẫn đọc được giá trị cũ,
--    câu lệnh này chỉ chuẩn hóa về mã mới cho gọn dữ liệu.
UPDATE service_prices SET unit = CASE LOWER(TRIM(unit))
    WHEN 'kg' THEN 'KG' WHEN 'bộ' THEN 'SET' WHEN 'đôi' THEN 'PAIR' ELSE 'PIECE' END
WHERE unit NOT IN ('PIECE', 'SET', 'PAIR', 'KG');

SET SQL_SAFE_UPDATES = 1;

-- Các cột/bảng mới (restaurants.customer_type, service_prices.category / service_type,
-- order_items.service_price_id / category / service_type / unit, bảng customer_prices)
-- được Hibernate tự thêm khi khởi động (JPA_DDL_AUTO=update), dữ liệu cũ nhận giá trị mặc định:
-- khách hàng cũ là "Nhà hàng", mục bảng giá và dòng đồ cũ thuộc nhóm "Khác", dịch vụ "Giặt sấy", đơn vị "cái".
-- Sau khi khởi động, vào menu Bảng giá để xếp lại nhóm đồ / dịch vụ cho các mục cũ.

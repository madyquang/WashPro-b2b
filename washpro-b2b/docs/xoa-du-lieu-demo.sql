-- Xóa TOÀN BỘ dữ liệu demo do DEMO_DATA=true sinh ra (khách hàng, đơn, thanh toán, yêu cầu lấy đồ, tài khoản demo).
-- Không đụng tới dữ liệu thật và bảng giá. Chạy được nhiều lần.

USE washpro_b2b;
SET SQL_SAFE_UPDATES = 0;

CREATE TEMPORARY TABLE demo_customers AS
SELECT id FROM restaurants WHERE name IN (
  'Khách sạn Hoa Sen', 'Khách sạn Biển Ngọc', 'Nhà hàng Biển Xanh', 'Nhà hàng Phố Cổ',
  'Spa Ngọc Lan', 'Công ty May Sao Mai', 'Phòng khám Đa khoa An Tâm', 'Homestay Đồi Thông');

CREATE TEMPORARY TABLE demo_orders AS
SELECT id FROM orders WHERE restaurant_id IN (SELECT id FROM demo_customers);

DELETE FROM pickup_requests WHERE restaurant_id IN (SELECT id FROM demo_customers);
DELETE FROM order_history WHERE order_id IN (SELECT id FROM demo_orders);
DELETE FROM order_items WHERE order_id IN (SELECT id FROM demo_orders);
DELETE FROM orders WHERE id IN (SELECT id FROM demo_orders);
DELETE FROM payments WHERE restaurant_id IN (SELECT id FROM demo_customers);
DELETE FROM customer_prices WHERE restaurant_id IN (SELECT id FROM demo_customers);
DELETE FROM users WHERE restaurant_id IN (SELECT id FROM demo_customers)
   OR username IN ('taixe.tai', 'taixe.hung', 'taixe.phuc', 'ketoan.mai');
DELETE FROM restaurants WHERE id IN (SELECT id FROM demo_customers);

DROP TEMPORARY TABLE demo_orders;
DROP TEMPORARY TABLE demo_customers;
SET SQL_SAFE_UPDATES = 1;

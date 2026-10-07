-- Bảng giá MẪU để bắt đầu nhanh (không bắt buộc). Giá chỉ để tham khảo, hãy sửa lại ở menu Bảng giá.
-- Chạy sau khi ứng dụng đã khởi động ít nhất một lần (để bảng service_prices đã được tạo).
-- Mục nào đã có (trùng tên + dịch vụ) sẽ được bỏ qua.
-- Lỗi "Unknown column 'category'" nghĩa là backend phiên bản mới chưa được khởi động lần nào.

USE washpro_b2b;

INSERT IGNORE INTO service_prices (name, category, service_type, unit, unit_price, active) VALUES
-- Đồ bàn ăn & bếp
('Khăn trải bàn',            'TABLE_LINEN', 'WASH_IRON',     'PIECE', 12000, true),
('Khăn ăn',                  'TABLE_LINEN', 'WASH_IRON',     'PIECE',  3000, true),
('Tạp dề',                   'TABLE_LINEN', 'WASH',          'PIECE',  5000, true),
('Khăn lau bếp',             'TABLE_LINEN', 'WASH',          'PIECE',  2000, true),
('Khăn trải bàn',            'TABLE_LINEN', 'STAIN_REMOVAL', 'PIECE', 18000, true),
-- Chăn ga gối
('Ga giường đơn',            'BEDDING',     'WASH_IRON',     'PIECE', 15000, true),
('Ga giường đôi',            'BEDDING',     'WASH_IRON',     'PIECE', 20000, true),
('Vỏ chăn',                  'BEDDING',     'WASH_IRON',     'PIECE', 25000, true),
('Vỏ gối',                   'BEDDING',     'WASH_IRON',     'PIECE',  5000, true),
('Ruột chăn',                'BEDDING',     'WASH',          'PIECE', 60000, true),
('Chăn ga gối (tính cân)',   'BEDDING',     'WASH',          'KG',    22000, true),
-- Khăn tắm & khăn mặt
('Khăn tắm',                 'TOWEL',       'WASH',          'PIECE',  6000, true),
('Khăn mặt',                 'TOWEL',       'WASH',          'PIECE',  2500, true),
('Áo choàng tắm',            'TOWEL',       'WASH',          'PIECE', 20000, true),
('Thảm chân',                'TOWEL',       'WASH',          'PIECE',  8000, true),
-- Đồng phục
('Áo đồng phục',             'UNIFORM',     'WASH_IRON',     'PIECE', 12000, true),
('Quần đồng phục',           'UNIFORM',     'WASH_IRON',     'PIECE', 12000, true),
('Bộ đồng phục',             'UNIFORM',     'WASH_IRON',     'SET',   22000, true),
('Áo đầu bếp',               'UNIFORM',     'STAIN_REMOVAL', 'PIECE', 18000, true),
('Áo vest / đồng phục lễ tân', 'UNIFORM',   'DRY_CLEAN',     'PIECE', 45000, true),
('Đồng phục (tính cân)',     'UNIFORM',     'WASH',          'KG',    25000, true);

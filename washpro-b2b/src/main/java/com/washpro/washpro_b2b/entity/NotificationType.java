package com.washpro.washpro_b2b.entity;

/** Loại thông báo (giao diện dùng để chọn biểu tượng / màu). */
public enum NotificationType {
    PICKUP_NEW, // Có yêu cầu lấy đồ mới
    PICKUP_ACCEPTED, // Tài xế đã nhận / được phân công lấy đồ
    PICKUP_ASSIGNED, // Tài xế được quản lý xưởng phân công
    PICKUP_CANCELLED, // Yêu cầu lấy đồ bị hủy
    ORDER_TO_CONFIRM, // Đơn mới chờ khách xác nhận số lượng
    ORDER_DISCREPANCY, // Có báo sai lệch số lượng
    ORDER_DELIVERING, // Đồ sạch đang được giao
    ORDER_RECEIPT_ISSUE, // Khách báo sự cố khi nhận đồ
    ORDER_CANCELLED, // Đơn bị hủy
    PAYMENT_RECORDED, // Xưởng ghi nhận thanh toán
    OVERDUE_ORDERS, // Bản tin hằng ngày: đơn trễ hạn trả
    OVERDUE_DEBT // Bản tin hằng ngày: nợ quá hạn
}

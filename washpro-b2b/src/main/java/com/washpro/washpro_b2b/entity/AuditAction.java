package com.washpro.washpro_b2b.entity;

/** Loại thay đổi được ghi nhật ký, theo nhóm để lọc. */
public enum AuditAction {
    PRICE_CREATED(Category.PRICE, "Thêm mục bảng giá"),
    PRICE_UPDATED(Category.PRICE, "Sửa bảng giá"),
    PRICE_HIDDEN(Category.PRICE, "Ẩn mục bảng giá"),
    PRICE_SHOWN(Category.PRICE, "Áp dụng lại mục bảng giá"),
    CUSTOMER_PRICE_SET(Category.PRICE, "Đặt giá riêng"),
    CUSTOMER_PRICE_REMOVED(Category.PRICE, "Bỏ giá riêng"),
    PAYMENT_RECORDED(Category.PAYMENT, "Ghi nhận thanh toán"),
    PAYMENT_VOIDED(Category.PAYMENT, "Hủy khoản thanh toán"),
    ORDER_ITEMS_UPDATED(Category.ORDER, "Sửa chi tiết / đơn giá đơn"),
    ORDER_DUE_CHANGED(Category.ORDER, "Đổi hạn trả"),
    ORDER_CANCELLED(Category.ORDER, "Hủy đơn"),
    ORDER_COMPLETED_BY_WORKSHOP(Category.ORDER, "Xưởng chốt hoàn thành"),
    CUSTOMER_CREATED(Category.CUSTOMER, "Thêm khách hàng"),
    CUSTOMER_UPDATED(Category.CUSTOMER, "Sửa thông tin khách hàng"),
    CUSTOMER_DEACTIVATED(Category.CUSTOMER, "Ngừng hợp tác"),
    CUSTOMER_REACTIVATED(Category.CUSTOMER, "Hợp tác lại"),
    USER_CREATED(Category.ACCOUNT, "Tạo tài khoản"),
    USER_LOCKED(Category.ACCOUNT, "Khóa tài khoản"),
    USER_UNLOCKED(Category.ACCOUNT, "Mở khóa tài khoản"),
    USER_PASSWORD_RESET(Category.ACCOUNT, "Đặt lại mật khẩu");

    public enum Category {
        PRICE, PAYMENT, ORDER, CUSTOMER, ACCOUNT
    }

    private final Category category;
    private final String label;

    AuditAction(Category category, String label) {
        this.category = category;
        this.label = label;
    }

    public Category getCategory() {
        return category;
    }

    public String getLabel() {
        return label;
    }
}

package com.washpro.washpro_b2b.entity;

public enum Role {
    DRIVER, // Tài xế: tạo đơn, báo sai lệch đơn của mình
    WORKSHOP_ADMIN, // Quản lý xưởng: điều phối đơn, quản lý đối tác/tài khoản
    WORKSHOP_ACCOUNTANT, // Kế toán xưởng: bảng giá, chi tiết đồ giặt, thanh toán, công nợ
    RESTAURANT_STAFF, // Nhân viên khách hàng: xác nhận đơn của khách hàng mình
    RESTAURANT_MANAGER; // Quản lý khách hàng: như nhân viên + xem công nợ của khách hàng mình

    public boolean isRestaurantRole() {
        return this == RESTAURANT_STAFF || this == RESTAURANT_MANAGER;
    }
}

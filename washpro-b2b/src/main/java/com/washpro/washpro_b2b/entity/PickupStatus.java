package com.washpro.washpro_b2b.entity;

/** Trạng thái yêu cầu lấy đồ: OPEN -> ACCEPTED -> DONE (đã tạo đơn), hoặc CANCELLED. */
public enum PickupStatus {
    OPEN("Chờ tài xế nhận"),
    ACCEPTED("Tài xế đã nhận"),
    DONE("Đã lấy đồ"),
    CANCELLED("Đã hủy");

    private final String label;

    PickupStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isActive() {
        return this == OPEN || this == ACCEPTED;
    }
}

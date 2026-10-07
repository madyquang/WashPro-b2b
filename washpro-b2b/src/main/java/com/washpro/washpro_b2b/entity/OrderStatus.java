package com.washpro.washpro_b2b.entity;

/**
 * Vòng đời của một đơn giặt:
 * PENDING_CONFIRM -> WASHING -> DELIVERING -> COMPLETED
 * Nếu lệch số lượng lúc nhận: PENDING_CONFIRM <-> DISCREPANCY -> WASHING
 * Nếu thiếu/hỏng lúc giao trả: DELIVERING -> DELIVERY_ISSUE -> (giao bù) DELIVERING hoặc (chốt lại) COMPLETED
 */
public enum OrderStatus {
    PENDING_CONFIRM("Chờ xác nhận"), // Tài xế đã tạo đơn, chờ khách hàng xác nhận số lượng
    DISCREPANCY("Có sai lệch"), // Số lượng bị báo lệch, chờ khách hàng chốt lại
    WASHING("Đang giặt"), // Khách hàng đã chốt, xưởng đang giặt
    DELIVERING("Đang giao"), // Giặt xong, đang giao trả, chờ khách xác nhận đã nhận
    DELIVERY_ISSUE("Sự cố giao trả"), // Khách báo thiếu/hỏng khi nhận, chờ xưởng xử lý
    COMPLETED("Hoàn thành"); // Khách đã nhận đủ (hoặc xưởng chốt), tính vào công nợ

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Đơn còn đang xử lý (chưa hoàn thành): dùng để tính trễ hạn trả đồ. */
    public boolean isOpen() {
        return this != COMPLETED;
    }

    /** Quy tắc chuyển trạng thái hợp lệ (không cho lùi, không cho nhảy bước). */
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING_CONFIRM -> next == WASHING || next == DISCREPANCY;
            case DISCREPANCY -> next == PENDING_CONFIRM || next == WASHING;
            case WASHING -> next == DELIVERING;
            case DELIVERING -> next == COMPLETED || next == DELIVERY_ISSUE;
            case DELIVERY_ISSUE -> next == DELIVERING || next == COMPLETED;
            case COMPLETED -> false;
        };
    }
}

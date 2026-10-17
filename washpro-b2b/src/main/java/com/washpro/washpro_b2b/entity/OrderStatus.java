package com.washpro.washpro_b2b.entity;

import java.util.Arrays;
import java.util.List;

/**
 * Vòng đời của một đơn giặt, từ lúc khách yêu cầu lấy đồ:
 * PICKUP_PENDING -> PICKUP_ASSIGNED -> RECEIVED -> WASHING -> WASHED -> DELIVERING -> COMPLETED
 * Tài xế trả lại việc: PICKUP_ASSIGNED -> PICKUP_PENDING, DELIVERING -> WASHED.
 * Khách báo thiếu/hỏng khi nhận: DELIVERING -> DELIVERY_ISSUE -> (giao bù) DELIVERING hoặc (chốt lại) COMPLETED.
 */
public enum OrderStatus {
    PICKUP_PENDING("Chờ lấy đồ"), // Khách gửi yêu cầu, chưa có tài xế
    PICKUP_ASSIGNED("Tài xế đang đến lấy"), // Đã có tài xế nhận / được phân công
    RECEIVED("Đã tiếp nhận"), // Tài xế đã kiểm số lượng, khách ký nhận; đồ đang về xưởng
    WASHING("Đang giặt"), // Xưởng đã nhận đồ, đang giặt
    WASHED("Đã giặt xong"), // Giặt xong, đã kiểm lại số lượng, chờ tài xế giao
    DELIVERING("Đang giao trả"), // Tài xế đang giao trả
    DELIVERY_ISSUE("Sự cố giao trả"), // Khách báo thiếu/hỏng khi nhận, chờ xưởng xử lý
    COMPLETED("Hoàn thành"), // Đã giao xong, tính vào công nợ
    /** Quy trình cũ (khách xác nhận số lượng trên ứng dụng): chỉ còn trong lịch sử của đơn cũ. */
    PENDING_CONFIRM("Chờ khách xác nhận"),
    /** Quy trình cũ: chỉ còn trong lịch sử của đơn cũ. */
    DISCREPANCY("Có sai lệch");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Các trạng thái của quy trình hiện tại, theo thứ tự (bỏ trạng thái cũ). */
    public static List<OrderStatus> flow() {
        return Arrays.stream(values()).filter(s -> !s.isLegacy()).toList();
    }

    public boolean isLegacy() {
        return this == PENDING_CONFIRM || this == DISCREPANCY;
    }

    /** Đơn còn đang xử lý (chưa hoàn thành): dùng để tính trễ hạn trả đồ. */
    public boolean isOpen() {
        return this != COMPLETED;
    }

    /** Đơn còn là yêu cầu lấy đồ (tài xế chưa nhận đồ). */
    public boolean isPickupStage() {
        return this == PICKUP_PENDING || this == PICKUP_ASSIGNED;
    }

    /** Quy tắc chuyển trạng thái hợp lệ (không nhảy bước; chỉ lùi khi tài xế trả lại việc). */
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PICKUP_PENDING -> next == PICKUP_ASSIGNED || next == RECEIVED;
            case PICKUP_ASSIGNED -> next == PICKUP_PENDING || next == RECEIVED;
            case RECEIVED -> next == WASHING;
            case WASHING -> next == WASHED;
            case WASHED -> next == DELIVERING;
            case DELIVERING -> next == COMPLETED || next == DELIVERY_ISSUE || next == WASHED;
            case DELIVERY_ISSUE -> next == DELIVERING || next == COMPLETED;
            case COMPLETED, PENDING_CONFIRM, DISCREPANCY -> false;
        };
    }
}

package com.washpro.washpro_b2b.entity;

/** Loại ảnh / chữ ký đính kèm đơn. */
public enum AttachmentKind {
    PICKUP_PHOTO("Ảnh lúc lấy đồ"),
    PICKUP_SIGNATURE("Chữ ký khách khi giao đồ bẩn"),
    DELIVERY_PHOTO("Ảnh lúc giao trả"),
    DELIVERY_SIGNATURE("Chữ ký khách khi nhận đồ sạch"),
    ISSUE_PHOTO("Ảnh đồ thiếu / hỏng");

    private final String label;

    AttachmentKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Vai trò nào được đính kèm loại nào. Kế toán chỉ xem. */
    public boolean allowedFor(Role role) {
        return switch (role) {
            case WORKSHOP_ADMIN -> true;
            case DRIVER -> this != ISSUE_PHOTO;
            case CUSTOMER_STAFF, CUSTOMER_MANAGER -> this == ISSUE_PHOTO;
            case WORKSHOP_ACCOUNTANT -> false;
        };
    }
}

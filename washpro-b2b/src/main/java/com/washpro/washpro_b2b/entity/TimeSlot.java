package com.washpro.washpro_b2b.entity;

/** Khung giờ khách muốn tài xế đến lấy đồ. */
public enum TimeSlot {
    MORNING("Sáng (7h–11h)"),
    AFTERNOON("Chiều (13h–17h)"),
    EVENING("Tối (17h–21h)"),
    ANYTIME("Giờ nào cũng được");

    private final String label;

    TimeSlot(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

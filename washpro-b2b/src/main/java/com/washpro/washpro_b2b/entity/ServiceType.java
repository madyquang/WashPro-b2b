package com.washpro.washpro_b2b.entity;

/** Cách xử lý một món đồ. Cùng một loại đồ có thể có giá khác nhau theo từng dịch vụ. */
public enum ServiceType {
    WASH("Giặt sấy"),
    WASH_IRON("Giặt + là ủi"),
    DRY_CLEAN("Giặt hấp"),
    STAIN_REMOVAL("Giặt + tẩy vết bẩn");

    private final String label;

    ServiceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

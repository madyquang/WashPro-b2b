package com.washpro.washpro_b2b.entity;

/** Loại khách hàng của xưởng. */
public enum CustomerType {
    RESTAURANT("Nhà hàng"),
    HOTEL("Khách sạn / Homestay"),
    SPA("Spa / Thẩm mỹ viện"),
    COMPANY("Công ty / Nhà máy"),
    HOSPITAL("Bệnh viện / Phòng khám"),
    OTHER("Khác");

    private final String label;

    CustomerType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

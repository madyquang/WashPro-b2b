package com.washpro.washpro_b2b.entity;

/** Nhóm đồ giặt: dùng để sắp xếp bảng giá, phiếu giao nhận và báo cáo. */
public enum ItemCategory {
    TABLE_LINEN("Đồ bàn ăn & bếp"), // Khăn bàn, khăn ăn, tạp dề, khăn lau bếp
    BEDDING("Chăn ga gối"), // Ga giường, vỏ chăn, vỏ gối, ruột chăn
    TOWEL("Khăn tắm & khăn mặt"), // Khăn tắm, khăn mặt, áo choàng tắm, thảm chân
    UNIFORM("Đồng phục"), // Áo, quần, áo đầu bếp, đồng phục lễ tân
    OTHER("Khác"); // Rèm, thảm, đồ ngoài danh mục

    private final String label;

    ItemCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

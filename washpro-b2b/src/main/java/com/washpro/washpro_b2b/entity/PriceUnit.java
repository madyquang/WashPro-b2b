package com.washpro.washpro_b2b.entity;

import java.math.BigDecimal;

/** Đơn vị tính tiền. Chỉ tính theo kg mới được nhập số lẻ (ví dụ 12,5 kg chăn ga). */
public enum PriceUnit {
    PIECE("cái", false),
    SET("bộ", false),
    PAIR("đôi", false),
    KG("kg", true);

    private final String label;
    private final boolean allowsDecimal;

    PriceUnit(String label, boolean allowsDecimal) {
        this.label = label;
        this.allowsDecimal = allowsDecimal;
    }

    public String getLabel() {
        return label;
    }

    public boolean allowsDecimal() {
        return allowsDecimal;
    }

    /** Số lượng hợp lệ với đơn vị: đơn vị đếm (cái, bộ, đôi) phải là số nguyên. */
    public boolean accepts(BigDecimal quantity) {
        return allowsDecimal || quantity.stripTrailingZeros().scale() <= 0;
    }

    /**
     * Đọc giá trị từ CSDL. Phiên bản cũ lưu đơn vị dạng chữ tự do ("cái", "kg", "bộ"...),
     * nên vẫn nhận các giá trị đó để dữ liệu cũ đọc được mà không cần chuyển đổi.
     */
    public static PriceUnit fromDb(String value) {
        if (value == null || value.isBlank()) {
            return PIECE;
        }
        String v = value.trim();
        for (PriceUnit u : values()) {
            if (u.name().equalsIgnoreCase(v) || u.label.equalsIgnoreCase(v)) {
                return u;
            }
        }
        return PIECE;
    }
}

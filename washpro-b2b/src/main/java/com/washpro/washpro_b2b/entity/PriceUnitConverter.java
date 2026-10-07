package com.washpro.washpro_b2b.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Lưu mã đơn vị (PIECE, KG...) và đọc được cả giá trị chữ tự do của phiên bản cũ. */
@Converter
public class PriceUnitConverter implements AttributeConverter<PriceUnit, String> {

    @Override
    public String convertToDatabaseColumn(PriceUnit unit) {
        return unit == null ? PriceUnit.PIECE.name() : unit.name();
    }

    @Override
    public PriceUnit convertToEntityAttribute(String value) {
        return PriceUnit.fromDb(value);
    }
}

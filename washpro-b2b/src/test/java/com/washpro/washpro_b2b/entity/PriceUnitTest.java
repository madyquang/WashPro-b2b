package com.washpro.washpro_b2b.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceUnitTest {

    @Test
    void tinhTheoKgDuocNhapSoLe() {
        assertTrue(PriceUnit.KG.accepts(new BigDecimal("12.5")));
        assertTrue(PriceUnit.KG.accepts(new BigDecimal("3")));
    }

    @Test
    void donViDemPhaiLaSoNguyen() {
        assertTrue(PriceUnit.PIECE.accepts(new BigDecimal("20")));
        assertTrue(PriceUnit.PIECE.accepts(new BigDecimal("20.00")));
        assertFalse(PriceUnit.PIECE.accepts(new BigDecimal("2.5")));
        assertFalse(PriceUnit.SET.accepts(new BigDecimal("1.5")));
        assertFalse(PriceUnit.PAIR.accepts(new BigDecimal("0.5")));
    }

    @Test
    void docDuocDonViChuTuDoCuaPhienBanTruoc() {
        assertEquals(PriceUnit.PIECE, PriceUnit.fromDb("cái"));
        assertEquals(PriceUnit.KG, PriceUnit.fromDb("Kg"));
        assertEquals(PriceUnit.SET, PriceUnit.fromDb(" bộ "));
        assertEquals(PriceUnit.PAIR, PriceUnit.fromDb("PAIR"));
        assertEquals(PriceUnit.PIECE, PriceUnit.fromDb("chiếc"));
        assertEquals(PriceUnit.PIECE, PriceUnit.fromDb(null));
    }
}

package com.washpro.washpro_b2b.entity;

import org.junit.jupiter.api.Test;

import static com.washpro.washpro_b2b.entity.OrderStatus.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStatusTest {

    @Test
    void luongChinhDiDungThuTu() {
        assertTrue(PENDING_CONFIRM.canTransitionTo(WASHING));
        assertTrue(WASHING.canTransitionTo(DELIVERING));
        assertTrue(DELIVERING.canTransitionTo(COMPLETED));
    }

    @Test
    void luongSaiLech() {
        assertTrue(PENDING_CONFIRM.canTransitionTo(DISCREPANCY));
        assertTrue(DISCREPANCY.canTransitionTo(PENDING_CONFIRM));
        assertTrue(DISCREPANCY.canTransitionTo(WASHING));
    }

    @Test
    void luongSuCoGiaoTra() {
        assertTrue(DELIVERING.canTransitionTo(DELIVERY_ISSUE));
        assertTrue(DELIVERY_ISSUE.canTransitionTo(DELIVERING));
        assertTrue(DELIVERY_ISSUE.canTransitionTo(COMPLETED));
        assertFalse(DELIVERY_ISSUE.canTransitionTo(WASHING));
        assertFalse(WASHING.canTransitionTo(DELIVERY_ISSUE));
    }

    @Test
    void khongChoNhayBuocHoacLuiTrangThai() {
        assertFalse(PENDING_CONFIRM.canTransitionTo(DELIVERING));
        assertFalse(PENDING_CONFIRM.canTransitionTo(COMPLETED));
        assertFalse(WASHING.canTransitionTo(PENDING_CONFIRM));
        assertFalse(WASHING.canTransitionTo(COMPLETED));
        assertFalse(DELIVERING.canTransitionTo(WASHING));
    }

    @Test
    void donHoanThanhLaKetThuc() {
        for (OrderStatus next : OrderStatus.values()) {
            assertFalse(COMPLETED.canTransitionTo(next), "COMPLETED không được chuyển sang " + next);
        }
    }
}

package com.washpro.washpro_b2b.entity;

import org.junit.jupiter.api.Test;

import static com.washpro.washpro_b2b.entity.OrderStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStatusTest {

    @Test
    void luongChinhDiDungThuTu() {
        assertTrue(PICKUP_PENDING.canTransitionTo(PICKUP_ASSIGNED));
        assertTrue(PICKUP_ASSIGNED.canTransitionTo(RECEIVED));
        assertTrue(RECEIVED.canTransitionTo(WASHING));
        assertTrue(WASHING.canTransitionTo(WASHED));
        assertTrue(WASHED.canTransitionTo(DELIVERING));
        assertTrue(DELIVERING.canTransitionTo(COMPLETED));
    }

    @Test
    void taiXeTraLaiViec() {
        assertTrue(PICKUP_ASSIGNED.canTransitionTo(PICKUP_PENDING));
        assertTrue(DELIVERING.canTransitionTo(WASHED));
    }

    @Test
    void xuongNhanDoTaiXuongKhongCanTaiXe() {
        assertTrue(PICKUP_PENDING.canTransitionTo(RECEIVED));
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
        assertFalse(PICKUP_PENDING.canTransitionTo(WASHING));
        assertFalse(RECEIVED.canTransitionTo(WASHED));
        assertFalse(WASHING.canTransitionTo(DELIVERING));
        assertFalse(WASHING.canTransitionTo(COMPLETED));
        assertFalse(WASHED.canTransitionTo(COMPLETED));
        assertFalse(WASHING.canTransitionTo(RECEIVED));
    }

    @Test
    void donHoanThanhVaTrangThaiCuLaKetThuc() {
        for (OrderStatus next : OrderStatus.values()) {
            assertFalse(COMPLETED.canTransitionTo(next), "COMPLETED không được chuyển sang " + next);
            assertFalse(PENDING_CONFIRM.canTransitionTo(next), "trạng thái cũ không được dùng tiếp");
        }
    }

    @Test
    void quyTrinhKhongCoTrangThaiCu() {
        assertEquals(8, OrderStatus.flow().size());
        assertFalse(OrderStatus.flow().contains(DISCREPANCY));
    }
}

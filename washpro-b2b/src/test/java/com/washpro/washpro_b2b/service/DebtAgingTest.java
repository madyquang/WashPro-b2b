package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.service.DebtAging.Receivable;
import com.washpro.washpro_b2b.service.DebtAging.Result;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DebtAgingTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    private static Receivable due(int daysAgo, long amount) {
        return new Receivable(TODAY.minusDays(daysAgo), BigDecimal.valueOf(amount));
    }

    private static void assertMoney(long expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual), "mong đợi " + expected + " nhưng là " + actual);
    }

    @Test
    void chiaDungNhomTuoiNo() {
        Result r = DebtAging.compute(List.of(
                due(-5, 100), // còn 5 ngày mới đến hạn
                due(0, 50), // đến hạn hôm nay: chưa quá hạn
                due(10, 200),
                due(45, 300),
                due(90, 400)), BigDecimal.ZERO, TODAY);
        assertMoney(150, r.notDue());
        assertMoney(200, r.overdue1to30());
        assertMoney(300, r.overdue31to60());
        assertMoney(400, r.overdueOver60());
        assertMoney(900, r.overdueTotal());
        assertEquals(TODAY.minusDays(90), r.oldestUnpaidDue());
    }

    @Test
    void tienTraDuocTruVaoKhoanCuNhatTruoc() {
        // Trả 450: hết khoản 90 ngày (400) và 50 của khoản 45 ngày
        Result r = DebtAging.compute(List.of(due(10, 200), due(90, 400), due(45, 300)),
                BigDecimal.valueOf(450), TODAY);
        assertMoney(0, r.overdueOver60());
        assertMoney(250, r.overdue31to60());
        assertMoney(200, r.overdue1to30());
        assertEquals(TODAY.minusDays(45), r.oldestUnpaidDue());
    }

    @Test
    void traDuThiKhongConNo() {
        Result r = DebtAging.compute(List.of(due(10, 200), due(90, 400)), BigDecimal.valueOf(600), TODAY);
        assertMoney(0, r.notDue());
        assertMoney(0, r.overdueTotal());
        assertNull(r.oldestUnpaidDue());
    }
}

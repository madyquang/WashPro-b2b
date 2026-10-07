package com.washpro.washpro_b2b.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Phân tích tuổi nợ của một khách hàng.
 * Mỗi đơn hoàn thành là một khoản phải thu có hạn thanh toán = ngày hoàn thành + kỳ thanh toán của khách.
 * Tiền khách đã trả được trừ dần vào khoản có hạn sớm nhất trước (FIFO), phần còn lại chia theo số ngày quá hạn.
 */
public final class DebtAging {

    private DebtAging() {
    }

    public record Receivable(LocalDate dueDate, BigDecimal amount) {
    }

    /**
     * @param notDue          chưa đến hạn thanh toán
     * @param overdue1to30    quá hạn 1–30 ngày
     * @param overdue31to60   quá hạn 31–60 ngày
     * @param overdueOver60   quá hạn trên 60 ngày
     * @param oldestUnpaidDue hạn thanh toán sớm nhất còn chưa trả hết (null nếu đã trả đủ)
     */
    public record Result(BigDecimal notDue, BigDecimal overdue1to30, BigDecimal overdue31to60,
            BigDecimal overdueOver60, LocalDate oldestUnpaidDue) {

        public BigDecimal overdueTotal() {
            return overdue1to30.add(overdue31to60).add(overdueOver60);
        }
    }

    public static Result compute(List<Receivable> receivables, BigDecimal paid, LocalDate today) {
        BigDecimal remainingPaid = paid == null ? BigDecimal.ZERO : paid;
        BigDecimal notDue = BigDecimal.ZERO;
        BigDecimal b30 = BigDecimal.ZERO;
        BigDecimal b60 = BigDecimal.ZERO;
        BigDecimal over60 = BigDecimal.ZERO;
        LocalDate oldest = null;

        List<Receivable> sorted = receivables.stream()
                .sorted(Comparator.comparing(Receivable::dueDate))
                .toList();
        for (Receivable r : sorted) {
            BigDecimal open = r.amount();
            if (remainingPaid.signum() > 0) {
                BigDecimal used = remainingPaid.min(open);
                open = open.subtract(used);
                remainingPaid = remainingPaid.subtract(used);
            }
            if (open.signum() <= 0) {
                continue;
            }
            if (oldest == null) {
                oldest = r.dueDate();
            }
            long daysLate = ChronoUnit.DAYS.between(r.dueDate(), today);
            if (daysLate <= 0) {
                notDue = notDue.add(open);
            } else if (daysLate <= 30) {
                b30 = b30.add(open);
            } else if (daysLate <= 60) {
                b60 = b60.add(open);
            } else {
                over60 = over60.add(open);
            }
        }
        return new Result(notDue, b30, b60, over60, oldest);
    }
}

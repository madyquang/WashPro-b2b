package com.washpro.washpro_b2b.dto;

import com.washpro.washpro_b2b.entity.CustomerType;
import com.washpro.washpro_b2b.entity.Payment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class FinanceDtos {

    private FinanceDtos() {
    }

    public record PaymentRequest(
            @NotNull(message = "Vui lòng chọn khách hàng") Long customerId,
            @NotNull(message = "Vui lòng nhập số tiền") @DecimalMin(value = "0.01", message = "Số tiền phải lớn hơn 0") @Digits(integer = 12, fraction = 2, message = "Số tiền không hợp lệ") BigDecimal amount,
            @PastOrPresent(message = "Ngày thanh toán không được ở tương lai") LocalDate paidDate,
            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) {
    }

    public record VoidRequest(
            @NotBlank(message = "Bắt buộc nhập lý do hủy khoản thanh toán") @Size(max = 500, message = "Lý do tối đa 500 ký tự") String reason) {
    }

    /** Doanh thu của một tháng (tính theo thời điểm đơn hoàn thành). */
    public record MonthRevenue(String month, long orders, BigDecimal revenue) {
    }

    public record PaymentResponse(
            Long id,
            Long customerId,
            String customerName,
            BigDecimal amount,
            LocalDate paidDate,
            String note,
            String createdBy,
            LocalDateTime createdAt,
            boolean voided,
            String voidedBy,
            String voidReason) {

        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(
                    p.getId(),
                    p.getCustomer().getId(),
                    p.getCustomer().getName(),
                    p.getAmount(),
                    p.getPaidDate(),
                    p.getNote(),
                    p.getCreatedBy(),
                    p.getCreatedAt(),
                    p.isVoided(),
                    p.getVoidedBy(),
                    p.getVoidReason());
        }
    }

    /**
     * Công nợ của một khách hàng: đã tính tiền - đã thanh toán, kèm tuổi nợ
     * (phần còn nợ chia theo số ngày quá hạn thanh toán).
     */
    public record DebtRow(
            Long customerId,
            String customerName,
            CustomerType customerType,
            boolean active,
            int paymentTermDays,
            long completedOrders,
            BigDecimal billed,
            BigDecimal paid,
            BigDecimal balance,
            BigDecimal notDue,
            BigDecimal overdue1to30,
            BigDecimal overdue31to60,
            BigDecimal overdueOver60,
            BigDecimal overdueTotal,
            /** Hạn thanh toán sớm nhất còn nợ (null nếu đã trả đủ). */
            LocalDate oldestUnpaidDue) {
    }

    public record DashboardResponse(
            Map<String, Long> countsByStatus,
            long totalOrders,
            long ordersToday,
            /** Đơn chưa xong mà đã quá hạn trả đồ. */
            long overdueOrders,
            /** Đơn chưa xong, hạn trả trong hôm nay (chưa quá hạn). */
            long dueToday,
            /** Yêu cầu lấy đồ đang chờ (chưa lấy, chưa hủy). */
            long openPickups,
            /** Các số dưới đây là null nếu vai trò hiện tại không được xem tài chính. */
            BigDecimal billed,
            BigDecimal paid,
            BigDecimal balance,
            BigDecimal overdueDebt) {
    }

    // ===== Sao kê công nợ theo tháng =====

    public record StatementOrder(Long id, LocalDateTime completedAt, LocalDate paymentDue, int itemLines,
            BigDecimal amount) {
    }

    public record StatementPayment(Long id, LocalDate paidDate, BigDecimal amount, String note) {
    }

    /** Sao kê: nợ đầu kỳ + phát sinh trong tháng - thanh toán trong tháng = nợ cuối kỳ. */
    public record StatementResponse(
            Long customerId,
            String customerName,
            CustomerType customerType,
            String address,
            int paymentTermDays,
            String month,
            BigDecimal openingBalance,
            List<StatementOrder> orders,
            List<StatementPayment> payments,
            BigDecimal totalBilled,
            BigDecimal totalPaid,
            BigDecimal closingBalance) {
    }
}

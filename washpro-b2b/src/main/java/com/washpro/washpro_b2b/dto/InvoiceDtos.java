package com.washpro.washpro_b2b.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Dữ liệu hóa đơn tháng của một khách: dùng để dựng PDF, và là dữ liệu đầu vào khi nối với nhà cung cấp
 * hóa đơn điện tử sau này (xem EInvoiceGateway).
 */
public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    public record Company(String name, String address, String phone, String taxCode, String bankAccount,
            String bankName, String accountHolder) {
    }

    public record CustomerInfo(Long id, String name, String type, String address, String phone, String contactName,
            int paymentTermDays) {
    }

    /** Một dòng dịch vụ: gộp cùng loại đồ, dịch vụ, đơn vị và đơn giá trong cả tháng. */
    public record Line(String description, String unit, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
    }

    public record OrderRef(Long id, LocalDateTime completedAt, BigDecimal amount) {
    }

    public record InvoiceData(
            /** Số hóa đơn, VD HD-202610-0007 (tháng + mã khách); cũng là nội dung chuyển khoản. */
            String number,
            YearMonth month,
            LocalDate issueDate,
            Company company,
            CustomerInfo customer,
            List<Line> lines,
            List<OrderRef> orders,
            /** Tổng tiền dịch vụ trong tháng (đơn hoàn thành trong tháng). */
            BigDecimal total,
            String totalInWords,
            BigDecimal openingBalance,
            BigDecimal paidInMonth,
            BigDecimal closingBalance) {
    }
}

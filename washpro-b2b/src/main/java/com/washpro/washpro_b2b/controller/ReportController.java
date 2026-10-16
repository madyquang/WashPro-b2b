package com.washpro.washpro_b2b.controller;

import com.washpro.washpro_b2b.dto.FinanceDtos.DashboardResponse;
import com.washpro.washpro_b2b.dto.FinanceDtos.DebtRow;
import com.washpro.washpro_b2b.dto.FinanceDtos.MonthRevenue;
import com.washpro.washpro_b2b.dto.FinanceDtos.StatementResponse;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.dto.InvoiceDtos.PaymentQr;
import com.washpro.washpro_b2b.dto.PerformanceDtos.Report;
import com.washpro.washpro_b2b.service.InvoiceService;
import com.washpro.washpro_b2b.service.PerformanceService;
import com.washpro.washpro_b2b.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final PerformanceService performanceService;
    private final InvoiceService invoiceService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> dashboard() {
        return ResponseEntity.ok(reportService.dashboard());
    }

    @GetMapping("/reports/debts")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'CUSTOMER_MANAGER')")
    public ResponseEntity<List<DebtRow>> debts() {
        return ResponseEntity.ok(reportService.debts());
    }

    /** Hóa đơn tháng (PDF) của một khách. Quản lý phía khách luôn nhận hóa đơn của khách mình. */
    @GetMapping("/reports/invoice")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'CUSTOMER_MANAGER')")
    public ResponseEntity<byte[]> invoice(@RequestParam(required = false) Long customerId,
            @RequestParam String month) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Tháng không hợp lệ (định dạng yyyy-MM)");
        }
        InvoiceService.InvoicePdf pdf = invoiceService.pdf(customerId, ym);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .body(pdf.content());
    }

    /** Mã QR VietQR chuyển khoản số còn phải thanh toán của tháng; 204 nếu không còn nợ / chưa cấu hình ngân hàng. */
    @GetMapping("/reports/payment-qr")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'CUSTOMER_MANAGER')")
    public ResponseEntity<PaymentQr> paymentQr(@RequestParam(required = false) Long customerId,
            @RequestParam String month) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Tháng không hợp lệ (định dạng yyyy-MM)");
        }
        return invoiceService.paymentQr(customerId, ym).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Báo cáo hiệu suất từ ngày from tới hết ngày to (yyyy-MM-dd; mặc định đầu tháng này tới hôm nay). */
    @GetMapping("/reports/performance")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'CUSTOMER_MANAGER')")
    public ResponseEntity<Report> performance(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return ResponseEntity.ok(performanceService.report(from, to));
    }

    @GetMapping("/reports/revenue")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'CUSTOMER_MANAGER')")
    public ResponseEntity<List<MonthRevenue>> revenue(@RequestParam(defaultValue = "6") int months) {
        return ResponseEntity.ok(reportService.revenue(months));
    }

    /** Sao kê công nợ tháng (month dạng yyyy-MM). Quản lý phía khách không cần gửi customerId. */
    @GetMapping("/reports/statement")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'CUSTOMER_MANAGER')")
    public ResponseEntity<StatementResponse> statement(@RequestParam(required = false) Long customerId,
            @RequestParam String month) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Tháng không hợp lệ (định dạng yyyy-MM)");
        }
        return ResponseEntity.ok(reportService.statement(customerId, ym));
    }

    @GetMapping("/reports/debts/export")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'CUSTOMER_MANAGER')")
    public ResponseEntity<byte[]> exportDebts() {
        return csv(reportService.exportDebtsCsv(), "cong-no");
    }

    @GetMapping("/reports/orders/export")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'CUSTOMER_MANAGER')")
    public ResponseEntity<byte[]> exportOrders() {
        return csv(reportService.exportOrdersCsv(), "don-hang");
    }

    @GetMapping("/reports/items/export")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'CUSTOMER_MANAGER')")
    public ResponseEntity<byte[]> exportItems() {
        return csv(reportService.exportItemsCsv(), "chi-tiet-do-giat");
    }

    private ResponseEntity<byte[]> csv(byte[] content, String name) {
        String filename = name + "-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(content);
    }
}

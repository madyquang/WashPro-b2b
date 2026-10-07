package com.washpro.washpro_b2b.controller;

import com.washpro.washpro_b2b.dto.FinanceDtos.DashboardResponse;
import com.washpro.washpro_b2b.dto.FinanceDtos.DebtRow;
import com.washpro.washpro_b2b.dto.FinanceDtos.MonthRevenue;
import com.washpro.washpro_b2b.dto.FinanceDtos.StatementResponse;
import com.washpro.washpro_b2b.exception.ApiException;
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

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> dashboard() {
        return ResponseEntity.ok(reportService.dashboard());
    }

    @GetMapping("/reports/debts")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'RESTAURANT_MANAGER')")
    public ResponseEntity<List<DebtRow>> debts() {
        return ResponseEntity.ok(reportService.debts());
    }

    @GetMapping("/reports/revenue")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'RESTAURANT_MANAGER')")
    public ResponseEntity<List<MonthRevenue>> revenue(@RequestParam(defaultValue = "6") int months) {
        return ResponseEntity.ok(reportService.revenue(months));
    }

    /** Sao kê công nợ tháng (month dạng yyyy-MM). Quản lý phía khách không cần gửi restaurantId. */
    @GetMapping("/reports/statement")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'RESTAURANT_MANAGER')")
    public ResponseEntity<StatementResponse> statement(@RequestParam(required = false) Long restaurantId,
            @RequestParam String month) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Tháng không hợp lệ (định dạng yyyy-MM)");
        }
        return ResponseEntity.ok(reportService.statement(restaurantId, ym));
    }

    @GetMapping("/reports/debts/export")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'RESTAURANT_MANAGER')")
    public ResponseEntity<byte[]> exportDebts() {
        return csv(reportService.exportDebtsCsv(), "cong-no");
    }

    @GetMapping("/reports/orders/export")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'RESTAURANT_MANAGER')")
    public ResponseEntity<byte[]> exportOrders() {
        return csv(reportService.exportOrdersCsv(), "don-hang");
    }

    @GetMapping("/reports/items/export")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'RESTAURANT_MANAGER')")
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

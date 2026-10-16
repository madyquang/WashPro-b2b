package com.washpro.washpro_b2b.controller;

import com.washpro.washpro_b2b.dto.FinanceDtos.PaymentRequest;
import com.washpro.washpro_b2b.dto.FinanceDtos.PaymentResponse;
import com.washpro.washpro_b2b.dto.FinanceDtos.VoidRequest;
import com.washpro.washpro_b2b.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT')")
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.create(request));
    }

    /** Hủy khoản thanh toán ghi nhầm (không xóa, còn dấu vết). */
    @PatchMapping("/{id}/void")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT')")
    public ResponseEntity<PaymentResponse> voidPayment(@PathVariable Long id,
            @Valid @RequestBody VoidRequest request) {
        return ResponseEntity.ok(paymentService.voidPayment(id, request.reason()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'CUSTOMER_MANAGER')")
    public ResponseEntity<List<PaymentResponse>> list(@RequestParam(required = false) Long customerId) {
        return ResponseEntity.ok(paymentService.list(customerId));
    }
}

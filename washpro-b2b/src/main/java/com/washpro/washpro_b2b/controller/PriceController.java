package com.washpro.washpro_b2b.controller;

import com.washpro.washpro_b2b.dto.PriceDtos.CatalogItem;
import com.washpro.washpro_b2b.dto.PriceDtos.CustomerPriceRequest;
import com.washpro.washpro_b2b.dto.PriceDtos.CustomerPriceRow;
import com.washpro.washpro_b2b.dto.PriceDtos.Request;
import com.washpro.washpro_b2b.dto.PriceDtos.Response;
import com.washpro.washpro_b2b.dto.UserDtos.EnabledRequest;
import com.washpro.washpro_b2b.service.PriceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prices")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT')")
public class PriceController {

    private final PriceService priceService;

    @GetMapping
    public ResponseEntity<List<Response>> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(priceService.list(activeOnly));
    }

    /** Danh mục đồ giặt (không kèm giá) để mọi vai trò chọn khi đếm đồ, báo sai lệch. */
    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CatalogItem>> catalog() {
        return ResponseEntity.ok(priceService.catalog());
    }

    @PostMapping
    public ResponseEntity<Response> create(@Valid @RequestBody Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(priceService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Response> update(@PathVariable Long id, @Valid @RequestBody Request request) {
        return ResponseEntity.ok(priceService.update(id, request));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<Response> setActive(@PathVariable Long id, @Valid @RequestBody EnabledRequest request) {
        return ResponseEntity.ok(priceService.setActive(id, request.enabled()));
    }

    // ===== Giá hợp đồng riêng theo khách hàng =====

    @GetMapping("/customers/{customerId}")
    public ResponseEntity<List<CustomerPriceRow>> customerPrices(@PathVariable Long customerId) {
        return ResponseEntity.ok(priceService.customerPrices(customerId));
    }

    @PutMapping("/customers/{customerId}/{servicePriceId}")
    public ResponseEntity<CustomerPriceRow> setCustomerPrice(@PathVariable Long customerId,
            @PathVariable Long servicePriceId, @Valid @RequestBody CustomerPriceRequest request) {
        return ResponseEntity.ok(priceService.setCustomerPrice(customerId, servicePriceId, request.unitPrice()));
    }

    @DeleteMapping("/customers/{customerId}/{servicePriceId}")
    public ResponseEntity<CustomerPriceRow> removeCustomerPrice(@PathVariable Long customerId,
            @PathVariable Long servicePriceId) {
        return ResponseEntity.ok(priceService.removeCustomerPrice(customerId, servicePriceId));
    }
}

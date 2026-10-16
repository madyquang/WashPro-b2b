package com.washpro.washpro_b2b.controller;

import com.washpro.washpro_b2b.dto.CustomerDtos.Request;
import com.washpro.washpro_b2b.dto.CustomerDtos.Response;
import com.washpro.washpro_b2b.dto.UserDtos.EnabledRequest;
import com.washpro.washpro_b2b.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'DRIVER')")
    public ResponseEntity<List<Response>> list() {
        return ResponseEntity.ok(customerService.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('WORKSHOP_ADMIN')")
    public ResponseEntity<Response> create(@Valid @RequestBody Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('WORKSHOP_ADMIN')")
    public ResponseEntity<Response> update(@PathVariable Long id, @Valid @RequestBody Request request) {
        return ResponseEntity.ok(customerService.update(id, request));
    }

    /** Bật/tắt hợp tác. Ngừng hợp tác thì không nhận đơn mới nhưng vẫn giữ lịch sử. */
    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('WORKSHOP_ADMIN')")
    public ResponseEntity<Response> setActive(@PathVariable Long id, @Valid @RequestBody EnabledRequest request) {
        return ResponseEntity.ok(customerService.setActive(id, request.enabled()));
    }
}

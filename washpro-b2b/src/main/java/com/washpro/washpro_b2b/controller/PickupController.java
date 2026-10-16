package com.washpro.washpro_b2b.controller;

import com.washpro.washpro_b2b.dto.PickupDtos.AssignRequest;
import com.washpro.washpro_b2b.dto.PickupDtos.CancelRequest;
import com.washpro.washpro_b2b.dto.PickupDtos.CreateRequest;
import com.washpro.washpro_b2b.dto.PickupDtos.Response;
import com.washpro.washpro_b2b.service.PickupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Yêu cầu lấy đồ. Phân quyền theo dữ liệu (của khách nào, tài xế nào) nằm trong PickupService. */
@RestController
@RequestMapping("/api/pickups")
@RequiredArgsConstructor
public class PickupController {

    private final PickupService pickupService;

    @GetMapping
    public ResponseEntity<List<Response>> list() {
        return ResponseEntity.ok(pickupService.list());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER_STAFF', 'CUSTOMER_MANAGER', 'WORKSHOP_ADMIN')")
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pickupService.create(request));
    }

    @PutMapping("/{id}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Response> accept(@PathVariable Long id) {
        return ResponseEntity.ok(pickupService.accept(id));
    }

    @PutMapping("/{id}/release")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Response> release(@PathVariable Long id) {
        return ResponseEntity.ok(pickupService.release(id));
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasRole('WORKSHOP_ADMIN')")
    public ResponseEntity<Response> assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request) {
        return ResponseEntity.ok(pickupService.assign(id, request.driverId()));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER_STAFF', 'CUSTOMER_MANAGER', 'WORKSHOP_ADMIN')")
    public ResponseEntity<Response> cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        return ResponseEntity.ok(pickupService.cancel(id, request.reason()));
    }
}

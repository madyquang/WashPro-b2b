package com.washpro.washpro_b2b.controller;

import com.washpro.washpro_b2b.dto.RouteDtos.RouteResponse;
import com.washpro.washpro_b2b.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    /** Lộ trình của tài xế trong ngày (mặc định hôm nay). Quản lý xưởng xem hộ tài xế bằng driverId. */
    @GetMapping
    @PreAuthorize("hasAnyRole('DRIVER', 'WORKSHOP_ADMIN')")
    public ResponseEntity<RouteResponse> route(@RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Long driverId) {
        return ResponseEntity.ok(routeService.route(date, driverId));
    }
}

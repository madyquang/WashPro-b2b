package com.washpro.washpro_b2b.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thông tin xưởng cho mọi tài khoản đã đăng nhập: tên và số hotline (nút "Gọi xưởng"). */
@RestController
@RequestMapping("/api/company")
public class CompanyController {

    /** hotline null = chưa cấu hình (giao diện ẩn nút gọi). */
    public record CompanyInfo(String name, String hotline) {
    }

    private final CompanyInfo info;

    public CompanyController(@Value("${app.company.name}") String name,
            @Value("${app.company.hotline:}") String hotline) {
        this.info = new CompanyInfo(name, hotline == null || hotline.isBlank() ? null : hotline.trim());
    }

    @GetMapping
    public ResponseEntity<CompanyInfo> get() {
        return ResponseEntity.ok(info);
    }
}

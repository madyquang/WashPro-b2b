package com.washpro.washpro_b2b.controller;

import com.washpro.washpro_b2b.dto.AuthDtos.AuthResponse;
import com.washpro.washpro_b2b.dto.AuthDtos.ChangePasswordRequest;
import com.washpro.washpro_b2b.dto.AuthDtos.LoginRequest;
import com.washpro.washpro_b2b.dto.UserDtos;
import com.washpro.washpro_b2b.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Endpoint công khai duy nhất. Tài khoản mới chỉ do Quản lý xưởng tạo qua /api/users. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDtos.Response> me() {
        return ResponseEntity.ok(authService.me());
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.noContent().build();
    }
}

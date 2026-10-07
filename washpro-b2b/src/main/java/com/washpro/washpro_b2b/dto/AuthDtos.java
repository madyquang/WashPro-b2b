package com.washpro.washpro_b2b.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Vui lòng nhập tên đăng nhập") @Size(max = 50, message = "Tên đăng nhập quá dài") String username,
            @NotBlank(message = "Vui lòng nhập mật khẩu") @Size(max = 72, message = "Mật khẩu quá dài") String password) {
    }

    public record AuthResponse(
            String token,
            String username,
            String fullName,
            String role,
            String restaurantName,
            long expiresAt) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại") String currentPassword,
            @NotBlank(message = "Vui lòng nhập mật khẩu mới") @Size(min = 8, max = 72, message = "Mật khẩu mới phải từ 8 đến 72 ký tự") String newPassword) {
    }
}

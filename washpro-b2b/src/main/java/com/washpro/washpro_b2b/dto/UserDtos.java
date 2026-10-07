package com.washpro.washpro_b2b.dto;

import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class UserDtos {

    private UserDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "Vui lòng nhập tên đăng nhập") @Size(min = 3, max = 50, message = "Tên đăng nhập phải từ 3 đến 50 ký tự") @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Tên đăng nhập chỉ gồm chữ không dấu, số, dấu chấm, gạch dưới, gạch ngang") String username,
            @NotBlank(message = "Vui lòng nhập mật khẩu") @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự") String password,
            @Size(max = 100, message = "Họ tên tối đa 100 ký tự") String fullName,
            @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự") String phone,
            @NotNull(message = "Vui lòng chọn vai trò") Role role,
            Long restaurantId) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Vui lòng nhập mật khẩu mới") @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự") String newPassword) {
    }

    public record EnabledRequest(@NotNull(message = "Thiếu trạng thái") Boolean enabled) {
    }

    /** Không bao giờ chứa mật khẩu. */
    public record Response(
            Long id,
            String username,
            String fullName,
            String phone,
            Role role,
            Long restaurantId,
            String restaurantName,
            boolean enabled,
            LocalDateTime createdAt) {

        public static Response from(User u) {
            return new Response(
                    u.getId(),
                    u.getUsername(),
                    u.getFullName(),
                    u.getPhone(),
                    u.getRole(),
                    u.getRestaurant() != null ? u.getRestaurant().getId() : null,
                    u.getRestaurant() != null ? u.getRestaurant().getName() : null,
                    u.isEnabled(),
                    u.getCreatedAt());
        }
    }
}

package com.washpro.washpro_b2b.dto;

import com.washpro.washpro_b2b.entity.CustomerType;
import com.washpro.washpro_b2b.entity.Restaurant;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Khách hàng của xưởng (giữ tên "Restaurant" để tương thích API cũ). */
public final class RestaurantDtos {

    private RestaurantDtos() {
    }

    public record Request(
            @NotBlank(message = "Vui lòng nhập tên khách hàng") @Size(max = 150, message = "Tên khách hàng tối đa 150 ký tự") String name,
            /** Không gửi thì mặc định là nhà hàng. */
            CustomerType customerType,
            @NotBlank(message = "Vui lòng nhập địa chỉ") @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự") String address,
            @Pattern(regexp = "^$|^[0-9+()\\-. ]{8,20}$", message = "Số điện thoại không hợp lệ") String phone,
            @Size(max = 100, message = "Tên người liên hệ tối đa 100 ký tự") String contactName,
            /** Không gửi thì mặc định 48 giờ. */
            @Min(value = 1, message = "Thời gian trả đồ tối thiểu 1 giờ") @Max(value = 720, message = "Thời gian trả đồ tối đa 30 ngày") Integer turnaroundHours,
            /** Không gửi thì mặc định thanh toán ngay (0 ngày). */
            @Min(value = 0, message = "Kỳ thanh toán không được âm") @Max(value = 120, message = "Kỳ thanh toán tối đa 120 ngày") Integer paymentTermDays) {
    }

    public record Response(
            Long id,
            String name,
            CustomerType customerType,
            String address,
            String phone,
            String contactName,
            int turnaroundHours,
            int paymentTermDays,
            boolean active,
            LocalDateTime createdAt) {

        public static Response from(Restaurant r) {
            return new Response(r.getId(), r.getName(), r.getCustomerType(), r.getAddress(), r.getPhone(),
                    r.getContactName(), r.getTurnaroundHours(), r.getPaymentTermDays(), r.isActive(),
                    r.getCreatedAt());
        }
    }
}

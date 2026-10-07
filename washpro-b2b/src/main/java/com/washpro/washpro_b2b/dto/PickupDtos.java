package com.washpro.washpro_b2b.dto;

import com.washpro.washpro_b2b.entity.CustomerType;
import com.washpro.washpro_b2b.entity.PickupRequest;
import com.washpro.washpro_b2b.entity.PickupStatus;
import com.washpro.washpro_b2b.entity.TimeSlot;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class PickupDtos {

    private PickupDtos() {
    }

    public record CreateRequest(
            /** Chỉ Quản lý xưởng cần gửi (tạo hộ khách gọi điện); tài khoản khách hàng tự lấy theo tài khoản. */
            Long restaurantId,
            @NotNull(message = "Vui lòng chọn ngày lấy đồ") @FutureOrPresent(message = "Ngày lấy đồ không được ở quá khứ") LocalDate preferredDate,
            TimeSlot timeSlot,
            @Min(value = 1, message = "Số túi tối thiểu là 1") @Max(value = 10000, message = "Số túi quá lớn") Integer estimatedBags,
            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) {
    }

    public record AssignRequest(@NotNull(message = "Vui lòng chọn tài xế") Long driverId) {
    }

    public record CancelRequest(@Size(max = 500, message = "Lý do tối đa 500 ký tự") String reason) {
    }

    public record Response(
            Long id,
            Long restaurantId,
            String restaurantName,
            CustomerType customerType,
            String address,
            String phone,
            String contactName,
            String requestedBy,
            LocalDate preferredDate,
            TimeSlot timeSlot,
            Integer estimatedBags,
            String note,
            PickupStatus status,
            Long driverId,
            String driverName,
            Long orderId,
            String cancelReason,
            String cancelledBy,
            LocalDateTime createdAt) {

        public static Response from(PickupRequest p) {
            var r = p.getRestaurant();
            return new Response(
                    p.getId(),
                    r.getId(),
                    r.getName(),
                    r.getCustomerType(),
                    r.getAddress(),
                    r.getPhone(),
                    r.getContactName(),
                    p.getRequestedBy(),
                    p.getPreferredDate(),
                    p.getTimeSlot(),
                    p.getEstimatedBags(),
                    p.getNote(),
                    p.getStatus(),
                    p.getDriver() == null ? null : p.getDriver().getId(),
                    p.getDriver() == null ? null : p.getDriver().getDisplayName(),
                    p.getOrder() == null ? null : p.getOrder().getId(),
                    p.getCancelReason(),
                    p.getCancelledBy(),
                    p.getCreatedAt());
        }
    }
}

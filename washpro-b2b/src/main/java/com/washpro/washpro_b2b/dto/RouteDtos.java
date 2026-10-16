package com.washpro.washpro_b2b.dto;

import com.washpro.washpro_b2b.entity.TimeSlot;

import java.time.LocalDate;
import java.util.List;

public final class RouteDtos {

    private RouteDtos() {
    }

    public enum StopType {
        /** Đến lấy đồ bẩn (yêu cầu lấy đồ tài xế đã nhận). */
        PICKUP,
        /** Giao trả đồ sạch (đơn đang giao). */
        DELIVERY
    }

    public record Stop(
            StopType type,
            /** Mã yêu cầu lấy đồ (PICKUP) hoặc mã đơn (DELIVERY). */
            Long id,
            Long customerId,
            String customerName,
            String address,
            /** Quận / huyện đoán từ địa chỉ; null nếu không đoán được. */
            String area,
            String phone,
            String contactName,
            /** Ngày hẹn lấy (PICKUP); null với DELIVERY. */
            LocalDate date,
            TimeSlot timeSlot,
            Integer bags,
            String note,
            /** Yêu cầu lấy đồ đã quá ngày hẹn mà chưa lấy. */
            boolean late) {
    }

    public record RouteResponse(LocalDate date, Long driverId, String driverName,
            /** Địa chỉ xưởng (điểm về cuối lộ trình), null nếu chưa cấu hình. */
            String workshopAddress,
            /** Thứ tự gợi ý: gom theo khu vực, trong khu vực theo khung giờ. */
            List<Stop> stops) {
    }
}

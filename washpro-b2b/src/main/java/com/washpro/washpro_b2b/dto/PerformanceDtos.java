package com.washpro.washpro_b2b.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Báo cáo hiệu suất. Tỉ lệ là số thập phân 0..1, null khi chưa có dữ liệu để tính. */
public final class PerformanceDtos {

    private PerformanceDtos() {
    }

    public record Summary(
            /** Đơn nhận trong kỳ (chưa hủy). */
            long orders,
            long bags,
            /** Tổng giá trị các đơn nhận trong kỳ (dòng đã có đơn giá). */
            BigDecimal orderValue,
            /** Đơn đã bắt đầu giao trả. */
            long delivered,
            /** Trong số đơn đã giao: bắt đầu giao trước hoặc đúng hạn trả. */
            long onTime,
            Double onTimeRate,
            /** Giờ trung bình từ lúc nhận đồ tới lúc bắt đầu giao trả. */
            Double avgTurnaroundHours,
            long discrepancyOrders,
            Double discrepancyRate,
            /** Đơn khách báo thiếu / hỏng khi nhận (trên số đơn đã giao). */
            long issueOrders,
            Double issueRate,
            long pickupsRequested,
            long pickupsDone,
            long pickupsCancelled,
            /** Giờ trung bình từ lúc khách gửi yêu cầu tới lúc tài xế lấy đồ (tạo đơn). */
            Double avgPickupHours) {
    }

    public record CustomerRow(Long customerId, String customerName, long orders, long bags, BigDecimal orderValue,
            long delivered, Double onTimeRate, Double avgTurnaroundHours, Double discrepancyRate, Double issueRate) {
    }

    public record DriverRow(Long driverId, String driverName, long orders, long bags, long pickupsDone,
            Double avgPickupHours, Double discrepancyRate) {
    }

    public record Report(LocalDate from, LocalDate to, Summary summary, List<CustomerRow> customers,
            /** Chỉ có với tài khoản phía xưởng. */
            List<DriverRow> drivers) {
    }
}

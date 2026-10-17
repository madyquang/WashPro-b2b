package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.RouteDtos.RouteResponse;
import com.washpro.washpro_b2b.dto.RouteDtos.Stop;
import com.washpro.washpro_b2b.dto.RouteDtos.StopType;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.OrderStatus;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.TimeSlot;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.repository.OrderRepository;
import com.washpro.washpro_b2b.repository.UserRepository;
import com.washpro.washpro_b2b.security.CurrentUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lộ trình trong ngày của tài xế: các đơn đã nhận việc lấy đồ (hẹn tới ngày đó, kể cả hẹn trước mà chưa lấy) và
 * các đơn đang giao trả. Không dùng API bản đồ trả phí: gom theo quận / huyện đoán từ địa chỉ; tài xế tự sắp lại
 * thứ tự trên giao diện và mở Google Maps với nhiều điểm dừng.
 */
@Service
public class RouteService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final String workshopAddress;

    public RouteService(OrderRepository orderRepository, UserRepository userRepository,
            CurrentUserService currentUserService, @Value("${app.company.address:}") String workshopAddress) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.workshopAddress = workshopAddress == null || workshopAddress.isBlank() ? null : workshopAddress.trim();
    }

    @Transactional(readOnly = true)
    public RouteResponse route(LocalDate date, Long driverId) {
        User user = currentUserService.getCurrentUser();
        User driver;
        if (user.getRole() == Role.DRIVER) {
            driver = user;
        } else if (user.getRole() == Role.WORKSHOP_ADMIN) {
            if (driverId == null) {
                throw ApiException.badRequest("Vui lòng chọn tài xế");
            }
            driver = userRepository.findById(driverId).filter(u -> u.getRole() == Role.DRIVER)
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài xế"));
        } else {
            throw ApiException.forbidden("Bạn không có quyền xem lộ trình");
        }
        LocalDate day = date != null ? date : LocalDate.now();

        List<Stop> stops = new ArrayList<>();
        orderRepository.findByDriverIdAndStatusAndPreferredDateLessThanEqualAndIsDeletedFalse(driver.getId(),
                OrderStatus.PICKUP_ASSIGNED, day)
                .forEach(o -> stops.add(stop(StopType.PICKUP, o.getId(), o.getCustomer(), o.getPreferredDate(),
                        o.getTimeSlot(), o.getEstimatedBags(), o.getPickupNote(), o.getPreferredDate().isBefore(day))));
        // Đơn đang giao là việc của hiện tại: chỉ đưa vào lộ trình hôm nay
        if (day.equals(LocalDate.now())) {
            orderRepository.findByDeliveryDriverIdAndStatusAndIsDeletedFalse(driver.getId(), OrderStatus.DELIVERING)
                    .forEach(o -> stops.add(stop(StopType.DELIVERY, o.getId(), o.getCustomer(), null, null,
                            o.getTotalBags(), null, false)));
        }
        stops.sort(SUGGESTED_ORDER);
        return new RouteResponse(day, driver.getId(), driver.getDisplayName(), workshopAddress, stops);
    }

    /** Gom theo khu vực (chưa rõ khu vực xếp cuối); trong khu vực: quá hẹn trước, rồi theo khung giờ, theo khách. */
    static final Comparator<Stop> SUGGESTED_ORDER = Comparator
            .comparing((Stop s) -> s.area() == null)
            .thenComparing(s -> s.area() == null ? "" : s.area(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(s -> !s.late())
            .thenComparingInt(s -> slotRank(s.timeSlot()))
            .thenComparing(Stop::customerName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Stop::type);

    private static int slotRank(TimeSlot slot) {
        return slot == null ? TimeSlot.ANYTIME.ordinal() : slot.ordinal();
    }

    private static Stop stop(StopType type, Long id, Customer c, LocalDate date, TimeSlot slot, Integer bags,
            String note, boolean late) {
        return new Stop(type, id, c.getId(), c.getName(), c.getAddress(), AddressAreas.areaOf(c.getAddress()),
                c.getPhone(), c.getContactName(), date, slot, bags, note, late);
    }
}

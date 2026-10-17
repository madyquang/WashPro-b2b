package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.PerformanceDtos.CustomerRow;
import com.washpro.washpro_b2b.dto.PerformanceDtos.DriverRow;
import com.washpro.washpro_b2b.dto.PerformanceDtos.Report;
import com.washpro.washpro_b2b.dto.PerformanceDtos.Summary;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.repository.OrderRepository;
import com.washpro.washpro_b2b.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Báo cáo hiệu suất trong một khoảng ngày: đúng hạn, thời gian xử lý, sai lệch / sự cố, đáp ứng yêu cầu lấy đồ;
 * theo khách và theo tài xế. CSDL trả mỗi đơn một dòng chỉ số (không tải chi tiết đơn), phần còn lại cộng dồn.
 */
@Service
@RequiredArgsConstructor
public class PerformanceService {

    /** Khoảng dài nhất được xem một lần (tránh truy vấn quá nặng). */
    static final int MAX_DAYS = 366;

    private final OrderRepository orderRepository;
    private final CurrentUserService currentUserService;

    /** Chỉ số của một đơn. */
    record OrderFact(Long customerId, String customerName, Long driverId, String driverName, LocalDateTime receivedAt,
            LocalDateTime dueAt, long bags, LocalDateTime firstDelivering, boolean discrepancy, boolean issue,
            BigDecimal value) {

        boolean delivered() {
            return firstDelivering != null;
        }

        boolean onTime() {
            return delivered() && (dueAt == null || !firstDelivering.isAfter(dueAt));
        }

        double turnaroundHours() {
            return Duration.between(receivedAt, firstDelivering).toMinutes() / 60.0;
        }
    }

    /** Một yêu cầu lấy đồ: đã lấy khi có lúc nhận đồ; đã hủy khi bị hủy trước khi lấy. */
    record PickupFact(boolean deleted, LocalDateTime requestedAt, LocalDateTime pickedAt, Long driverId) {

        boolean done() {
            return pickedAt != null;
        }

        boolean cancelled() {
            return deleted && pickedAt == null;
        }

        Double hoursToPickup() {
            return pickedAt == null ? null : Duration.between(requestedAt, pickedAt).toMinutes() / 60.0;
        }
    }

    @Transactional(readOnly = true)
    public Report report(LocalDate from, LocalDate to) {
        User user = currentUserService.getCurrentUser();
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now();
        if (end.isBefore(start)) {
            throw ApiException.badRequest("Ngày kết thúc phải sau ngày bắt đầu");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
            throw ApiException.badRequest("Chỉ xem được tối đa " + MAX_DAYS + " ngày mỗi lần");
        }

        Long customerId = switch (user.getRole()) {
            case WORKSHOP_ADMIN, WORKSHOP_ACCOUNTANT -> null;
            case CUSTOMER_MANAGER -> {
                if (user.getCustomer() == null) {
                    throw ApiException.forbidden("Tài khoản này chưa được liên kết với khách hàng nào");
                }
                yield user.getCustomer().getId();
            }
            default -> throw ApiException.forbidden("Bạn không có quyền xem báo cáo hiệu suất");
        };

        LocalDateTime fromTime = start.atStartOfDay();
        LocalDateTime toTime = end.plusDays(1).atStartOfDay();
        List<OrderFact> orders = orderRepository.performanceRows(fromTime, toTime, customerId).stream()
                .map(PerformanceService::orderFact).toList();
        List<PickupFact> pickups = orderRepository.pickupPerformanceRows(fromTime, toTime, customerId).stream()
                .map(r -> new PickupFact((Boolean) r[0], (LocalDateTime) r[1], (LocalDateTime) r[2], (Long) r[3]))
                .toList();

        List<DriverRow> drivers = customerId == null ? driverRows(orders, pickups) : null;
        return new Report(start, end, summary(orders, pickups), customerRows(orders), drivers);
    }

    private static OrderFact orderFact(Object[] r) {
        String driverName = r[4] != null && !r[4].toString().isBlank() ? r[4].toString() : (String) r[5];
        return new OrderFact((Long) r[1], (String) r[2], (Long) r[3], driverName, (LocalDateTime) r[6],
                (LocalDateTime) r[7], r[8] == null ? 0 : ((Number) r[8]).longValue(), (LocalDateTime) r[9],
                ((Number) r[10]).longValue() > 0, ((Number) r[11]).longValue() > 0,
                r[12] == null ? BigDecimal.ZERO : new BigDecimal(r[12].toString()));
    }

    private static Summary summary(List<OrderFact> orders, List<PickupFact> pickups) {
        long delivered = orders.stream().filter(OrderFact::delivered).count();
        long onTime = orders.stream().filter(OrderFact::onTime).count();
        long discrepancy = orders.stream().filter(OrderFact::discrepancy).count();
        long issue = orders.stream().filter(o -> o.delivered() && o.issue()).count();
        return new Summary(orders.size(), orders.stream().mapToLong(OrderFact::bags).sum(), value(orders),
                delivered, onTime, rate(onTime, delivered), avgTurnaround(orders), discrepancy,
                rate(discrepancy, orders.size()), issue, rate(issue, delivered), pickups.size(),
                pickups.stream().filter(PickupFact::done).count(),
                pickups.stream().filter(PickupFact::cancelled).count(), avgPickup(pickups));
    }

    /** Theo khách: nhiều đơn lên trước. */
    private static List<CustomerRow> customerRows(List<OrderFact> orders) {
        return group(orders, OrderFact::customerId).values().stream()
                .map(list -> {
                    OrderFact first = list.getFirst();
                    long delivered = list.stream().filter(OrderFact::delivered).count();
                    return new CustomerRow(first.customerId(), first.customerName(), list.size(),
                            list.stream().mapToLong(OrderFact::bags).sum(), value(list), delivered,
                            rate(list.stream().filter(OrderFact::onTime).count(), delivered), avgTurnaround(list),
                            rate(list.stream().filter(OrderFact::discrepancy).count(), list.size()),
                            rate(list.stream().filter(o -> o.delivered() && o.issue()).count(), delivered));
                })
                .sorted(Comparator.comparingLong(CustomerRow::orders).reversed()
                        .thenComparing(CustomerRow::customerName))
                .toList();
    }

    /** Theo tài xế lấy đồ (bỏ đơn khách mang đến xưởng): nhiều đơn lên trước. */
    private static List<DriverRow> driverRows(List<OrderFact> orders, List<PickupFact> pickups) {
        Map<Long, List<PickupFact>> pickupsByDriver = pickups.stream()
                .filter(p -> p.done() && p.driverId() != null)
                .collect(Collectors.groupingBy(PickupFact::driverId));
        List<DriverRow> rows = new ArrayList<>();
        List<OrderFact> withDriver = orders.stream().filter(o -> o.driverId() != null).toList();
        for (List<OrderFact> list : group(withDriver, OrderFact::driverId).values()) {
            OrderFact first = list.getFirst();
            List<PickupFact> done = pickupsByDriver.getOrDefault(first.driverId(), List.of());
            rows.add(new DriverRow(first.driverId(), first.driverName(), list.size(),
                    list.stream().mapToLong(OrderFact::bags).sum(), done.size(), avgPickup(done),
                    rate(list.stream().filter(OrderFact::discrepancy).count(), list.size())));
        }
        rows.sort(Comparator.comparingLong(DriverRow::orders).reversed().thenComparing(DriverRow::driverName));
        return rows;
    }

    private static <K> Map<K, List<OrderFact>> group(List<OrderFact> orders, Function<OrderFact, K> key) {
        return orders.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
    }

    private static BigDecimal value(List<OrderFact> orders) {
        return orders.stream().map(OrderFact::value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Double avgTurnaround(List<OrderFact> orders) {
        return round1(orders.stream().filter(OrderFact::delivered).mapToDouble(OrderFact::turnaroundHours).average());
    }

    private static Double avgPickup(List<PickupFact> pickups) {
        return round1(pickups.stream().map(PickupFact::hoursToPickup).filter(h -> h != null)
                .mapToDouble(Double::doubleValue).average());
    }

    private static Double round1(java.util.OptionalDouble v) {
        return v.isPresent() ? Math.round(v.getAsDouble() * 10) / 10.0 : null;
    }

    private static Double rate(long part, long total) {
        return total == 0 ? null : Math.round(part * 1000.0 / total) / 1000.0;
    }
}

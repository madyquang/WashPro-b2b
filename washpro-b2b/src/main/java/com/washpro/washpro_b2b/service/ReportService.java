package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.FinanceDtos.DashboardResponse;
import com.washpro.washpro_b2b.dto.FinanceDtos.DebtRow;
import com.washpro.washpro_b2b.dto.FinanceDtos.MonthRevenue;
import com.washpro.washpro_b2b.dto.FinanceDtos.StatementOrder;
import com.washpro.washpro_b2b.dto.FinanceDtos.StatementPayment;
import com.washpro.washpro_b2b.dto.FinanceDtos.StatementResponse;
import com.washpro.washpro_b2b.dto.OrderDtos;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderStatus;
import com.washpro.washpro_b2b.entity.PickupStatus;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.repository.OrderRepository;
import com.washpro.washpro_b2b.repository.OrderSpecs;
import com.washpro.washpro_b2b.repository.PaymentRepository;
import com.washpro.washpro_b2b.repository.PickupRequestRepository;
import com.washpro.washpro_b2b.repository.CustomerRepository;
import com.washpro.washpro_b2b.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Thống kê tổng quan, công nợ (kèm tuổi nợ), sao kê tháng và xuất báo cáo CSV. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final PickupRequestRepository pickupRepository;
    private final CurrentUserService currentUserService;

    // ===================== Tổng quan =====================

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        User user = currentUserService.getCurrentUser();
        // Mọi số đếm chạy trong CSDL (COUNT / GROUP BY), không tải danh sách đơn
        Specification<Order> scope = orderService.scope(user);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) {
            counts.put(s.name(), 0L);
        }
        orderRepository.countByStatus(scope).forEach((s, n) -> counts.put(s.name(), n));
        long totalOrders = counts.values().stream().mapToLong(Long::longValue).sum();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();
        LocalDateTime endOfToday = startOfToday.plusDays(1);
        long ordersToday = orderRepository.count(scope.and(OrderSpecs.createdBetween(startOfToday, endOfToday)));
        long overdueOrders = orderRepository.count(scope.and(OrderSpecs.overdue(now)));
        long dueToday = orderRepository.count(scope.and(OrderSpecs.dueBetween(now, endOfToday)));

        var activePickup = EnumSet.of(PickupStatus.OPEN, PickupStatus.ACCEPTED);
        long openPickups = user.getRole().isCustomerRole()
                ? pickupRepository.countByCustomerIdAndStatusIn(requireCustomer(user).getId(), activePickup)
                : pickupRepository.countByStatusIn(activePickup);

        BigDecimal billed = null;
        BigDecimal paid = null;
        BigDecimal balance = null;
        BigDecimal overdueDebt = null;
        if (canViewFinance(user)) {
            List<DebtRow> rows = debtRowsFor(user);
            billed = sum(rows, DebtRow::billed);
            paid = sum(rows, DebtRow::paid);
            balance = billed.subtract(paid);
            overdueDebt = sum(rows, DebtRow::overdueTotal);
        }
        return new DashboardResponse(counts, totalOrders, ordersToday, overdueOrders, dueToday, openPickups,
                billed, paid, balance, overdueDebt);
    }

    // ===================== Công nợ =====================

    @Transactional(readOnly = true)
    public List<DebtRow> debts() {
        User user = currentUserService.getCurrentUser();
        requireFinanceAccess(user);
        return debtRowsFor(user);
    }

    /** Công nợ hiện tại của một khách hàng (dùng để chặn thanh toán vượt số nợ). */
    @Transactional(readOnly = true)
    public BigDecimal balanceOf(Long customerId) {
        BigDecimal billed = completedByDay(customerId).stream().map(CompletedDay::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return billed.subtract(paymentRepository.sumPaid(customerId));
    }

    // ===================== Sao kê tháng =====================

    /**
     * Sao kê công nợ một tháng của một khách hàng: nợ đầu kỳ, các đơn hoàn thành trong tháng,
     * các khoản đã thanh toán trong tháng, nợ cuối kỳ. Quản lý phía khách chỉ xem được của mình.
     */
    @Transactional(readOnly = true)
    public StatementResponse statement(Long customerId, YearMonth month) {
        User user = currentUserService.getCurrentUser();
        requireFinanceAccess(user);
        Customer customer;
        if (user.getRole() == Role.CUSTOMER_MANAGER) {
            customer = requireCustomer(user);
        } else {
            if (customerId == null) {
                throw ApiException.badRequest("Vui lòng chọn khách hàng");
            }
            customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        }

        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        LocalDate startDate = start.toLocalDate();
        LocalDate endDate = end.toLocalDate();

        // Nợ đầu kỳ tính bằng SUM trong CSDL; chỉ tải các đơn / khoản thanh toán của tháng sao kê
        BigDecimal opening = orderRepository.billedBefore(customer.getId(), start)
                .subtract(paymentRepository.sumPaidBefore(customer.getId(), startDate));

        List<StatementOrder> orders = orderRepository.findCompletedBetween(customer.getId(), start, end).stream()
                .map(o -> {
                    LocalDateTime completed = o.getEffectiveCompletedAt();
                    return new StatementOrder(o.getId(), completed,
                            completed.toLocalDate().plusDays(customer.getPaymentTermDays()), o.getItems().size(),
                            o.getTotalAmount());
                })
                .sorted(Comparator.comparing(StatementOrder::completedAt).thenComparing(StatementOrder::id))
                .toList();

        List<StatementPayment> payments = paymentRepository.findActiveBetween(customer.getId(), startDate, endDate)
                .stream()
                .map(p -> new StatementPayment(p.getId(), p.getPaidDate(), p.getAmount(), p.getNote()))
                .toList();

        BigDecimal totalBilled = orders.stream().map(StatementOrder::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = payments.stream().map(StatementPayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StatementResponse(customer.getId(), customer.getName(), customer.getCustomerType(),
                customer.getAddress(), customer.getPaymentTermDays(), month.toString(), opening, orders, payments,
                totalBilled, totalPaid, opening.add(totalBilled).subtract(totalPaid));
    }

    // ===================== Doanh thu theo tháng =====================

    /** Doanh thu N tháng gần nhất (tính theo lúc đơn hoàn thành), tháng không có đơn hiển thị 0. */
    @Transactional(readOnly = true)
    public List<MonthRevenue> revenue(int months) {
        User user = currentUserService.getCurrentUser();
        requireFinanceAccess(user);

        int n = Math.min(Math.max(months, 1), 24);
        YearMonth first = YearMonth.now().minusMonths(n - 1L);

        List<Object[]> rows;
        if (user.getRole() == Role.CUSTOMER_MANAGER) {
            rows = orderRepository.revenueByMonthForCustomer(OrderStatus.COMPLETED,
                    first.atDay(1).atStartOfDay(), requireCustomer(user).getId());
        } else {
            rows = orderRepository.revenueByMonth(OrderStatus.COMPLETED, first.atDay(1).atStartOfDay());
        }

        Map<YearMonth, Object[]> byMonth = new HashMap<>();
        for (Object[] row : rows) {
            byMonth.put(YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()), row);
        }

        List<MonthRevenue> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            YearMonth ym = first.plusMonths(i);
            Object[] row = byMonth.get(ym);
            long orders = row == null ? 0 : ((Number) row[2]).longValue();
            BigDecimal amount = (row == null || row[3] == null) ? BigDecimal.ZERO : new BigDecimal(row[3].toString());
            result.add(new MonthRevenue(ym.toString(), orders, amount));
        }
        return result;
    }

    // ===================== Xuất CSV =====================

    @Transactional(readOnly = true)
    public byte[] exportDebtsCsv() {
        User user = currentUserService.getCurrentUser();
        requireFinanceAccess(user);

        StringBuilder sb = new StringBuilder();
        sb.append(row("Mã khách hàng", "Khách hàng", "Loại khách", "Kỳ thanh toán (ngày)", "Số đơn hoàn thành",
                "Tổng tiền (đ)", "Đã thanh toán (đ)", "Còn nợ (đ)", "Chưa đến hạn (đ)", "Quá hạn 1-30 ngày (đ)",
                "Quá hạn 31-60 ngày (đ)", "Quá hạn trên 60 ngày (đ)", "Hạn sớm nhất còn nợ"));
        for (DebtRow r : debtRowsFor(user)) {
            sb.append(row(String.valueOf(r.customerId()), r.customerName(), r.customerType().getLabel(),
                    String.valueOf(r.paymentTermDays()), String.valueOf(r.completedOrders()), plain(r.billed()),
                    plain(r.paid()), plain(r.balance()), plain(r.notDue()), plain(r.overdue1to30()),
                    plain(r.overdue31to60()), plain(r.overdueOver60()),
                    r.oldestUnpaidDue() == null ? "" : DATE.format(r.oldestUnpaidDue())));
        }
        return withBom(sb);
    }

    @Transactional(readOnly = true)
    public byte[] exportOrdersCsv() {
        User user = currentUserService.getCurrentUser();
        requireFinanceAccess(user);

        StringBuilder sb = new StringBuilder();
        sb.append(row("Mã đơn", "Khách hàng", "Loại khách", "Tài xế", "Trạng thái", "Số túi", "Số loại đồ",
                "Tổng tiền (đ)", "Đã đủ đơn giá", "Ngày tạo", "Hạn trả", "Trễ hạn", "Ngày hoàn thành"));
        for (Order o : orderService.visibleOrders(user)) {
            OrderDtos.Response r = OrderDtos.Response.from(o, false);
            sb.append(row(String.valueOf(r.id()), r.customerName(), r.customerType().getLabel(), r.driverName(),
                    r.status().getLabel(), String.valueOf(r.totalBags()), String.valueOf(r.items().size()),
                    plain(r.totalAmount()), r.billable() ? "Có" : "Chưa", dateTime(r.createdAt()), dateTime(r.dueAt()),
                    r.overdue() ? "Có" : "", dateTime(r.completedAt())));
        }
        return withBom(sb);
    }

    /** Mỗi dòng đồ giặt một hàng: lọc/tổng hợp trong Excel theo nhóm đồ (chăn ga, đồng phục...) hoặc dịch vụ. */
    @Transactional(readOnly = true)
    public byte[] exportItemsCsv() {
        User user = currentUserService.getCurrentUser();
        requireFinanceAccess(user);

        StringBuilder sb = new StringBuilder();
        sb.append(row("Mã đơn", "Ngày tạo", "Khách hàng", "Trạng thái", "Nhóm đồ", "Loại đồ", "Dịch vụ",
                "Số lượng", "Đơn vị", "Đơn giá (đ)", "Thành tiền (đ)"));
        for (Order o : orderService.visibleOrders(user)) {
            String created = dateTime(o.getCreatedAt());
            for (OrderDtos.ItemResponse i : OrderDtos.Response.from(o, false).items()) {
                sb.append(row(String.valueOf(o.getId()), created, o.getCustomer().getName(),
                        o.getStatus().getLabel(), i.category().getLabel(), i.itemName(), i.serviceType().getLabel(),
                        plain(i.quantity()), i.unit().getLabel(), plain(i.unitPrice()), plain(i.lineTotal())));
            }
        }
        return withBom(sb);
    }

    // ===================== Hàm dùng nội bộ =====================

    private boolean canViewFinance(User user) {
        return user.getRole() == Role.WORKSHOP_ADMIN
                || user.getRole() == Role.WORKSHOP_ACCOUNTANT
                || user.getRole() == Role.CUSTOMER_MANAGER;
    }

    private void requireFinanceAccess(User user) {
        if (!canViewFinance(user)) {
            throw ApiException.forbidden("Bạn không có quyền xem công nợ");
        }
    }

    private static Customer requireCustomer(User user) {
        if (user.getCustomer() == null) {
            throw ApiException.forbidden("Tài khoản này chưa được liên kết với khách hàng nào");
        }
        return user.getCustomer();
    }

    /**
     * Công nợ từng khách, sắp theo nợ quá hạn rồi tổng nợ giảm dần (khách cần đòi gấp lên đầu).
     * CSDL gộp sẵn tiền theo [khách, ngày hoàn thành] nên số dòng phải xử lý không tăng theo số đơn.
     */
    private List<DebtRow> debtRowsFor(User user) {
        List<Customer> customers;
        List<CompletedDay> completed;
        if (user.getRole() == Role.CUSTOMER_MANAGER) {
            Customer own = requireCustomer(user);
            customers = List.of(own);
            completed = completedByDay(own.getId());
        } else {
            customers = customerRepository.findAllByOrderByNameAsc();
            completed = completedByDay(null);
        }

        Map<Long, List<CompletedDay>> byCustomer = completed.stream()
                .collect(Collectors.groupingBy(CompletedDay::customerId));
        Map<Long, BigDecimal> paid = paidByCustomer();
        LocalDate today = LocalDate.now();

        return customers.stream()
                .map(r -> {
                    List<CompletedDay> days = byCustomer.getOrDefault(r.getId(), List.of());
                    List<DebtAging.Receivable> receivables = days.stream()
                            .map(d -> new DebtAging.Receivable(d.day().plusDays(r.getPaymentTermDays()), d.amount()))
                            .toList();
                    BigDecimal b = receivables.stream().map(DebtAging.Receivable::amount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal p = paid.getOrDefault(r.getId(), BigDecimal.ZERO);
                    DebtAging.Result aging = DebtAging.compute(receivables, p, today);
                    long orderCount = days.stream().mapToLong(CompletedDay::orders).sum();
                    return new DebtRow(r.getId(), r.getName(), r.getCustomerType(), r.isActive(),
                            r.getPaymentTermDays(), (int) orderCount, b, p, b.subtract(p), aging.notDue(),
                            aging.overdue1to30(), aging.overdue31to60(), aging.overdueOver60(),
                            aging.overdueTotal(), aging.oldestUnpaidDue());
                })
                .sorted(Comparator.comparing(DebtRow::overdueTotal).reversed()
                        .thenComparing(Comparator.comparing(DebtRow::balance).reversed()))
                .toList();
    }

    /** Tiền các đơn hoàn thành của một khách trong một ngày. */
    private record CompletedDay(Long customerId, LocalDate day, long orders, BigDecimal amount) {
    }

    private List<CompletedDay> completedByDay(Long customerId) {
        return orderRepository.completedByDay(customerId).stream()
                .map(row -> new CompletedDay(
                        ((Number) row[0]).longValue(),
                        toLocalDate(row[1]),
                        ((Number) row[2]).longValue(),
                        row[3] == null ? BigDecimal.ZERO : new BigDecimal(row[3].toString())))
                .toList();
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate d) {
            return d;
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        return LocalDate.parse(value.toString().substring(0, 10));
    }

    private Map<Long, BigDecimal> paidByCustomer() {
        Map<Long, BigDecimal> paid = new HashMap<>();
        for (Object[] row : paymentRepository.sumPaidByCustomer()) {
            paid.put(((Number) row[0]).longValue(), new BigDecimal(row[1].toString()));
        }
        return paid;
    }

    private static BigDecimal sum(List<DebtRow> rows, java.util.function.Function<DebtRow, BigDecimal> field) {
        return rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String dateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME.format(value);
    }

    private static byte[] withBom(StringBuilder sb) {
        // BOM giúp Excel mở file UTF-8 hiển thị đúng tiếng Việt
        return ("﻿" + sb).getBytes(StandardCharsets.UTF_8);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String row(String... cells) {
        return java.util.Arrays.stream(cells).map(ReportService::csv).collect(Collectors.joining(",")) + "\r\n";
    }

    /** Escape CSV và chặn CSV injection (ô bắt đầu bằng = + - @ sẽ bị Excel hiểu là công thức). */
    static String csv(String value) {
        String v = value == null ? "" : value;
        boolean plainNumber = v.matches("-?\\d+(\\.\\d+)?");
        if (!plainNumber && !v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}

package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.FinanceDtos.DebtRow;
import com.washpro.washpro_b2b.entity.NotificationType;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.repository.NotificationRepository;
import com.washpro.washpro_b2b.repository.OrderRepository;
import com.washpro.washpro_b2b.repository.OrderSpecs;
import com.washpro.washpro_b2b.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Bản tin mỗi sáng: quản lý xưởng biết số đơn trễ hạn trả; quản lý xưởng / kế toán biết khách nợ quá hạn;
 * quản lý phía khách biết số nợ quá hạn của mình. Mỗi người nhận tối đa một bản tin mỗi loại mỗi ngày
 * (khởi động lại máy chủ không gửi trùng). Đồng thời dọn thông báo cũ hơn 90 ngày.
 */
@Component
@RequiredArgsConstructor
public class NotificationDigestJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationDigestJob.class);
    private static final int KEEP_DAYS = 90;

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ReportService reportService;

    @Scheduled(cron = "${app.notifications.digest-cron:0 0 7 * * *}", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        long overdueOrders = orderRepository.count(OrderSpecs.notDeleted().and(OrderSpecs.overdue(now)));
        if (overdueOrders > 0) {
            sendOnce(notificationService.users(Role.WORKSHOP_ADMIN), startOfDay, NotificationType.OVERDUE_ORDERS,
                    overdueOrders + " đơn đã quá hạn trả đồ", "Ưu tiên giặt và giao trả các đơn này",
                    "/orders?overdue=1");
        }

        List<DebtRow> overdueDebts = reportService.allDebts().stream()
                .filter(r -> r.overdueTotal().signum() > 0).toList();
        if (!overdueDebts.isEmpty()) {
            BigDecimal total = overdueDebts.stream().map(DebtRow::overdueTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            sendOnce(notificationService.users(Role.WORKSHOP_ADMIN, Role.WORKSHOP_ACCOUNTANT), startOfDay,
                    NotificationType.OVERDUE_DEBT,
                    overdueDebts.size() + " khách có nợ quá hạn, tổng " + NotificationService.money(total),
                    "Nhiều nhất: " + overdueDebts.getFirst().customerName() + " ("
                            + NotificationService.money(overdueDebts.getFirst().overdueTotal()) + ")",
                    "/finance");
            for (DebtRow r : overdueDebts) {
                sendOnce(userRepository.findByCustomerIdAndRoleInAndEnabledTrue(r.customerId(),
                        Set.of(Role.CUSTOMER_MANAGER)), startOfDay, NotificationType.OVERDUE_DEBT,
                        "Công nợ quá hạn: " + NotificationService.money(r.overdueTotal()),
                        "Tổng còn nợ " + NotificationService.money(r.balance()) + ". Xem sao kê trong mục Công nợ",
                        "/finance");
            }
        }

        int removed = notificationRepository.deleteOlderThan(now.minusDays(KEEP_DAYS));
        log.info("Bản tin thông báo: {} đơn trễ hạn, {} khách nợ quá hạn; đã dọn {} thông báo cũ",
                overdueOrders, overdueDebts.size(), removed);
    }

    private void sendOnce(List<User> recipients, LocalDateTime startOfDay, NotificationType type, String title,
            String body, String link) {
        List<User> fresh = recipients.stream()
                .filter(u -> !notificationRepository.existsByUserIdAndTypeAndCreatedAtGreaterThanEqual(u.getId(),
                        type, startOfDay))
                .toList();
        notificationService.send(fresh, null, type, title, body, link);
    }
}

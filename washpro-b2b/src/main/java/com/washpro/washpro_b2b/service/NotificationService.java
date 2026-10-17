package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.NotificationDtos.Item;
import com.washpro.washpro_b2b.dto.NotificationDtos.ListResponse;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.Notification;
import com.washpro.washpro_b2b.entity.NotificationType;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.Payment;
import com.washpro.washpro_b2b.entity.PickupRequest;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.repository.NotificationRepository;
import com.washpro.washpro_b2b.repository.UserRepository;
import com.washpro.washpro_b2b.security.CurrentUserService;
import com.washpro.washpro_b2b.service.push.PushService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thông báo trong ứng dụng: ai cần biết gì khi có sự kiện ở đơn, yêu cầu lấy đồ, thanh toán.
 * Thông báo được ghi trong cùng giao dịch với thao tác nghiệp vụ (thao tác lỗi thì không có thông báo).
 * Người gây ra sự kiện không nhận thông báo của chính mình.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM");
    private static final Set<Role> CUSTOMER_ROLES = Set.of(Role.CUSTOMER_STAFF, Role.CUSTOMER_MANAGER);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final PushService pushService;

    // ===================== Đọc thông báo của mình =====================

    @Transactional(readOnly = true)
    public ListResponse mine(int limit) {
        User user = currentUserService.getCurrentUser();
        List<Item> items = notificationRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(user.getId(), PageRequest.of(0, Math.min(Math.max(limit, 1), 100)))
                .stream().map(Item::from).toList();
        return new ListResponse(items, notificationRepository.countByUserIdAndReadAtIsNull(user.getId()));
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByUserIdAndReadAtIsNull(currentUserService.getCurrentUser().getId());
    }

    @Transactional
    public void markRead(Long id) {
        User user = currentUserService.getCurrentUser();
        Notification n = notificationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thông báo"));
        if (n.getReadAt() == null) {
            n.setReadAt(LocalDateTime.now());
        }
    }

    @Transactional
    public void markAllRead() {
        notificationRepository.markAllRead(currentUserService.getCurrentUser().getId(), LocalDateTime.now());
    }

    // ===================== Yêu cầu lấy đồ =====================

    public void pickupCreated(PickupRequest p, User actor) {
        // Mọi tài xế đang hoạt động thấy việc mới để nhận; quản lý xưởng để điều phối
        send(users(Role.WORKSHOP_ADMIN, Role.DRIVER), actor, NotificationType.PICKUP_NEW,
                "Yêu cầu lấy đồ mới: " + p.getCustomer().getName(),
                "Ngày " + DATE.format(p.getPreferredDate()) + ", " + p.getTimeSlot().getLabel().toLowerCase()
                        + (p.getEstimatedBags() != null ? ", khoảng " + p.getEstimatedBags() + " túi" : ""),
                "/pickups");
    }

    public void pickupAccepted(PickupRequest p, User actor) {
        send(customerUsers(p.getCustomer()), actor, NotificationType.PICKUP_ACCEPTED,
                "Tài xế " + p.getDriver().getDisplayName() + " sẽ đến lấy đồ",
                "Ngày " + DATE.format(p.getPreferredDate()) + ", " + p.getTimeSlot().getLabel().toLowerCase(),
                "/pickups");
    }

    public void pickupAssigned(PickupRequest p, User actor) {
        send(List.of(p.getDriver()), actor, NotificationType.PICKUP_ASSIGNED,
                "Bạn được phân công lấy đồ: " + p.getCustomer().getName(),
                "Ngày " + DATE.format(p.getPreferredDate()) + ", " + p.getTimeSlot().getLabel().toLowerCase()
                        + (p.getCustomer().getAddress() != null ? ". " + p.getCustomer().getAddress() : ""),
                "/pickups");
        pickupAccepted(p, actor);
    }

    public void pickupCancelled(PickupRequest p, User actor) {
        List<User> to = new ArrayList<>(users(Role.WORKSHOP_ADMIN));
        to.addAll(customerUsers(p.getCustomer()));
        if (p.getDriver() != null) {
            to.add(p.getDriver());
        }
        send(to, actor, NotificationType.PICKUP_CANCELLED,
                "Đã hủy yêu cầu lấy đồ: " + p.getCustomer().getName(),
                p.getCancelReason() != null ? "Lý do: " + p.getCancelReason() : null,
                "/pickups");
    }

    // ===================== Đơn hàng =====================

    public void orderCreated(Order o, User actor) {
        send(customerUsers(o.getCustomer()), actor, NotificationType.ORDER_TO_CONFIRM,
                "Đơn #" + o.getId() + " chờ bạn xác nhận số lượng",
                o.getTotalBags() + " túi, tài xế " + o.getDriver().getDisplayName(), orderLink(o));
    }

    public void orderDiscrepancy(Order o, User actor, String note) {
        List<User> to = new ArrayList<>(users(Role.WORKSHOP_ADMIN));
        to.addAll(customerUsers(o.getCustomer()));
        to.add(o.getDriver());
        send(to, actor, NotificationType.ORDER_DISCREPANCY,
                "Đơn #" + o.getId() + " (" + o.getCustomer().getName() + ") có báo sai lệch", note, orderLink(o));
    }

    public void orderDelivering(Order o, User actor) {
        send(customerUsers(o.getCustomer()), actor, NotificationType.ORDER_DELIVERING,
                "Đơn #" + o.getId() + " đang được giao trả",
                "Kiểm đồ rồi bấm xác nhận đã nhận đủ, hoặc báo sự cố nếu thiếu / hỏng", orderLink(o));
    }

    public void orderReceiptIssue(Order o, User actor, String note) {
        send(users(Role.WORKSHOP_ADMIN), actor, NotificationType.ORDER_RECEIPT_ISSUE,
                "Đơn #" + o.getId() + " (" + o.getCustomer().getName() + ") báo sự cố khi nhận đồ", note, orderLink(o));
    }

    public void orderCancelled(Order o, User actor, String reason) {
        List<User> to = new ArrayList<>(customerUsers(o.getCustomer()));
        to.add(o.getDriver());
        send(to, actor, NotificationType.ORDER_CANCELLED, "Đơn #" + o.getId() + " đã bị hủy",
                reason != null ? "Lý do: " + reason : null, "/orders");
    }

    // ===================== Thanh toán =====================

    public void paymentRecorded(Payment p, User actor) {
        send(userRepository.findByCustomerIdAndRoleInAndEnabledTrue(p.getCustomer().getId(),
                Set.of(Role.CUSTOMER_MANAGER)), actor, NotificationType.PAYMENT_RECORDED,
                "Xưởng đã ghi nhận thanh toán " + money(p.getAmount()),
                "Ngày " + DATE.format(p.getPaidDate()) + (p.getNote() != null ? ". " + p.getNote() : ""), "/finance");
    }

    // ===================== Gửi =====================

    /** Ghi một thông báo cho mỗi người nhận (bỏ trùng, bỏ người gây ra sự kiện). */
    public void send(Collection<User> recipients, User actor, NotificationType type, String title, String body,
            String link) {
        Map<Long, User> unique = new LinkedHashMap<>();
        for (User u : recipients) {
            if (u != null && u.isEnabled() && (actor == null || !u.getId().equals(actor.getId()))) {
                unique.putIfAbsent(u.getId(), u);
            }
        }
        List<Notification> rows = unique.values().stream()
                .map(u -> Notification.builder().user(u).type(type).title(cut(title, 200)).body(cut(body, 500))
                        .link(link).build())
                .toList();
        notificationRepository.saveAll(rows);
        // Cùng nội dung tới điện thoại / máy tính đã bật thông báo đẩy (gửi sau khi giao dịch lưu xong)
        pushService.notifyUsers(unique.keySet(), cut(title, 200), cut(body, 500), link, type.name());
    }

    List<User> users(Role... roles) {
        return userRepository.findByRoleInAndEnabledTrue(List.of(roles));
    }

    private List<User> customerUsers(Customer customer) {
        return userRepository.findByCustomerIdAndRoleInAndEnabledTrue(customer.getId(), CUSTOMER_ROLES);
    }

    /** Mở trang đơn ở đúng nhóm trạng thái hiện tại của đơn, tìm đơn đó và mở chi tiết. */
    private static String orderLink(Order o) {
        return "/orders?status=" + o.getStatus().name() + "&open=" + o.getId();
    }

    static String money(BigDecimal amount) {
        return String.format(java.util.Locale.US, "%,.0f", amount).replace(',', '.') + " đ";
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

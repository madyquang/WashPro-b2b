package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.NotificationDtos.Item;
import com.washpro.washpro_b2b.dto.NotificationDtos.ListResponse;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.Notification;
import com.washpro.washpro_b2b.entity.NotificationType;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.Payment;
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
 * Thông báo trong ứng dụng: ai cần biết gì khi có sự kiện ở đơn (từ yêu cầu lấy đồ đến giao xong), thanh toán.
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

    // ===================== Lấy đồ =====================

    public void pickupRequested(Order o, User actor) {
        // Mọi tài xế đang hoạt động thấy việc mới để nhận; quản lý xưởng để điều phối
        send(users(Role.WORKSHOP_ADMIN, Role.DRIVER), actor, NotificationType.PICKUP_NEW,
                "Yêu cầu lấy đồ mới: " + o.getCustomer().getName(), when(o)
                        + (o.getEstimatedBags() != null ? ", khoảng " + o.getEstimatedBags() + " túi" : ""),
                "/pickups?tab=open");
    }

    public void pickupAccepted(Order o, User actor) {
        send(customerUsers(o.getCustomer()), actor, NotificationType.PICKUP_ACCEPTED,
                "Tài xế " + o.getDriver().getDisplayName() + " sẽ đến lấy đồ", when(o), "/pickups");
    }

    public void pickupAssigned(Order o, User actor) {
        send(List.of(o.getDriver()), actor, NotificationType.PICKUP_ASSIGNED,
                "Bạn được phân công lấy đồ: " + o.getCustomer().getName(),
                when(o) + (o.getCustomer().getAddress() != null ? ". " + o.getCustomer().getAddress() : ""),
                "/pickups?tab=mine");
        pickupAccepted(o, actor);
    }

    public void orderReceived(Order o, User actor) {
        String body = o.getTotalBags() + " túi" + (o.getDriver() != null ? ", tài xế " + o.getDriver().getDisplayName() : "");
        send(customerUsers(o.getCustomer()), actor, NotificationType.ORDER_RECEIVED,
                "Đã nhận đồ đơn #" + o.getId(), body, orderLink(o));
        send(users(Role.WORKSHOP_ADMIN), actor, NotificationType.ORDER_RECEIVED,
                "Đơn #" + o.getId() + " (" + o.getCustomer().getName() + ") đang về xưởng",
                body + ". Bấm \"Bắt đầu giặt\" khi đồ về tới xưởng", orderLink(o));
    }

    // ===================== Giặt, giao trả =====================

    public void orderWashed(Order o, User actor) {
        send(users(Role.DRIVER), actor, NotificationType.ORDER_WASHED,
                "Đơn #" + o.getId() + " giặt xong, chờ giao: " + o.getCustomer().getName(),
                o.getTotalBags() + " túi" + (o.getCustomer().getAddress() != null ? ". " + o.getCustomer().getAddress() : ""),
                "/pickups?tab=deliver");
        send(customerUsers(o.getCustomer()), actor, NotificationType.ORDER_WASHED,
                "Đồ đơn #" + o.getId() + " đã giặt xong", "Xưởng sẽ sớm giao trả", orderLink(o));
    }

    public void deliveryAssigned(Order o, User actor, boolean startsDelivery) {
        send(List.of(o.getDeliveryDriver()), actor, NotificationType.DELIVERY_ASSIGNED,
                "Bạn được phân công giao trả đơn #" + o.getId() + ": " + o.getCustomer().getName(),
                o.getTotalBags() + " túi" + (o.getCustomer().getAddress() != null ? ". " + o.getCustomer().getAddress() : ""),
                "/pickups?tab=mine");
        if (startsDelivery) {
            orderDelivering(o, actor);
        }
    }

    public void orderDelivering(Order o, User actor) {
        send(customerUsers(o.getCustomer()), actor, NotificationType.ORDER_DELIVERING,
                "Đơn #" + o.getId() + " đang được giao trả",
                (o.getDeliveryDriver() != null ? "Tài xế " + o.getDeliveryDriver().getDisplayName() + ". " : "")
                        + "Kiểm đồ khi nhận; thiếu / hỏng thì báo ngay trên ứng dụng hoặc gọi xưởng",
                orderLink(o));
    }

    public void orderCompleted(Order o, User actor) {
        send(customerUsers(o.getCustomer()), actor, NotificationType.ORDER_COMPLETED,
                "Đã giao xong đơn #" + o.getId(), "Có vấn đề với đồ đã nhận, vui lòng gọi hotline của xưởng",
                orderLink(o));
    }

    public void orderReceiptIssue(Order o, User actor, String note) {
        List<User> to = new ArrayList<>(users(Role.WORKSHOP_ADMIN));
        to.add(o.getDeliveryDriver());
        send(to, actor, NotificationType.ORDER_RECEIPT_ISSUE,
                "Đơn #" + o.getId() + " (" + o.getCustomer().getName() + ") báo sự cố khi nhận đồ", note, orderLink(o));
    }

    /** Hủy yêu cầu lấy đồ: báo xưởng, khách, tài xế đã nhận; hủy đơn đang xử lý: báo khách và các tài xế của đơn. */
    public void orderCancelled(Order o, User actor, String reason) {
        boolean pickup = o.getStatus().isPickupStage();
        List<User> to = new ArrayList<>(customerUsers(o.getCustomer()));
        if (pickup) {
            to.addAll(users(Role.WORKSHOP_ADMIN));
        }
        to.add(o.getDriver());
        to.add(o.getDeliveryDriver());
        send(to, actor, pickup ? NotificationType.PICKUP_CANCELLED : NotificationType.ORDER_CANCELLED,
                pickup ? "Đã hủy yêu cầu lấy đồ: " + o.getCustomer().getName() : "Đơn #" + o.getId() + " đã bị hủy",
                reason != null ? "Lý do: " + reason : null, pickup ? "/pickups" : "/orders");
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

    /** "Ngày 17/10, sáng (7h–11h)". */
    private static String when(Order o) {
        return "Ngày " + DATE.format(o.getPreferredDate()) + ", " + o.getTimeSlot().getLabel().toLowerCase();
    }

    static String money(BigDecimal amount) {
        return String.format(java.util.Locale.US, "%,.0f", amount).replace(',', '.') + " đ";
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

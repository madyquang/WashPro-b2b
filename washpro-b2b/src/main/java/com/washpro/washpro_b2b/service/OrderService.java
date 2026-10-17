package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.OrderDtos;
import com.washpro.washpro_b2b.dto.OrderDtos.CreateRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.DueRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.Filter;
import com.washpro.washpro_b2b.dto.OrderDtos.ItemRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.ItemsRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.PageResponse;
import com.washpro.washpro_b2b.dto.OrderDtos.ReceiptIssueRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.ReceiveRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.Response;
import com.washpro.washpro_b2b.dto.OrderDtos.StatusRequest;
import com.washpro.washpro_b2b.entity.AttachmentKind;
import com.washpro.washpro_b2b.entity.AuditAction;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.ItemCategory;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderItem;
import com.washpro.washpro_b2b.entity.OrderNote;
import com.washpro.washpro_b2b.entity.OrderStatus;
import com.washpro.washpro_b2b.entity.PriceUnit;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.ServicePrice;
import com.washpro.washpro_b2b.entity.ServiceType;
import com.washpro.washpro_b2b.entity.TimeSlot;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.repository.CustomerRepository;
import com.washpro.washpro_b2b.repository.OrderAttachmentRepository;
import com.washpro.washpro_b2b.repository.OrderNoteRepository;
import com.washpro.washpro_b2b.repository.OrderRepository;
import com.washpro.washpro_b2b.repository.OrderSpecs;
import com.washpro.washpro_b2b.repository.UserRepository;
import com.washpro.washpro_b2b.security.CurrentUserService;
import com.washpro.washpro_b2b.service.AuditService.Changes;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Toàn bộ quy tắc nghiệp vụ của đơn giặt, từ lúc khách yêu cầu lấy đồ đến khi giao xong:
 * - Khách (hoặc xưởng tạo hộ) gửi yêu cầu: đơn "Chờ lấy đồ". Tài xế nhận việc / xưởng phân công.
 * - Tài xế kiểm số lượng cùng khách, khách ký: "Đã tiếp nhận". Xưởng "Bắt đầu giặt", "Đã giặt xong".
 * - Tài xế nhận giao / xưởng phân công giao: "Đang giao trả"; giao xong kèm chữ ký / ảnh: "Hoàn thành".
 * - Mỗi vai trò chỉ thấy/thao tác trên đơn thuộc phạm vi của mình; trạng thái đi theo OrderStatus#canTransitionTo.
 * - Mọi thay đổi đều được ghi vào nhật ký (OrderHistory) để đối soát.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Các bước Quản lý xưởng bấm trực tiếp (giặt, giặt xong, giao bù, chốt hoàn thành). */
    private static final Set<OrderStatus> ADMIN_TARGETS = EnumSet.of(
            OrderStatus.WASHING, OrderStatus.WASHED, OrderStatus.DELIVERING, OrderStatus.COMPLETED);
    private static final Set<AttachmentKind> PICKUP_PROOF = EnumSet.of(AttachmentKind.PICKUP_PHOTO,
            AttachmentKind.PICKUP_SIGNATURE);
    private static final Set<AttachmentKind> DELIVERY_PROOF = EnumSet.of(AttachmentKind.DELIVERY_PHOTO,
            AttachmentKind.DELIVERY_SIGNATURE);

    private final OrderRepository orderRepository;
    private final OrderNoteRepository noteRepository;
    private final OrderAttachmentRepository attachmentRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final PriceService priceService;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    // ===================== Yêu cầu lấy đồ =====================

    /** Khách gửi yêu cầu lấy đồ (hoặc Quản lý xưởng tạo hộ khi khách gọi điện): mở đơn "Chờ lấy đồ". */
    @Transactional
    public Response create(CreateRequest request) {
        User user = currentUserService.getCurrentUser();
        Customer customer;
        if (user.getRole().isCustomerRole()) {
            customer = requireCustomer(user);
        } else if (user.getRole() == Role.WORKSHOP_ADMIN) {
            if (request.customerId() == null) {
                throw ApiException.badRequest("Vui lòng chọn khách hàng");
            }
            customer = customerRepository.findById(request.customerId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        } else {
            throw ApiException.forbidden("Bạn không có quyền tạo yêu cầu lấy đồ");
        }
        if (!customer.isActive()) {
            throw ApiException.badRequest("Khách hàng này đã ngừng hợp tác, không thể tạo đơn mới");
        }

        TimeSlot slot = request.timeSlot() != null ? request.timeSlot() : TimeSlot.ANYTIME;
        Order order = Order.builder()
                .customer(customer)
                .status(OrderStatus.PICKUP_PENDING)
                .requestedBy(user.getUsername())
                .preferredDate(request.preferredDate())
                .timeSlot(slot)
                .estimatedBags(request.estimatedBags())
                .pickupNote(blankToNull(request.note()))
                .build();
        order.addHistory(null, OrderStatus.PICKUP_PENDING, user.getUsername(),
                "Yêu cầu lấy đồ ngày " + DAY.format(request.preferredDate()) + ", " + slot.getLabel().toLowerCase()
                        + (request.estimatedBags() != null ? ", khoảng " + request.estimatedBags() + " túi" : "")
                        + (user.getRole() == Role.WORKSHOP_ADMIN ? " (xưởng tạo hộ khách)" : ""));
        Order saved = orderRepository.saveAndFlush(order);
        notificationService.pickupRequested(saved, user);
        return Response.from(saved, true);
    }

    // ===================== Tài xế nhận / trả việc =====================

    /** Tài xế nhận việc đang chờ: đi lấy đồ (đơn chờ lấy) hoặc đi giao (đơn đã giặt xong). */
    @Transactional
    public Response accept(Long id) {
        User driver = currentUserService.getCurrentUser();
        Order order = loadForUpdate(id, driver);
        switch (order.getStatus()) {
            case PICKUP_PENDING -> {
                order.setDriver(driver);
                moveTo(order, OrderStatus.PICKUP_ASSIGNED, driver.getUsername(),
                        "Tài xế " + driver.getDisplayName() + " nhận việc lấy đồ");
                notificationService.pickupAccepted(order, driver);
            }
            case WASHED -> {
                order.setDeliveryDriver(driver);
                moveTo(order, OrderStatus.DELIVERING, driver.getUsername(),
                        "Tài xế " + driver.getDisplayName() + " nhận giao trả");
                notificationService.orderDelivering(order, driver);
            }
            default -> throw ApiException.conflict("Việc này đã có tài xế nhận hoặc không còn chờ tài xế");
        }
        return Response.from(orderRepository.saveAndFlush(order), true);
    }

    /** Tài xế trả lại việc mình đang nhận (để người khác nhận). */
    @Transactional
    public Response release(Long id) {
        User driver = currentUserService.getCurrentUser();
        Order order = loadForUpdate(id, driver);
        if (order.getStatus() == OrderStatus.PICKUP_ASSIGNED && isUser(order.getDriver(), driver)) {
            order.setDriver(null);
            moveTo(order, OrderStatus.PICKUP_PENDING, driver.getUsername(), "Tài xế trả lại việc lấy đồ");
        } else if (order.getStatus() == OrderStatus.DELIVERING && isUser(order.getDeliveryDriver(), driver)) {
            order.setDeliveryDriver(null);
            moveTo(order, OrderStatus.WASHED, driver.getUsername(), "Tài xế trả lại việc giao trả");
        } else {
            throw ApiException.badRequest("Bạn chỉ trả lại được việc mình đang nhận");
        }
        return Response.from(orderRepository.saveAndFlush(order), true);
    }

    /** Quản lý xưởng phân công (hoặc đổi) tài xế lấy đồ / tài xế giao trả. */
    @Transactional
    public Response assign(Long id, Long driverId) {
        User admin = currentUserService.getCurrentUser();
        Order order = loadForUpdate(id, admin);
        User driver = userRepository.findById(driverId)
                .filter(u -> u.getRole() == Role.DRIVER && u.isEnabled())
                .orElseThrow(() -> ApiException.badRequest("Tài xế không hợp lệ hoặc đã bị khóa"));
        OrderStatus status = order.getStatus();

        if (status.isPickupStage()) {
            String old = order.getDriver() == null ? null : order.getDriver().getDisplayName();
            order.setDriver(driver);
            if (status == OrderStatus.PICKUP_PENDING) {
                moveTo(order, OrderStatus.PICKUP_ASSIGNED, admin.getUsername(),
                        "Phân công tài xế " + driver.getDisplayName() + " lấy đồ");
            } else {
                order.addHistory(status, status, admin.getUsername(),
                        "Đổi tài xế lấy đồ: " + old + " → " + driver.getDisplayName());
            }
            notificationService.pickupAssigned(order, admin);
        } else if (status == OrderStatus.WASHED || status == OrderStatus.DELIVERING
                || status == OrderStatus.DELIVERY_ISSUE) {
            String old = order.getDeliveryDriver() == null ? null : order.getDeliveryDriver().getDisplayName();
            order.setDeliveryDriver(driver);
            if (status == OrderStatus.WASHED) {
                moveTo(order, OrderStatus.DELIVERING, admin.getUsername(),
                        "Phân công tài xế " + driver.getDisplayName() + " giao trả");
            } else {
                order.addHistory(status, status, admin.getUsername(),
                        "Đổi tài xế giao trả: " + old + " → " + driver.getDisplayName());
            }
            notificationService.deliveryAssigned(order, admin, status == OrderStatus.WASHED);
        } else {
            throw ApiException.badRequest("Chỉ phân công tài xế khi đơn đang chờ lấy đồ hoặc đã giặt xong");
        }
        return Response.from(orderRepository.saveAndFlush(order), true);
    }

    // ===================== Nhận đồ =====================

    /**
     * Tài xế nhận đồ: đã kiểm số lượng cùng khách và có chữ ký / ảnh của khách (đính kèm trước khi gọi).
     * Quản lý xưởng cũng nhận được (khách mang đồ đến xưởng), không bắt buộc chữ ký.
     */
    @Transactional
    public Response receive(Long id, ReceiveRequest request) {
        User user = currentUserService.getCurrentUser();
        Order order = loadForUpdate(id, user);
        if (!order.getStatus().isPickupStage()) {
            throw ApiException.badRequest("Đơn này đã được nhận đồ");
        }
        boolean atWorkshop = user.getRole() == Role.WORKSHOP_ADMIN;
        if (!atWorkshop) {
            if (order.getDriver() != null && !isUser(order.getDriver(), user)) {
                throw ApiException.badRequest("Việc này đã được giao cho tài xế khác");
            }
            if (!attachmentRepository.existsByOrderIdAndKindIn(order.getId(), PICKUP_PROOF)) {
                throw ApiException.badRequest("Cần chữ ký của khách hoặc ảnh túi đồ để xác nhận số lượng khi nhận đồ");
            }
            order.setDriver(user);
        }

        Function<ServicePrice, BigDecimal> pricer = priceService.pricerFor(order.getCustomer().getId());
        order.getItems().clear();
        for (ItemRequest dto : request.items()) {
            // Tài xế không thấy / không nhập giá; xưởng nhận đồ tại quầy thì nhập được giá luôn
            order.addItem(buildItem(dto.servicePriceId(), dto.itemName(), dto.unit(), dto.quantity(),
                    atWorkshop ? dto.unitPrice() : null, pricer, Set.of()));
        }
        LocalDateTime now = LocalDateTime.now();
        order.setTotalBags(request.totalBags());
        order.setDescription(blankToNull(request.description()));
        order.setReceivedAt(now);
        // Hạn trả tính từ lúc nhận đồ theo cam kết với từng khách (khách sạn 24h, nhà hàng 48h...)
        order.setDueAt(now.plusHours(order.getCustomer().getTurnaroundHours()));
        moveTo(order, OrderStatus.RECEIVED, user.getUsername(),
                (atWorkshop ? "Xưởng nhận đồ khách mang đến (" : "Tài xế nhận đồ, khách đã ký xác nhận (")
                        + request.totalBags() + " túi): " + summarize(order.getItems()));
        Order saved = orderRepository.saveAndFlush(order);
        notificationService.orderReceived(saved, user);
        return Response.from(saved, true);
    }

    // ===================== Xem đơn =====================

    /**
     * Một trang đơn trong phạm vi của người đăng nhập. Lọc, sắp xếp, phân trang đều chạy trong CSDL;
     * chi tiết (khách, tài xế, dòng đồ giặt) chỉ được nạp cho các đơn của trang đó.
     */
    @Transactional(readOnly = true)
    public PageResponse searchOrders(Filter filter, OrderDtos.Sort sort, int page, int size) {
        User user = currentUserService.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();

        // Phạm vi + nhóm đồ + từ khóa: dùng chung cho danh sách và các con số đếm
        Specification<Order> base = filter.cancelled() ? cancelledScope(user) : scope(user);
        if (filter.mine() && user.getRole() == Role.DRIVER) {
            base = base.and(OrderSpecs.assignedTo(user.getId()));
        }
        if (filter.category() != null) {
            base = base.and(OrderSpecs.hasCategory(filter.category()));
        }
        String keyword = blankToNull(filter.keyword());
        if (keyword != null) {
            base = base.and(OrderSpecs.keyword(keyword));
        }
        Specification<Order> withOverdue = filter.overdueOnly() ? base.and(OrderSpecs.overdue(now)) : base;
        boolean byStatus = filter.statuses() != null && !filter.statuses().isEmpty();
        Specification<Order> spec = byStatus ? withOverdue.and(OrderSpecs.statusIn(filter.statuses())) : withOverdue;

        Sort order = switch (sort) {
            case DUE -> Sort.by(Sort.Order.asc("dueAt").nullsLast(), Sort.Order.asc("id"));
            case PICKUP -> Sort.by(Sort.Order.asc("preferredDate").nullsLast(), Sort.Order.asc("id"));
            case NEWEST -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };
        Page<Order> result = orderRepository.findAll(spec, PageRequest.of(page, size, order));

        // Bước 2: nạp chi tiết cho các đơn trong trang (tránh phân trang trên tập đã JOIN dòng đồ giặt)
        List<Long> ids = result.getContent().stream().map(Order::getId).toList();
        Map<Long, Order> loaded = orderRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        List<Response> content = ids.stream().map(id -> Response.from(loaded.get(id), false)).toList();

        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.flow()) {
            counts.put(s, 0L);
        }
        orderRepository.countByStatus(withOverdue).forEach((s, n) -> {
            if (!s.isLegacy()) {
                counts.put(s, n);
            }
        });
        long overdueCount = orderRepository.count(base.and(OrderSpecs.overdue(now)));

        return new PageResponse(content, result.getTotalElements(), page, size, counts, overdueCount);
    }

    /** Điều kiện "đơn chưa hủy mà người dùng được phép thấy", chạy trong CSDL. Dùng chung cho báo cáo. */
    public Specification<Order> scope(User user) {
        Specification<Order> alive = OrderSpecs.notDeleted();
        return switch (user.getRole()) {
            case WORKSHOP_ADMIN, WORKSHOP_ACCOUNTANT -> alive;
            case DRIVER -> alive.and(OrderSpecs.visibleToDriver(user.getId()));
            case CUSTOMER_STAFF, CUSTOMER_MANAGER -> alive.and(OrderSpecs.customer(requireCustomerId(user)));
        };
    }

    /** Đơn / yêu cầu đã hủy mà người dùng được xem lại. */
    private Specification<Order> cancelledScope(User user) {
        Specification<Order> gone = OrderSpecs.deleted();
        return switch (user.getRole()) {
            case WORKSHOP_ADMIN, WORKSHOP_ACCOUNTANT -> gone;
            case DRIVER -> gone.and(OrderSpecs.involvesDriver(user.getId()));
            case CUSTOMER_STAFF, CUSTOMER_MANAGER -> gone.and(OrderSpecs.customer(requireCustomerId(user)));
        };
    }

    @Transactional(readOnly = true)
    public Response getOrder(Long id) {
        User user = currentUserService.getCurrentUser();
        Order order = orderRepository.findDetailById(id)
                .filter(o -> !o.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        assertCanAccess(order, user);
        return Response.from(order, true, user.getRole().isCustomerRole() ? null : notesOf(order.getId()));
    }

    /** Danh sách đơn (đã bỏ đơn hủy) mà người dùng được phép thấy. Dùng chung cho báo cáo. */
    @Transactional(readOnly = true)
    public List<Order> visibleOrders(User user) {
        return switch (user.getRole()) {
            case WORKSHOP_ADMIN, WORKSHOP_ACCOUNTANT -> orderRepository.findByIsDeletedFalseOrderByCreatedAtDesc();
            case DRIVER -> orderRepository.findByDriverIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getId());
            case CUSTOMER_STAFF, CUSTOMER_MANAGER ->
                orderRepository.findByCustomerIdAndIsDeletedFalseOrderByCreatedAtDesc(requireCustomerId(user));
        };
    }

    // ===================== Ghi chú nội bộ =====================

    /** Xưởng, kế toán, tài xế ghi lại vấn đề phát sinh (VD đã gọi khách, khách hẹn lại). Không sửa / xóa. */
    @Transactional
    public List<OrderDtos.NoteResponse> addNote(Long id, String body) {
        User user = currentUserService.getCurrentUser();
        Order order = loadForUpdate(id, user);
        noteRepository.save(OrderNote.builder()
                .order(order)
                .author(user.getUsername())
                .authorRole(user.getRole())
                .body(body.trim())
                .build());
        return notesOf(order.getId());
    }

    private List<OrderDtos.NoteResponse> notesOf(Long orderId) {
        return noteRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId).stream()
                .map(OrderDtos.NoteResponse::from).toList();
    }

    // ===================== Giao trả =====================

    /** Tài xế giao xong (khách đã ký / có ảnh lúc giao): đơn hoàn thành và tính vào công nợ. */
    @Transactional
    public Response delivered(Long id) {
        User driver = currentUserService.getCurrentUser();
        Order order = loadForUpdate(id, driver);
        if (order.getStatus() != OrderStatus.DELIVERING || !isUser(order.getDeliveryDriver(), driver)) {
            throw ApiException.badRequest("Chỉ tài xế đang giao đơn này mới bấm giao xong được");
        }
        if (!attachmentRepository.existsByOrderIdAndKindIn(order.getId(), DELIVERY_PROOF)) {
            throw ApiException.badRequest("Cần chữ ký của khách hoặc ảnh lúc giao trước khi báo giao xong");
        }
        complete(order, driver.getUsername(), "Tài xế giao xong, khách đã ký nhận / có ảnh lúc giao");
        notificationService.orderCompleted(order, driver);
        return Response.from(orderRepository.saveAndFlush(order), true);
    }

    /** Khách xác nhận đã nhận đủ đồ sạch: đơn hoàn thành và bắt đầu tính hạn thanh toán. */
    @Transactional
    public Response confirmReceipt(Long id) {
        User user = currentUserService.getCurrentUser();
        Order order = loadForUpdate(id, user);

        if (order.getStatus() != OrderStatus.DELIVERING) {
            throw ApiException.badRequest("Chỉ xác nhận nhận đồ được khi đơn đang giao");
        }
        complete(order, user.getUsername(), "Khách hàng xác nhận đã nhận đủ đồ");
        return Response.from(orderRepository.saveAndFlush(order), true);
    }

    /** Khách báo thiếu/hỏng khi nhận: xưởng sẽ giao bù hoặc điều chỉnh rồi chốt. */
    @Transactional
    public Response reportReceiptIssue(Long id, ReceiptIssueRequest request) {
        User user = currentUserService.getCurrentUser();
        Order order = loadForUpdate(id, user);

        if (order.getStatus() != OrderStatus.DELIVERING) {
            throw ApiException.badRequest("Chỉ báo sự cố được khi đơn đang giao");
        }
        String note = request.note().trim();
        String line = "[%s - %s] Sự cố giao trả: %s".formatted(TS.format(LocalDateTime.now()), user.getUsername(), note);
        order.setDescription(order.getDescription() == null ? line : order.getDescription() + "\n" + line);
        moveTo(order, OrderStatus.DELIVERY_ISSUE, user.getUsername(), "Khách báo sự cố khi nhận đồ: " + note);
        notificationService.orderReceiptIssue(order, user, note);
        return Response.from(orderRepository.saveAndFlush(order), true);
    }

    // ===================== Quản lý xưởng điều phối =====================

    /** Bắt đầu giặt, đã giặt xong, giao bù sau sự cố, chốt hoàn thành. */
    @Transactional
    public Response updateStatus(Long id, StatusRequest request) {
        User user = currentUserService.getCurrentUser();
        Order order = loadForUpdate(id, user);
        OrderStatus target = request.status();
        OrderStatus from = order.getStatus();

        if (!ADMIN_TARGETS.contains(target)) {
            throw ApiException.badRequest("Không thể đặt thủ công trạng thái \"" + target.getLabel() + "\"");
        }
        // Chốt đủ đơn giá khi giặt xong để giao xong là có số tiền chính xác
        if ((target == OrderStatus.WASHED || target == OrderStatus.COMPLETED) && !order.isBillable()) {
            throw ApiException.badRequest("Cần nhập đơn giá cho mọi dòng đồ giặt trước (Chi tiết & giá)");
        }
        if (target == OrderStatus.DELIVERING && (from != OrderStatus.DELIVERY_ISSUE || order.getDeliveryDriver() == null)) {
            throw ApiException.badRequest("Phân công tài xế để giao trả đơn này");
        }

        String note = blankToNull(request.note());
        switch (target) {
            case COMPLETED -> {
                // Xưởng chốt thay khi khách không bấm xác nhận, hoặc sau khi xử lý xong sự cố
                complete(order, user.getUsername(), note != null ? note : "Xưởng chốt hoàn thành đơn");
                auditService.record(AuditAction.ORDER_COMPLETED_BY_WORKSHOP, order.getId(),
                        order.getCustomer().getId(),
                        "Xưởng chốt hoàn thành đơn #" + order.getId() + " (" + order.getCustomer().getName() + ")",
                        new Changes().line("Từ trạng thái: " + from.getLabel())
                                .line("Ghi vào công nợ: " + AuditService.money(order.getTotalAmount()))
                                .line(note == null ? null : "Ghi chú: " + note));
            }
            case WASHING -> moveTo(order, target, user.getUsername(),
                    note != null ? note : "Xưởng nhận đồ, bắt đầu giặt");
            case WASHED -> {
                moveTo(order, target, user.getUsername(),
                        note != null ? note : "Giặt xong, đã kiểm lại số lượng: " + order.getTotalBags() + " túi, "
                                + summarize(order.getItems()));
                notificationService.orderWashed(order, user);
            }
            default -> {
                moveTo(order, target, user.getUsername(), note != null ? note : "Giao bù phần thiếu / hỏng");
                notificationService.orderDelivering(order, user);
            }
        }
        return Response.from(orderRepository.saveAndFlush(order), true);
    }

    @Transactional
    public Response updateDueDate(Long id, DueRequest request) {
        User user = currentUserService.getCurrentUser();
        Order order = loadForUpdate(id, user);

        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw ApiException.badRequest("Đơn đã hoàn thành, không đổi hạn trả được");
        }
        if (order.getStatus().isPickupStage()) {
            throw ApiException.badRequest("Chưa nhận đồ nên chưa có hạn trả");
        }
        String old = order.getDueAt() == null ? "chưa có" : TS.format(order.getDueAt());
        String note = blankToNull(request.note());
        auditService.record(AuditAction.ORDER_DUE_CHANGED, order.getId(), order.getCustomer().getId(),
                "Đổi hạn trả đơn #" + order.getId() + " (" + order.getCustomer().getName() + ")",
                new Changes().field("Hạn trả", order.getDueAt(), request.dueAt()).line(note == null ? null : "Lý do: " + note));
        order.setDueAt(request.dueAt());
        order.addHistory(order.getStatus(), order.getStatus(), user.getUsername(),
                "Đổi hạn trả: " + old + " → " + TS.format(request.dueAt()) + (note != null ? ". " + note : ""));
        return Response.from(orderRepository.saveAndFlush(order), true);
    }

    /**
     * Hủy (xóa mềm, giữ để đối soát). Khách chỉ hủy được yêu cầu của mình khi tài xế chưa lấy đồ;
     * Quản lý xưởng hủy được mọi đơn chưa hoàn thành.
     */
    @Transactional
    public Response cancelOrder(Long id, String reason) {
        User user = currentUserService.getCurrentUser();
        Order order = loadForUpdate(id, user);
        boolean pickup = order.getStatus().isPickupStage();

        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw ApiException.badRequest("Đơn đã hoàn thành, không thể hủy");
        }
        if (user.getRole() != Role.WORKSHOP_ADMIN && !pickup) {
            throw ApiException.badRequest("Tài xế đã lấy đồ, vui lòng gọi xưởng nếu cần hủy");
        }
        String why = blankToNull(reason);
        order.setDeleted(true);
        order.setCancelReason(why);
        order.setCancelledBy(user.getUsername());
        order.setCancelledAt(LocalDateTime.now());
        String what = pickup ? "Hủy yêu cầu lấy đồ" : "Hủy đơn";
        auditService.record(AuditAction.ORDER_CANCELLED, order.getId(), order.getCustomer().getId(),
                what + " #" + order.getId() + " (" + order.getCustomer().getName() + ")",
                new Changes().line("Trạng thái lúc hủy: " + order.getStatus().getLabel())
                        .line("Lý do: " + (why != null ? why : "Không nêu lý do"))
                        .line(pickup ? null : "Giá trị đơn: " + AuditService.money(order.getTotalAmount())));
        order.addHistory(order.getStatus(), order.getStatus(), user.getUsername(),
                what + ". Lý do: " + (why != null ? why : "Không nêu lý do"));
        notificationService.orderCancelled(order, user, why);
        return Response.from(orderRepository.saveAndFlush(order), true);
    }

    // ===================== Chi tiết đồ giặt và đơn giá =====================

    @Transactional
    public Response updateItems(Long id, ItemsRequest request) {
        User user = currentUserService.getCurrentUser();
        Order order = loadForUpdate(id, user);

        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw ApiException.badRequest("Đơn đã hoàn thành, không thể sửa chi tiết");
        }
        if (order.getStatus().isPickupStage()) {
            throw ApiException.badRequest("Tài xế chưa nhận đồ, chưa nhập chi tiết được");
        }

        // Mục bảng giá đã ẩn vẫn được giữ nếu đơn này đang dùng nó
        Set<Long> alreadyUsed = order.getItems().stream()
                .filter(i -> i.getServicePrice() != null)
                .map(i -> i.getServicePrice().getId())
                .collect(Collectors.toSet());
        Function<ServicePrice, BigDecimal> pricer = priceService.pricerFor(order.getCustomer().getId());

        List<OrderItem> rebuilt = new ArrayList<>();
        for (ItemRequest dto : request.items()) {
            rebuilt.add(buildItem(dto.servicePriceId(), dto.itemName(), dto.unit(), dto.quantity(),
                    dto.unitPrice(), pricer, alreadyUsed));
        }

        // Đơn đã giặt xong / đang giao thì số tiền phải luôn đầy đủ để giao xong là chốt được ngay
        boolean priced = order.getStatus() == OrderStatus.WASHED || order.getStatus() == OrderStatus.DELIVERING
                || order.getStatus() == OrderStatus.DELIVERY_ISSUE;
        if (priced && rebuilt.stream().anyMatch(i -> i.getUnitPrice() == null)) {
            throw ApiException.badRequest("Đơn đã giặt xong: mọi dòng đồ giặt phải có đơn giá");
        }

        String beforeLines = priced(order.getItems());
        BigDecimal beforeTotal = order.getTotalAmount();
        // Sửa trực tiếp trên danh sách hiện có (orphanRemoval sẽ xóa dòng cũ)
        order.getItems().clear();
        rebuilt.forEach(order::addItem);
        order.addHistory(order.getStatus(), order.getStatus(), user.getUsername(),
                "Cập nhật chi tiết đồ giặt và đơn giá (" + rebuilt.size() + " dòng): " + summarize(rebuilt));
        String afterLines = priced(order.getItems());
        if (!beforeLines.equals(afterLines)) {
            auditService.record(AuditAction.ORDER_ITEMS_UPDATED, order.getId(), order.getCustomer().getId(),
                    "Sửa chi tiết đơn #" + order.getId() + " (" + order.getCustomer().getName() + ")",
                    new Changes().field("Tổng tiền", beforeTotal, order.getTotalAmount())
                            .line("Trước: " + beforeLines).line("Sau: " + afterLines));
        }
        return Response.from(orderRepository.saveAndFlush(order), true);
    }

    // ===================== Hàm dùng nội bộ =====================

    /**
     * Tạo một dòng đồ giặt. Chọn từ bảng giá thì chép lại tên/nhóm/dịch vụ/đơn vị và lấy giá theo khách
     * (trừ khi người nhập đã ghi đè giá); nhập tay thì xếp vào nhóm "Khác" và để trống giá nếu không nhập.
     */
    private OrderItem buildItem(Long servicePriceId, String itemName, PriceUnit unit, BigDecimal quantity,
            BigDecimal unitPrice, Function<ServicePrice, BigDecimal> pricer, Set<Long> allowInactive) {
        OrderItem item;
        if (servicePriceId != null) {
            ServicePrice price = priceService.find(servicePriceId);
            if (!price.isActive() && !allowInactive.contains(price.getId())) {
                throw ApiException.badRequest("\"" + price.getName() + "\" đã ngừng áp dụng trong bảng giá");
            }
            item = OrderItem.builder()
                    .servicePrice(price)
                    .itemName(price.getName())
                    .category(price.getCategory())
                    .serviceType(price.getServiceType())
                    .unit(price.getUnit())
                    .unitPrice(unitPrice != null ? money(unitPrice) : pricer.apply(price))
                    .build();
        } else {
            String name = blankToNull(itemName);
            if (name == null) {
                throw ApiException.badRequest("Vui lòng chọn loại đồ trong bảng giá hoặc nhập tên đồ");
            }
            item = OrderItem.builder()
                    .itemName(name)
                    .category(ItemCategory.OTHER)
                    .serviceType(ServiceType.WASH)
                    .unit(unit != null ? unit : PriceUnit.PIECE)
                    .unitPrice(unitPrice != null ? money(unitPrice) : null)
                    .build();
        }
        item.setQuantity(checkQuantity(quantity, item));
        return item;
    }

    private static BigDecimal checkQuantity(BigDecimal quantity, OrderItem item) {
        if (!item.getUnit().accepts(quantity)) {
            throw ApiException.badRequest("Số lượng \"" + item.getItemName() + "\" tính theo "
                    + item.getUnit().getLabel() + " nên phải là số nguyên");
        }
        return quantity.setScale(2, RoundingMode.HALF_UP);
    }

    /** Các dòng kèm đơn giá cho nhật ký, VD "Khăn bàn 10 cái × 5.000 đ". */
    private static String priced(List<OrderItem> items) {
        if (items.isEmpty()) {
            return "chưa có chi tiết";
        }
        return items.stream()
                .map(i -> i.getItemName() + " " + plain(i.getQuantity()) + " " + i.getUnit().getLabel() + " × "
                        + (i.getUnitPrice() == null ? "chưa có giá" : AuditService.money(i.getUnitPrice())))
                .collect(Collectors.joining("; "));
    }

    /** "Khăn bàn 50 cái, Ga giường đôi 20 cái, Chăn ga (tính cân) 12.5 kg". */
    private static String summarize(List<OrderItem> items) {
        if (items.isEmpty()) {
            return "chưa có chi tiết";
        }
        return items.stream()
                .map(i -> i.getItemName() + " " + plain(i.getQuantity()) + " " + i.getUnit().getLabel())
                .collect(Collectors.joining(", "));
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private Order loadForUpdate(Long id, User user) {
        Order order = orderRepository.findByIdForUpdate(id)
                .filter(o -> !o.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        assertCanAccess(order, user);
        return order;
    }

    /** Đơn (chưa hủy) trong phạm vi người dùng được xem; ngoài phạm vi trả 404. Dùng cho ảnh đính kèm. */
    @Transactional(readOnly = true)
    public Order requireAccessible(Long id, User user) {
        Order order = orderRepository.findById(id)
                .filter(o -> !o.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        assertCanAccess(order, user);
        return order;
    }

    /** Trả về 404 (thay vì 403) khi đơn không thuộc phạm vi, để không lộ việc đơn có tồn tại. */
    private void assertCanAccess(Order order, User user) {
        boolean allowed = switch (user.getRole()) {
            case WORKSHOP_ADMIN, WORKSHOP_ACCOUNTANT -> true;
            case DRIVER -> isUser(order.getDriver(), user) || isUser(order.getDeliveryDriver(), user)
                    || OrderSpecs.OPEN_FOR_DRIVERS.contains(order.getStatus());
            case CUSTOMER_STAFF, CUSTOMER_MANAGER ->
                order.getCustomer().getId().equals(requireCustomerId(user));
        };
        if (!allowed) {
            throw ApiException.notFound("Không tìm thấy đơn hàng");
        }
    }

    private static boolean isUser(User assigned, User user) {
        return assigned != null && Objects.equals(assigned.getId(), user.getId());
    }

    private Long requireCustomerId(User user) {
        return requireCustomer(user).getId();
    }

    private static Customer requireCustomer(User user) {
        if (user.getCustomer() == null) {
            throw ApiException.forbidden("Tài khoản này chưa được liên kết với khách hàng nào");
        }
        return user.getCustomer();
    }

    private void moveTo(Order order, OrderStatus target, String actor, String note) {
        OrderStatus from = order.getStatus();
        if (!from.canTransitionTo(target)) {
            throw ApiException.badRequest("Không thể chuyển đơn từ \"" + from.getLabel()
                    + "\" sang \"" + target.getLabel() + "\"");
        }
        order.setStatus(target);
        order.addHistory(from, target, actor, note);
    }

    /** Hoàn thành đơn: ghi mốc hoàn thành để tính hạn thanh toán theo kỳ của khách. */
    private void complete(Order order, String actor, String note) {
        if (!order.isBillable()) {
            throw ApiException.badRequest("Đơn còn dòng chưa có đơn giá, vui lòng báo xưởng cập nhật trước khi chốt");
        }
        moveTo(order, OrderStatus.COMPLETED, actor, note);
        order.setCompletedAt(LocalDateTime.now());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}

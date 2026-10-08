package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.OrderDtos;
import com.washpro.washpro_b2b.dto.OrderDtos.CreateRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.DiscrepancyRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.DueRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.Filter;
import com.washpro.washpro_b2b.dto.OrderDtos.ReceiptIssueRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.ItemCountRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.ItemRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.ItemsRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.PageResponse;
import com.washpro.washpro_b2b.dto.OrderDtos.Response;
import com.washpro.washpro_b2b.dto.OrderDtos.StatusRequest;
import com.washpro.washpro_b2b.entity.ItemCategory;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderItem;
import com.washpro.washpro_b2b.entity.OrderStatus;
import com.washpro.washpro_b2b.entity.PriceUnit;
import com.washpro.washpro_b2b.entity.Restaurant;
import com.washpro.washpro_b2b.entity.ServicePrice;
import com.washpro.washpro_b2b.entity.ServiceType;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.repository.OrderRepository;
import com.washpro.washpro_b2b.repository.OrderSpecs;
import com.washpro.washpro_b2b.repository.RestaurantRepository;
import com.washpro.washpro_b2b.security.CurrentUserService;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Toàn bộ quy tắc nghiệp vụ của đơn giặt:
 * - Mỗi vai trò chỉ thấy/thao tác trên đơn thuộc phạm vi của mình (tài xế: đơn của mình, khách hàng: đơn của mình).
 * - Trạng thái chỉ đi theo đúng quy trình (OrderStatus#canTransitionTo), không lùi, không nhảy bước.
 * - Đồ giặt được đếm theo từng loại (khăn bàn, ga giường, đồng phục...); giá lấy theo bảng giá của khách.
 * - Mọi thay đổi đều được ghi vào nhật ký (OrderHistory) để đối soát.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Các trạng thái Quản lý xưởng được phép đặt thủ công (WASHING chỉ đến từ việc khách hàng xác nhận). */
    private static final Set<OrderStatus> ADMIN_TARGETS = EnumSet.of(
            OrderStatus.PENDING_CONFIRM, OrderStatus.DELIVERING, OrderStatus.COMPLETED);

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final PriceService priceService;
    private final PickupService pickupService;
    private final CurrentUserService currentUserService;

    // ===================== Tạo đơn =====================

    @Transactional
    public Response createOrder(CreateRequest request) {
        User driver = currentUserService.getCurrentUser();

        Restaurant restaurant = restaurantRepository.findById(request.restaurantId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        if (!restaurant.isActive()) {
            throw ApiException.badRequest("Khách hàng này đã ngừng hợp tác, không thể tạo đơn mới");
        }

        Order order = Order.builder()
                .driver(driver)
                .restaurant(restaurant)
                .status(OrderStatus.PENDING_CONFIRM)
                .totalBags(request.totalBags())
                .description(blankToNull(request.description()))
                // Hạn trả tính từ lúc nhận đồ theo cam kết với từng khách (khách sạn 24h, nhà hàng 48h...)
                .dueAt(LocalDateTime.now().plusHours(restaurant.getTurnaroundHours()))
                .build();

        // Tài xế chỉ đếm số lượng; giá được điền tự động theo bảng giá của khách (tài xế không thấy giá)
        Function<ServicePrice, BigDecimal> pricer = priceService.pricerFor(restaurant.getId());
        for (ItemRequest dto : request.items()) {
            order.addItem(buildItem(dto.servicePriceId(), dto.itemName(), dto.unit(), dto.quantity(),
                    null, pricer, Set.of()));
        }
        order.addHistory(null, OrderStatus.PENDING_CONFIRM, driver.getUsername(),
                "Tài xế tạo đơn (" + request.totalBags() + " túi): " + summarize(order.getItems())
                        + (request.pickupRequestId() != null ? " · theo yêu cầu lấy đồ #" + request.pickupRequestId() : ""));

        Order saved = orderRepository.saveAndFlush(order);
        if (request.pickupRequestId() != null) {
            pickupService.completeWithOrder(request.pickupRequestId(), saved, driver);
        }
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
        Specification<Order> base = scope(user);
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

        Sort order = sort == OrderDtos.Sort.DUE
                ? Sort.by(Sort.Order.asc("dueAt").nullsLast(), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Page<Order> result = orderRepository.findAll(spec, PageRequest.of(page, size, order));

        // Bước 2: nạp chi tiết cho các đơn trong trang (tránh phân trang trên tập đã JOIN dòng đồ giặt)
        List<Long> ids = result.getContent().stream().map(Order::getId).toList();
        Map<Long, Order> loaded = orderRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        List<Response> content = ids.stream().map(id -> Response.from(loaded.get(id), false)).toList();

        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) {
            counts.put(s, 0L);
        }
        counts.putAll(orderRepository.countByStatus(withOverdue));
        long overdueCount = orderRepository.count(base.and(OrderSpecs.overdue(now)));

        return new PageResponse(content, result.getTotalElements(), page, size, counts, overdueCount);
    }

    /** Điều kiện "đơn chưa hủy mà người dùng được phép thấy", chạy trong CSDL. */
    private Specification<Order> scope(User user) {
        Specification<Order> alive = OrderSpecs.notDeleted();
        return switch (user.getRole()) {
            case WORKSHOP_ADMIN, WORKSHOP_ACCOUNTANT -> alive;
            case DRIVER -> alive.and(OrderSpecs.driver(user.getId()));
            case RESTAURANT_STAFF, RESTAURANT_MANAGER -> alive.and(OrderSpecs.restaurant(requireRestaurantId(user)));
        };
    }

    @Transactional(readOnly = true)
    public Response getOrder(Long id) {
        User user = currentUserService.getCurrentUser();
        Order order = orderRepository.findDetailById(id)
                .filter(o -> !o.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        assertCanAccess(order, user);
        return Response.from(order, true);
    }

    /** Danh sách đơn (đã bỏ đơn hủy) mà người dùng được phép thấy. Dùng chung cho báo cáo. */
    @Transactional(readOnly = true)
    public List<Order> visibleOrders(User user) {
        return switch (user.getRole()) {
            case WORKSHOP_ADMIN, WORKSHOP_ACCOUNTANT -> orderRepository.findByIsDeletedFalseOrderByCreatedAtDesc();
            case DRIVER -> orderRepository.findByDriverIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getId());
            case RESTAURANT_STAFF, RESTAURANT_MANAGER ->
                orderRepository.findByRestaurantIdAndIsDeletedFalseOrderByCreatedAtDesc(requireRestaurantId(user));
        };
    }

    // ===================== Khách hàng xác nhận =====================

    @Transactional
    public Response approveOrder(Long id) {
        User user = currentUserService.getCurrentUser();
        Order order = loadForUpdate(id, user);

        if (order.getStatus() != OrderStatus.PENDING_CONFIRM && order.getStatus() != OrderStatus.DISCREPANCY) {
            throw ApiException.badRequest("Chỉ xác nhận được đơn đang chờ xác nhận hoặc đang có sai lệch");
        }
        moveTo(order, OrderStatus.WASHING, user.getUsername(),
                "Khách hàng xác nhận số lượng: " + order.getTotalBags() + " túi, " + summarize(order.getItems())
                        + ". Chuyển xưởng giặt");
        return Response.from(orderRepository.saveAndFlush(order), true);
    }

    // ===================== Báo sai lệch (tài xế / khách hàng) =====================

    @Transactional
    public Response reportDiscrepancy(Long id, DiscrepancyRequest request) {
        User user = currentUserService.getCurrentUser();
        Order order = loadForUpdate(id, user);

        if (order.getStatus() != OrderStatus.PENDING_CONFIRM && order.getStatus() != OrderStatus.DISCREPANCY) {
            throw ApiException.badRequest("Chỉ báo sai lệch được khi đơn đang chờ xác nhận hoặc đang có sai lệch");
        }

        List<String> changes = new ArrayList<>();
        int oldBags = order.getTotalBags() == null ? 0 : order.getTotalBags();
        if (oldBags != request.totalBags()) {
            changes.add("Số túi %d → %d".formatted(oldBags, request.totalBags()));
            order.setTotalBags(request.totalBags());
        }
        if (request.items() != null && !request.items().isEmpty()) {
            changes.addAll(applyCounts(order, request.items()));
        }
        if (order.getItems().isEmpty()) {
            throw ApiException.badRequest("Đơn phải còn ít nhất một loại đồ");
        }

        String note = request.note().trim();
        String detail = (changes.isEmpty() ? "Không đổi số lượng" : String.join("; ", changes)) + ". " + note;

        // Bảo toàn ghi chú cũ: chỉ nối thêm, kèm thời điểm và người báo
        String line = "[%s - %s] Sai lệch: %s".formatted(TS.format(LocalDateTime.now()), user.getUsername(), detail);
        order.setDescription(order.getDescription() == null ? line : order.getDescription() + "\n" + line);

        OrderStatus from = order.getStatus();
        order.setStatus(OrderStatus.DISCREPANCY);
        order.addHistory(from, OrderStatus.DISCREPANCY, user.getUsername(), "Báo sai lệch: " + detail);
        return Response.from(orderRepository.saveAndFlush(order), true);
    }

    // ===================== Khách hàng nhận đồ sạch =====================

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
        return Response.from(orderRepository.saveAndFlush(order), true);
    }

    // ===================== Quản lý xưởng điều phối =====================

    @Transactional
    public Response updateStatus(Long id, StatusRequest request) {
        User user = currentUserService.getCurrentUser();
        Order order = loadForUpdate(id, user);
        OrderStatus target = request.status();

        if (!ADMIN_TARGETS.contains(target)) {
            throw ApiException.badRequest("Không thể đặt thủ công trạng thái \"" + target.getLabel() + "\"");
        }
        // Chốt đủ đơn giá trước khi giao để khách nhận đồ xong là có số tiền chính xác
        if ((target == OrderStatus.DELIVERING || target == OrderStatus.COMPLETED) && !order.isBillable()) {
            throw ApiException.badRequest("Cần nhập đơn giá cho mọi dòng đồ giặt trước khi giao trả");
        }

        String note = blankToNull(request.note());
        if (target == OrderStatus.COMPLETED) {
            // Xưởng chốt thay khi khách không bấm xác nhận, hoặc sau khi xử lý xong sự cố
            complete(order, user.getUsername(), note != null ? note : "Xưởng chốt hoàn thành đơn");
        } else {
            moveTo(order, target, user.getUsername(), note != null ? note : "Chuyển sang \"" + target.getLabel() + "\"");
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
        String old = order.getDueAt() == null ? "chưa có" : TS.format(order.getDueAt());
        order.setDueAt(request.dueAt());
        String note = blankToNull(request.note());
        order.addHistory(order.getStatus(), order.getStatus(), user.getUsername(),
                "Đổi hạn trả: " + old + " → " + TS.format(request.dueAt()) + (note != null ? ". " + note : ""));
        return Response.from(orderRepository.saveAndFlush(order), true);
    }

    @Transactional
    public Response cancelOrder(Long id, String reason) {
        User user = currentUserService.getCurrentUser();
        Order order = loadForUpdate(id, user);

        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw ApiException.badRequest("Đơn đã hoàn thành, không thể hủy");
        }
        order.setDeleted(true);
        String why = blankToNull(reason) != null ? reason.trim() : "Không nêu lý do";
        order.addHistory(order.getStatus(), order.getStatus(), user.getUsername(), "Hủy đơn. Lý do: " + why);
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

        // Mục bảng giá đã ẩn vẫn được giữ nếu đơn này đang dùng nó
        Set<Long> alreadyUsed = order.getItems().stream()
                .filter(i -> i.getServicePrice() != null)
                .map(i -> i.getServicePrice().getId())
                .collect(Collectors.toSet());
        Function<ServicePrice, BigDecimal> pricer = priceService.pricerFor(order.getRestaurant().getId());

        List<OrderItem> rebuilt = new ArrayList<>();
        for (ItemRequest dto : request.items()) {
            rebuilt.add(buildItem(dto.servicePriceId(), dto.itemName(), dto.unit(), dto.quantity(),
                    dto.unitPrice(), pricer, alreadyUsed));
        }

        // Đơn đã/đang giao thì số tiền phải luôn đầy đủ để khách xác nhận là chốt được ngay
        boolean outForDelivery = order.getStatus() == OrderStatus.DELIVERING
                || order.getStatus() == OrderStatus.DELIVERY_ISSUE;
        if (outForDelivery && rebuilt.stream().anyMatch(i -> i.getUnitPrice() == null)) {
            throw ApiException.badRequest("Đơn đang giao trả: mọi dòng đồ giặt phải có đơn giá");
        }

        // Sửa trực tiếp trên danh sách hiện có (orphanRemoval sẽ xóa dòng cũ)
        order.getItems().clear();
        rebuilt.forEach(order::addItem);
        order.addHistory(order.getStatus(), order.getStatus(), user.getUsername(),
                "Cập nhật chi tiết đồ giặt và đơn giá (" + rebuilt.size() + " dòng): " + summarize(rebuilt));
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

    /** Áp số lượng thực tế khi báo sai lệch; trả về mô tả từng thay đổi để ghi nhật ký. */
    private List<String> applyCounts(Order order, List<ItemCountRequest> counts) {
        Map<Long, OrderItem> byId = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        Function<ServicePrice, BigDecimal> pricer = priceService.pricerFor(order.getRestaurant().getId());
        List<String> changes = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        for (ItemCountRequest c : counts) {
            if (c.itemId() != null) {
                OrderItem item = byId.get(c.itemId());
                if (item == null || !seen.add(c.itemId())) {
                    throw ApiException.badRequest("Dòng đồ giặt không thuộc đơn này");
                }
                BigDecimal oldQty = item.getQuantity();
                if (c.quantity().signum() == 0) {
                    changes.add("%s: %s → 0 (không có)".formatted(item.getItemName(), plain(oldQty)));
                    item.setQuantity(BigDecimal.ZERO); // đánh dấu để xóa bên dưới
                } else if (c.quantity().compareTo(oldQty) != 0) {
                    BigDecimal newQty = checkQuantity(c.quantity(), item);
                    changes.add("%s: %s → %s %s".formatted(item.getItemName(), plain(oldQty), plain(newQty),
                            item.getUnit().getLabel()));
                    item.setQuantity(newQty);
                }
            } else {
                if (c.quantity().signum() == 0) {
                    continue;
                }
                OrderItem added = buildItem(c.servicePriceId(), c.itemName(), c.unit(), c.quantity(), null,
                        pricer, Set.of());
                order.addItem(added);
                changes.add("Thêm " + added.describe());
            }
        }

        for (Iterator<OrderItem> it = order.getItems().iterator(); it.hasNext();) {
            if (it.next().getQuantity().signum() == 0) {
                it.remove();
            }
        }
        return changes;
    }

    private static BigDecimal checkQuantity(BigDecimal quantity, OrderItem item) {
        if (!item.getUnit().accepts(quantity)) {
            throw ApiException.badRequest("Số lượng \"" + item.getItemName() + "\" tính theo "
                    + item.getUnit().getLabel() + " nên phải là số nguyên");
        }
        return quantity.setScale(2, RoundingMode.HALF_UP);
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

    /** Trả về 404 (thay vì 403) khi đơn không thuộc phạm vi, để không lộ việc đơn có tồn tại. */
    private void assertCanAccess(Order order, User user) {
        boolean allowed = switch (user.getRole()) {
            case WORKSHOP_ADMIN, WORKSHOP_ACCOUNTANT -> true;
            case DRIVER -> order.getDriver().getId().equals(user.getId());
            case RESTAURANT_STAFF, RESTAURANT_MANAGER ->
                order.getRestaurant().getId().equals(requireRestaurantId(user));
        };
        if (!allowed) {
            throw ApiException.notFound("Không tìm thấy đơn hàng");
        }
    }

    private Long requireRestaurantId(User user) {
        if (user.getRestaurant() == null) {
            throw ApiException.forbidden("Tài khoản này chưa được liên kết với khách hàng nào");
        }
        return user.getRestaurant().getId();
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

package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.ItemCategory;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderItem;
import com.washpro.washpro_b2b.entity.OrderStatus;
import com.washpro.washpro_b2b.entity.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Điều kiện lọc đơn hàng chạy trong CSDL (thay vì tải toàn bộ rồi lọc trong bộ nhớ).
 * Mỗi hàm trả về một điều kiện độc lập để ghép bằng and().
 */
public final class OrderSpecs {

    /** Việc đang chờ bất kỳ tài xế nào nhận: yêu cầu lấy đồ chưa có tài xế, đơn giặt xong chờ giao. */
    public static final List<OrderStatus> OPEN_FOR_DRIVERS = List.of(OrderStatus.PICKUP_PENDING, OrderStatus.WASHED);

    private OrderSpecs() {
    }

    public static Specification<Order> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("isDeleted"));
    }

    public static Specification<Order> deleted() {
        return (root, query, cb) -> cb.isTrue(root.get("isDeleted"));
    }

    /** Tài xế thấy: đơn mình lấy, đơn mình giao, và việc đang chờ tài xế nhận. */
    public static Specification<Order> visibleToDriver(Long driverId) {
        return (root, query, cb) -> cb.or(
                cb.equal(left(root, "driver").get("id"), driverId),
                cb.equal(left(root, "deliveryDriver").get("id"), driverId),
                root.get("status").in(OPEN_FOR_DRIVERS));
    }

    /** Tài xế từng lấy hoặc giao đơn này (dùng cho đơn đã hủy). */
    public static Specification<Order> involvesDriver(Long driverId) {
        return (root, query, cb) -> cb.or(
                cb.equal(left(root, "driver").get("id"), driverId),
                cb.equal(left(root, "deliveryDriver").get("id"), driverId));
    }

    /** Việc tài xế đang phải làm: đi lấy đồ đã nhận, đơn đang đi giao. */
    public static Specification<Order> assignedTo(Long driverId) {
        return (root, query, cb) -> cb.or(
                cb.and(cb.equal(root.get("status"), OrderStatus.PICKUP_ASSIGNED),
                        cb.equal(left(root, "driver").get("id"), driverId)),
                cb.and(cb.equal(root.get("status"), OrderStatus.DELIVERING),
                        cb.equal(left(root, "deliveryDriver").get("id"), driverId)));
    }

    public static Specification<Order> customer(Long customerId) {
        return (root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId);
    }

    public static Specification<Order> statusIn(Collection<OrderStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    /** Chưa hoàn thành và đã quá hạn trả (khớp Order#isOverdue). */
    public static Specification<Order> overdue(LocalDateTime now) {
        return (root, query, cb) -> cb.and(
                cb.notEqual(root.get("status"), OrderStatus.COMPLETED),
                cb.isNotNull(root.get("dueAt")),
                cb.lessThan(root.get("dueAt"), now));
    }

    /** Tài xế nhận đồ trong khoảng [from, to). */
    public static Specification<Order> receivedBetween(LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("receivedAt"), from),
                cb.lessThan(root.get("receivedAt"), to));
    }

    /** Chưa hoàn thành, chưa quá hạn (hạn trả từ `now`) và hạn trả trước `to`. */
    public static Specification<Order> dueBetween(LocalDateTime now, LocalDateTime to) {
        return (root, query, cb) -> cb.and(
                cb.notEqual(root.get("status"), OrderStatus.COMPLETED),
                cb.greaterThanOrEqualTo(root.get("dueAt"), now),
                cb.lessThan(root.get("dueAt"), to));
    }

    /** Đơn có ít nhất một dòng đồ giặt thuộc nhóm này. */
    public static Specification<Order> hasCategory(ItemCategory category) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            var item = sub.from(OrderItem.class);
            sub.select(item.get("id")).where(
                    cb.equal(item.get("order"), root),
                    cb.equal(item.get("category"), category));
            return cb.exists(sub);
        };
    }

    /** Tìm theo tên khách hàng, tên tài xế (lấy hoặc giao), tên loại đồ, hoặc mã đơn ("#12" / "12"). */
    public static Specification<Order> keyword(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + escapeLike(keyword.toLowerCase()) + "%";
            Join<Order, User> driver = left(root, "driver");
            Join<Order, User> deliveryDriver = left(root, "deliveryDriver");
            Subquery<Long> sub = query.subquery(Long.class);
            var item = sub.from(OrderItem.class);
            sub.select(item.get("id")).where(
                    cb.equal(item.get("order"), root),
                    cb.like(cb.lower(item.get("itemName")), pattern, '\\'));

            var any = cb.or(
                    cb.like(cb.lower(root.get("customer").get("name")), pattern, '\\'),
                    cb.like(cb.lower(driver.get("fullName")), pattern, '\\'),
                    cb.like(cb.lower(driver.get("username")), pattern, '\\'),
                    cb.like(cb.lower(deliveryDriver.get("fullName")), pattern, '\\'),
                    cb.like(cb.lower(deliveryDriver.get("username")), pattern, '\\'),
                    cb.exists(sub));
            Long id = parseId(keyword);
            return id == null ? any : cb.or(any, cb.equal(root.get("id"), id));
        };
    }

    /** LEFT JOIN tới tài xế (đơn có thể chưa có tài xế); dùng lại join đã có trong cùng truy vấn. */
    @SuppressWarnings("unchecked")
    private static Join<Order, User> left(Root<Order> root, String attribute) {
        for (Join<Order, ?> j : root.getJoins()) {
            if (j.getAttribute().getName().equals(attribute) && j.getJoinType() == JoinType.LEFT) {
                return (Join<Order, User>) j;
            }
        }
        return root.join(attribute, JoinType.LEFT);
    }

    private static Long parseId(String keyword) {
        String digits = keyword.startsWith("#") ? keyword.substring(1) : keyword;
        if (digits.isEmpty() || digits.length() > 18 || !digits.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return Long.parseLong(digits);
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

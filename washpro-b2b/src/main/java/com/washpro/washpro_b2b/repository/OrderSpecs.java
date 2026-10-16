package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.ItemCategory;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderItem;
import com.washpro.washpro_b2b.entity.OrderStatus;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Điều kiện lọc đơn hàng chạy trong CSDL (thay vì tải toàn bộ rồi lọc trong bộ nhớ).
 * Mỗi hàm trả về một điều kiện độc lập để ghép bằng and().
 */
public final class OrderSpecs {

    private OrderSpecs() {
    }

    public static Specification<Order> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("isDeleted"));
    }

    public static Specification<Order> driver(Long driverId) {
        return (root, query, cb) -> cb.equal(root.get("driver").get("id"), driverId);
    }

    public static Specification<Order> restaurant(Long restaurantId) {
        return (root, query, cb) -> cb.equal(root.get("restaurant").get("id"), restaurantId);
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

    /** Tạo trong khoảng [from, to). */
    public static Specification<Order> createdBetween(LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                cb.lessThan(root.get("createdAt"), to));
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

    /** Tìm theo tên khách hàng, tên tài xế, tên loại đồ, hoặc mã đơn ("#12" / "12"). */
    public static Specification<Order> keyword(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + escapeLike(keyword.toLowerCase()) + "%";
            var driver = root.join("driver");
            Subquery<Long> sub = query.subquery(Long.class);
            var item = sub.from(OrderItem.class);
            sub.select(item.get("id")).where(
                    cb.equal(item.get("order"), root),
                    cb.like(cb.lower(item.get("itemName")), pattern, '\\'));

            var any = cb.or(
                    cb.like(cb.lower(root.get("restaurant").get("name")), pattern, '\\'),
                    cb.like(cb.lower(driver.get("fullName")), pattern, '\\'),
                    cb.like(cb.lower(driver.get("username")), pattern, '\\'),
                    cb.exists(sub));
            Long id = parseId(keyword);
            return id == null ? any : cb.or(any, cb.equal(root.get("id"), id));
        };
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

package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order>,
        OrderRepositoryCustom {

    /** Nạp đủ khách, tài xế, chi tiết cho một trang đơn (bước 2 của phân trang, sau khi đã có danh sách id). */
    @EntityGraph(attributePaths = { "customer", "driver", "deliveryDriver", "items" })
    List<Order> findByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = { "customer", "driver", "deliveryDriver", "items" })
    List<Order> findByIsDeletedFalseOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "customer", "driver", "deliveryDriver", "items" })
    List<Order> findByDriverIdAndIsDeletedFalseOrderByCreatedAtDesc(Long driverId);

    @EntityGraph(attributePaths = { "customer", "driver", "deliveryDriver", "items" })
    List<Order> findByCustomerIdAndIsDeletedFalseOrderByCreatedAtDesc(Long customerId);

    /**
     * Đơn hoàn thành gộp theo [khách, ngày hoàn thành]: mỗi dòng [customerId, ngày, số đơn, tổng tiền].
     * Đủ để tính công nợ và tuổi nợ mà không phải tải từng đơn. Đơn cũ chưa có completed_at lấy lần cập nhật cuối
     * (khớp Order#getEffectiveCompletedAt); dòng chưa có đơn giá không tính tiền. customerId null = mọi khách.
     */
    @Query("""
            select o.customer.id, cast(coalesce(o.completedAt, o.updatedAt) as LocalDate), count(distinct o.id),
                   sum(i.quantity * i.unitPrice)
            from LaundryOrder o left join o.items i
            where o.status = com.washpro.washpro_b2b.entity.OrderStatus.COMPLETED and o.isDeleted = false
              and (:customerId is null or o.customer.id = :customerId)
            group by o.customer.id, cast(coalesce(o.completedAt, o.updatedAt) as LocalDate)
            """)
    List<Object[]> completedByDay(@Param("customerId") Long customerId);

    /** Tổng tiền các đơn của khách hoàn thành trước mốc `before` (nợ đầu kỳ của sao kê). */
    @Query("""
            select coalesce(sum(i.quantity * i.unitPrice), 0)
            from LaundryOrder o join o.items i
            where o.customer.id = :customerId and o.isDeleted = false
              and o.status = com.washpro.washpro_b2b.entity.OrderStatus.COMPLETED
              and coalesce(o.completedAt, o.updatedAt) < :before
            """)
    BigDecimal billedBefore(@Param("customerId") Long customerId, @Param("before") LocalDateTime before);

    /** Đơn của khách hoàn thành trong khoảng [from, to), kèm chi tiết (dòng sao kê). */
    @EntityGraph(attributePaths = "items")
    @Query("""
            select o from LaundryOrder o
            where o.customer.id = :customerId and o.isDeleted = false
              and o.status = com.washpro.washpro_b2b.entity.OrderStatus.COMPLETED
              and coalesce(o.completedAt, o.updatedAt) >= :from and coalesce(o.completedAt, o.updatedAt) < :to
            """)
    List<Order> findCompletedBetween(@Param("customerId") Long customerId, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Mỗi đơn tài xế nhận đồ trong khoảng [from, to) một dòng chỉ số (báo cáo hiệu suất):
     * [id, customerId, tên khách, driverId, tên tài xế, tên đăng nhập tài xế, lúc nhận đồ, hạn trả, số túi,
     *  lúc bắt đầu giao lần đầu, số lần báo sai lệch (quy trình cũ), số lần báo sự cố nhận, giá trị đơn].
     * driverId null khi khách mang đồ đến xưởng. customerId null = mọi khách.
     */
    @Query("""
            select o.id, c.id, c.name, d.id, d.fullName, d.username, o.receivedAt, o.dueAt, o.totalBags,
                   (select min(h.createdAt) from OrderHistory h
                    where h.order = o and h.toStatus = com.washpro.washpro_b2b.entity.OrderStatus.DELIVERING),
                   (select count(h2) from OrderHistory h2
                    where h2.order = o and h2.toStatus = com.washpro.washpro_b2b.entity.OrderStatus.DISCREPANCY),
                   (select count(h3) from OrderHistory h3
                    where h3.order = o and h3.toStatus = com.washpro.washpro_b2b.entity.OrderStatus.DELIVERY_ISSUE),
                   (select coalesce(sum(i.quantity * i.unitPrice), 0) from OrderItem i where i.order = o)
            from LaundryOrder o join o.customer c left join o.driver d
            where o.isDeleted = false and o.receivedAt >= :from and o.receivedAt < :to
              and (:customerId is null or c.id = :customerId)
            """)
    List<Object[]> performanceRows(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
            @Param("customerId") Long customerId);

    /**
     * Yêu cầu lấy đồ gửi trong khoảng [from, to) (báo cáo hiệu suất), kể cả đã hủy: mỗi dòng
     * [đã hủy, lúc gửi, lúc tài xế nhận đồ (null nếu chưa lấy), driverId (null nếu chưa ai nhận)].
     * Đơn cũ tài xế tạo trực tiếp (không có người yêu cầu) không tính.
     */
    @Query("""
            select o.isDeleted, o.createdAt, o.receivedAt, d.id
            from LaundryOrder o left join o.driver d
            where o.requestedBy is not null and o.createdAt >= :from and o.createdAt < :to
              and (:customerId is null or o.customer.id = :customerId)
            """)
    List<Object[]> pickupPerformanceRows(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
            @Param("customerId") Long customerId);

    /** Lộ trình: việc lấy đồ tài xế đã nhận, hẹn tới ngày `date` (kể cả hẹn trước mà chưa lấy). */
    @EntityGraph(attributePaths = "customer")
    List<Order> findByDriverIdAndStatusAndPreferredDateLessThanEqualAndIsDeletedFalse(Long driverId,
            OrderStatus status, LocalDate date);

    /** Lộ trình: đơn tài xế đang giao. */
    @EntityGraph(attributePaths = "customer")
    List<Order> findByDeliveryDriverIdAndStatusAndIsDeletedFalse(Long driverId, OrderStatus status);

    /** Khóa dòng khi đổi trạng thái để hai người thao tác cùng lúc không làm hỏng luồng. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from LaundryOrder o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = { "customer", "driver", "deliveryDriver", "items" })
    @Query("select o from LaundryOrder o where o.id = :id")
    Optional<Order> findDetailById(@Param("id") Long id);

    /** Mỗi dòng: [năm, tháng, số đơn, tổng tiền] của các đơn hoàn thành kể từ mốc `since`. */
    @Query("""
            select year(h.createdAt), month(h.createdAt), count(distinct o.id), sum(i.quantity * i.unitPrice)
            from OrderHistory h join h.order o left join o.items i
            where h.toStatus = :status and h.createdAt >= :since and o.isDeleted = false
            group by year(h.createdAt), month(h.createdAt)
            """)
    List<Object[]> revenueByMonth(@Param("status") OrderStatus status, @Param("since") LocalDateTime since);

    @Query("""
            select year(h.createdAt), month(h.createdAt), count(distinct o.id), sum(i.quantity * i.unitPrice)
            from OrderHistory h join h.order o left join o.items i
            where h.toStatus = :status and h.createdAt >= :since and o.isDeleted = false
              and o.customer.id = :customerId
            group by year(h.createdAt), month(h.createdAt)
            """)
    List<Object[]> revenueByMonthForCustomer(@Param("status") OrderStatus status,
            @Param("since") LocalDateTime since, @Param("customerId") Long customerId);
}

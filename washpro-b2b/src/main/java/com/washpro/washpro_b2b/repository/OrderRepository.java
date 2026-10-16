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
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order>,
        OrderRepositoryCustom {

    /** Nạp đủ khách, tài xế, chi tiết cho một trang đơn (bước 2 của phân trang, sau khi đã có danh sách id). */
    @EntityGraph(attributePaths = { "customer", "driver", "items" })
    List<Order> findByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = { "customer", "driver", "items" })
    List<Order> findByIsDeletedFalseOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "customer", "driver", "items" })
    List<Order> findByDriverIdAndIsDeletedFalseOrderByCreatedAtDesc(Long driverId);

    @EntityGraph(attributePaths = { "customer", "driver", "items" })
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

    /** Khóa dòng khi đổi trạng thái để hai người thao tác cùng lúc không làm hỏng luồng. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from LaundryOrder o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = { "customer", "driver", "items" })
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

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

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order>,
        OrderRepositoryCustom {

    /** Nạp đủ khách, tài xế, chi tiết cho một trang đơn (bước 2 của phân trang, sau khi đã có danh sách id). */
    @EntityGraph(attributePaths = { "restaurant", "driver", "items" })
    List<Order> findByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = { "restaurant", "driver", "items" })
    List<Order> findByIsDeletedFalseOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "restaurant", "driver", "items" })
    List<Order> findByDriverIdAndIsDeletedFalseOrderByCreatedAtDesc(Long driverId);

    @EntityGraph(attributePaths = { "restaurant", "driver", "items" })
    List<Order> findByRestaurantIdAndIsDeletedFalseOrderByCreatedAtDesc(Long restaurantId);

    /** Đơn hoàn thành (kèm chi tiết để tính tiền): dùng cho tuổi nợ và sao kê. */
    @EntityGraph(attributePaths = { "restaurant", "items" })
    List<Order> findByStatusAndIsDeletedFalse(OrderStatus status);

    @EntityGraph(attributePaths = { "restaurant", "items" })
    List<Order> findByRestaurantIdAndStatusAndIsDeletedFalse(Long restaurantId, OrderStatus status);

    /** Khóa dòng khi đổi trạng thái để hai người thao tác cùng lúc không làm hỏng luồng. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from LaundryOrder o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = { "restaurant", "driver", "items" })
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
              and o.restaurant.id = :restaurantId
            group by year(h.createdAt), month(h.createdAt)
            """)
    List<Object[]> revenueByMonthForRestaurant(@Param("status") OrderStatus status,
            @Param("since") LocalDateTime since, @Param("restaurantId") Long restaurantId);
}

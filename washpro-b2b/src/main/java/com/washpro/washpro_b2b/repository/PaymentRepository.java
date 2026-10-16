package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = "restaurant")
    List<Payment> findAllByOrderByPaidDateDescIdDesc();

    @EntityGraph(attributePaths = "restaurant")
    List<Payment> findByRestaurantIdOrderByPaidDateDescIdDesc(Long restaurantId);

    /** Mỗi dòng: [restaurantId, tổng đã thanh toán]. */
    @Query("select p.restaurant.id, sum(p.amount) from Payment p where p.voided = false group by p.restaurant.id")
    List<Object[]> sumPaidByRestaurant();

    /** Tổng khách đã trả (bỏ khoản đã hủy) trước ngày `before`. */
    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.restaurant.id = :restaurantId and p.voided = false and p.paidDate < :before")
    BigDecimal sumPaidBefore(@Param("restaurantId") Long restaurantId, @Param("before") LocalDate before);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.restaurant.id = :restaurantId and p.voided = false")
    BigDecimal sumPaid(@Param("restaurantId") Long restaurantId);

    /** Khoản thanh toán còn hiệu lực trong khoảng ngày [from, to), cũ trước. */
    @Query("""
            select p from Payment p
            where p.restaurant.id = :restaurantId and p.voided = false and p.paidDate >= :from and p.paidDate < :to
            order by p.paidDate, p.id
            """)
    List<Payment> findActiveBetween(@Param("restaurantId") Long restaurantId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Khóa dòng khi hủy: hai lần hủy cùng lúc không cùng vượt qua bước kiểm tra "đã hủy chưa". */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);
}

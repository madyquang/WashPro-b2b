package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Khóa dòng khi hủy: hai lần hủy cùng lúc không cùng vượt qua bước kiểm tra "đã hủy chưa". */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);
}

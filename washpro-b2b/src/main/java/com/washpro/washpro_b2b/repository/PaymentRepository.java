package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.Payment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = "restaurant")
    List<Payment> findAllByOrderByPaidDateDescIdDesc();

    @EntityGraph(attributePaths = "restaurant")
    List<Payment> findByRestaurantIdOrderByPaidDateDescIdDesc(Long restaurantId);

    /** Mỗi dòng: [restaurantId, tổng đã thanh toán]. */
    @Query("select p.restaurant.id, sum(p.amount) from Payment p where p.voided = false group by p.restaurant.id")
    List<Object[]> sumPaidByRestaurant();
}

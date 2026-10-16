package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findAllByOrderByNameAsc();

    List<Customer> findByActiveTrueOrderByNameAsc();

    Optional<Customer> findByNameIgnoreCase(String name);

    /**
     * Khóa dòng khách hàng khi ghi nhận thanh toán: hai người thu tiền cùng lúc phải lần lượt kiểm tra công nợ,
     * nếu không cả hai cùng thấy còn nợ và tổng tiền thu có thể vượt số nợ.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Customer r where r.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") Long id);
}

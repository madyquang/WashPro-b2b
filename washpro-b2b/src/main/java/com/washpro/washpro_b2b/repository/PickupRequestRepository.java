package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.PickupRequest;
import com.washpro.washpro_b2b.entity.PickupStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PickupRequestRepository extends JpaRepository<PickupRequest, Long> {

    @EntityGraph(attributePaths = { "customer", "driver", "order" })
    List<PickupRequest> findAllByOrderByPreferredDateDescIdDesc();

    @EntityGraph(attributePaths = { "customer", "driver", "order" })
    List<PickupRequest> findByCustomerIdOrderByPreferredDateDescIdDesc(Long customerId);

    /** Việc của tài xế: các yêu cầu chưa ai nhận, cộng các yêu cầu tài xế này đã nhận. */
    @EntityGraph(attributePaths = { "customer", "driver", "order" })
    @Query("""
            select p from PickupRequest p
            where p.status = com.washpro.washpro_b2b.entity.PickupStatus.OPEN
               or p.driver.id = :driverId
            order by p.preferredDate desc, p.id desc
            """)
    List<PickupRequest> findForDriver(@Param("driverId") Long driverId);

    long countByStatusIn(Collection<PickupStatus> statuses);

    long countByCustomerIdAndStatusIn(Long customerId, Collection<PickupStatus> statuses);

    /** Khóa dòng để hai tài xế không cùng nhận một yêu cầu. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PickupRequest p where p.id = :id")
    Optional<PickupRequest> findByIdForUpdate(@Param("id") Long id);
}

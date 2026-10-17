package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserIdIn(Collection<Long> userIds);

    long countByUserId(Long userId);

    @Modifying
    @Query("delete from PushSubscription s where s.endpoint = :endpoint")
    int deleteByEndpoint(@Param("endpoint") String endpoint);

    @Modifying
    @Query("delete from PushSubscription s where s.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}

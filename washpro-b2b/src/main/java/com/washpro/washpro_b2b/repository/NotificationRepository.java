package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.Notification;
import com.washpro.washpro_b2b.entity.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /** Đã gửi bản tin loại này cho người này kể từ mốc `since` chưa (tránh gửi trùng khi máy chủ khởi động lại). */
    boolean existsByUserIdAndTypeAndCreatedAtGreaterThanEqual(Long userId, NotificationType type, LocalDateTime since);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from Notification n where n.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}

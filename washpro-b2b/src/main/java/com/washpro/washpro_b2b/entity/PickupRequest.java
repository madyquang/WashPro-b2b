package com.washpro.washpro_b2b.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Khách hàng yêu cầu xưởng đến lấy đồ. Tài xế nhận việc, đến nơi đếm đồ và tạo đơn từ yêu cầu này;
 * khi đơn được tạo, yêu cầu chuyển sang DONE và gắn với đơn đó.
 */
@Entity
@Table(name = "pickup_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PickupRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    /** Tên đăng nhập của người tạo yêu cầu. */
    @Column(name = "requested_by", nullable = false, length = 50)
    private String requestedBy;

    @Column(name = "preferred_date", nullable = false)
    private LocalDate preferredDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "time_slot", nullable = false, length = 20)
    @Builder.Default
    private TimeSlot timeSlot = TimeSlot.ANYTIME;

    /** Số túi ước tính để tài xế chuẩn bị xe (không bắt buộc). */
    @Column(name = "estimated_bags")
    private Integer estimatedBags;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PickupStatus status = PickupStatus.OPEN;

    /** Tài xế nhận việc (tự nhận hoặc được Quản lý xưởng phân công). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private User driver;

    /** Đơn được tạo từ yêu cầu này. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

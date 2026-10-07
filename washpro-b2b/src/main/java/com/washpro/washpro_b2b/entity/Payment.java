package com.washpro.washpro_b2b.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Một lần khách hàng thanh toán cho xưởng. Công nợ = tổng tiền đơn hoàn thành - tổng thanh toán. */
@Entity
@Table(name = "payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_date", nullable = false)
    private LocalDate paidDate;

    @Column(length = 500)
    private String note;

    /** Tên đăng nhập của người ghi nhận. */
    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    /** Hủy khoản thanh toán ghi nhầm: không xóa dòng, chỉ đánh dấu để sổ sách còn dấu vết. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean voided = false;

    @Column(name = "voided_by", length = 50)
    private String voidedBy;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

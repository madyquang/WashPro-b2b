package com.washpro.washpro_b2b.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Đơn giá hợp đồng riêng của một khách hàng cho một mục trong bảng giá (ghi đè giá chung). */
@Entity
@Table(name = "customer_prices", uniqueConstraints = @UniqueConstraint(
        name = "uk_customer_prices_customer_price", columnNames = { "customer_id", "service_price_id" }))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_price_id", nullable = false)
    private ServicePrice servicePrice;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    /** Tên đăng nhập của người đặt giá gần nhất. */
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}

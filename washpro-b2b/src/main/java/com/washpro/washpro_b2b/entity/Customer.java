package com.washpro.washpro_b2b.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Khách hàng của xưởng (nhà hàng, khách sạn, spa, công ty...).
 * Tên lớp/bảng vẫn là "customer" để tương thích dữ liệu và API cũ; loại khách nằm ở customerType.
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "customer_type", nullable = false, length = 30,
            columnDefinition = "varchar(30) default 'RESTAURANT'")
    @Builder.Default
    private CustomerType customerType = CustomerType.RESTAURANT;

    @Column(length = 255)
    private String address;

    @Column(length = 20)
    private String phone;

    /** Người liên hệ phía khách (quản lý buồng phòng, bếp trưởng...). */
    @Column(name = "contact_name", length = 100)
    private String contactName;

    /** Cam kết trả đồ: số giờ kể từ lúc tài xế nhận đồ (khách sạn thường 24h, nhà hàng 48h). */
    @Column(name = "turnaround_hours", nullable = false, columnDefinition = "int default 48")
    @Builder.Default
    private int turnaroundHours = 48;

    /** Kỳ thanh toán: số ngày được nợ kể từ khi đơn hoàn thành (0 = thanh toán ngay). */
    @Column(name = "payment_term_days", nullable = false, columnDefinition = "int default 0")
    @Builder.Default
    private int paymentTermDays = 0;

    /** false = ngừng hợp tác: không nhận đơn mới nhưng vẫn giữ lịch sử để đối soát. */
    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

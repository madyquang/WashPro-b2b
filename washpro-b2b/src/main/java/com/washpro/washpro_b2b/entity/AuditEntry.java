package com.washpro.washpro_b2b.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Một dòng nhật ký thay đổi. Chỉ thêm, không sửa / xóa. */
@Entity
@Table(name = "audit_log")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Tên đăng nhập người thực hiện. */
    @Column(nullable = false, length = 50)
    private String actor;

    @Column(name = "actor_role", nullable = false, length = 30)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AuditAction.Category category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    /** Mã của đối tượng bị đổi (mục giá, khoản thanh toán, đơn, khách, tài khoản). */
    @Column(name = "entity_id")
    private Long entityId;

    /** Khách hàng liên quan (để lọc theo khách), nếu có. */
    @Column(name = "customer_id")
    private Long customerId;

    @Column(nullable = false, length = 300)
    private String summary;

    /** Chi tiết giá trị cũ → mới, mỗi thay đổi một dòng. */
    @Column(length = 2000)
    private String details;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

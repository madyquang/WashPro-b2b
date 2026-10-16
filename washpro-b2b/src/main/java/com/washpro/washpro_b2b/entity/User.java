package com.washpro.washpro_b2b.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** Luôn lưu dạng BCrypt hash, không bao giờ trả ra API. */
    @Column(nullable = false)
    private String password;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    // Lưu dạng VARCHAR (không dùng kiểu ENUM của MySQL) để đổi / thêm vai trò không phải sửa kiểu cột
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private Role role;

    /** Chỉ có giá trị với tài khoản khách hàng. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    /** false = tài khoản bị khóa, không đăng nhập được. */
    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean enabled = true;

    /** Tăng khi đổi / đặt lại mật khẩu hoặc đăng xuất mọi thiết bị: token mang số cũ không còn dùng được. */
    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private int tokenVersion = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public String getDisplayName() {
        return (fullName != null && !fullName.isBlank()) ? fullName : username;
    }
}

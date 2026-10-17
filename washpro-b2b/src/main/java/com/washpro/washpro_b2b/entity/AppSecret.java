package com.washpro.washpro_b2b.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Khóa / bí mật do hệ thống tự sinh, giữ nguyên qua các lần khởi động (VD khóa VAPID của Web Push). */
@Entity
@Table(name = "app_secrets")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AppSecret {

    @Id
    @Column(length = 50)
    private String name;

    @Column(name = "secret_value", nullable = false, length = 500)
    private String value;
}

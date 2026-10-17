package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.AppSecret;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSecretRepository extends JpaRepository<AppSecret, String> {
}

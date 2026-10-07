package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.ServicePrice;
import com.washpro.washpro_b2b.entity.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServicePriceRepository extends JpaRepository<ServicePrice, Long> {

    List<ServicePrice> findByActiveTrue();

    Optional<ServicePrice> findByNameIgnoreCaseAndServiceType(String name, ServiceType serviceType);
}

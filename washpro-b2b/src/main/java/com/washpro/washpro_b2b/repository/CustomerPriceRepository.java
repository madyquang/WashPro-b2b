package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.CustomerPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerPriceRepository extends JpaRepository<CustomerPrice, Long> {

    List<CustomerPrice> findByRestaurantId(Long restaurantId);

    Optional<CustomerPrice> findByRestaurantIdAndServicePriceId(Long restaurantId, Long servicePriceId);
}

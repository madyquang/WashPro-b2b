package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    List<Restaurant> findAllByOrderByNameAsc();

    List<Restaurant> findByActiveTrueOrderByNameAsc();

    Optional<Restaurant> findByNameIgnoreCase(String name);
}

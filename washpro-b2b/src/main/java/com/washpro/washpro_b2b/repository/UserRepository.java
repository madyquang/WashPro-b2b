package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "restaurant")
    Optional<User> findByUsername(String username);

    @EntityGraph(attributePaths = "restaurant")
    Optional<User> findById(Long id);

    @EntityGraph(attributePaths = "restaurant")
    List<User> findAllByOrderByUsernameAsc();

    boolean existsByRole(Role role);

    long countByRoleAndEnabledTrue(Role role);
}

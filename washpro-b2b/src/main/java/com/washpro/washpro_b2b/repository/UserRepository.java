package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "customer")
    Optional<User> findByUsername(String username);

    @EntityGraph(attributePaths = "customer")
    Optional<User> findById(Long id);

    @EntityGraph(attributePaths = "customer")
    List<User> findAllByOrderByUsernameAsc();

    boolean existsByRole(Role role);

    long countByRoleAndEnabledTrue(Role role);

    /** Người nhận thông báo theo vai trò (chỉ tài khoản đang hoạt động). */
    List<User> findByRoleInAndEnabledTrue(Collection<Role> roles);

    /** Tài khoản đang hoạt động của một khách hàng có vai trò thuộc danh sách. */
    List<User> findByCustomerIdAndRoleInAndEnabledTrue(Long customerId, Collection<Role> roles);
}

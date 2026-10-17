package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.UserDtos.CreateRequest;
import com.washpro.washpro_b2b.dto.UserDtos.Response;
import com.washpro.washpro_b2b.entity.AuditAction;
import com.washpro.washpro_b2b.service.AuditService.Changes;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.repository.CustomerRepository;
import com.washpro.washpro_b2b.repository.UserRepository;
import com.washpro.washpro_b2b.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Quản lý tài khoản (chỉ Quản lý xưởng được gọi). */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<Response> list(Role role) {
        return userRepository.findAllByOrderByUsernameAsc().stream()
                .filter(u -> role == null || u.getRole() == role)
                .map(Response::from)
                .toList();
    }

    @Transactional
    public Response create(CreateRequest request) {
        String username = request.username().trim();
        if (userRepository.findByUsername(username).isPresent()) {
            throw ApiException.conflict("Tên đăng nhập đã tồn tại");
        }

        Customer customer = null;
        if (request.role().isCustomerRole()) {
            if (request.customerId() == null) {
                throw ApiException.badRequest("Vui lòng chọn khách hàng cho tài khoản này");
            }
            customer = customerRepository.findById(request.customerId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
            if (!customer.isActive()) {
                throw ApiException.badRequest("Khách hàng này đã ngừng hợp tác");
            }
        } else if (request.customerId() != null) {
            throw ApiException.badRequest("Chỉ tài khoản khách hàng mới được gắn với một khách hàng");
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(request.password()))
                .fullName(blankToNull(request.fullName()))
                .phone(blankToNull(request.phone()))
                .role(request.role())
                .customer(customer)
                .build();
        User saved = userRepository.save(user);
        auditService.record(AuditAction.USER_CREATED, saved.getId(), customer == null ? null : customer.getId(),
                "Tạo tài khoản " + saved.getUsername() + " (" + roleLabel(saved.getRole()) + ")"
                        + (customer == null ? "" : " cho " + customer.getName()), null);
        return Response.from(saved);
    }

    @Transactional
    public Response setEnabled(Long id, boolean enabled) {
        User target = find(id);
        User me = currentUserService.getCurrentUser();

        if (!enabled) {
            if (target.getId().equals(me.getId())) {
                throw ApiException.badRequest("Bạn không thể tự khóa tài khoản của mình");
            }
            if (target.getRole() == Role.WORKSHOP_ADMIN && target.isEnabled()
                    && userRepository.countByRoleAndEnabledTrue(Role.WORKSHOP_ADMIN) <= 1) {
                throw ApiException.badRequest("Hệ thống phải còn ít nhất một Quản lý xưởng đang hoạt động");
            }
        }
        if (target.isEnabled() != enabled) {
            auditService.record(enabled ? AuditAction.USER_UNLOCKED : AuditAction.USER_LOCKED, target.getId(),
                    target.getCustomer() == null ? null : target.getCustomer().getId(),
                    (enabled ? "Mở khóa tài khoản " : "Khóa tài khoản ") + target.getUsername() + " (" + roleLabel(target.getRole()) + ")",
                    null);
        }
        target.setEnabled(enabled);
        return Response.from(userRepository.save(target));
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User target = find(id);
        target.setPassword(passwordEncoder.encode(newPassword));
        // Ai đang đăng nhập bằng mật khẩu cũ đều bị đăng xuất
        target.setTokenVersion(target.getTokenVersion() + 1);
        userRepository.save(target);
        auditService.record(AuditAction.USER_PASSWORD_RESET, target.getId(),
                target.getCustomer() == null ? null : target.getCustomer().getId(),
                "Đặt lại mật khẩu tài khoản " + target.getUsername() + " (" + roleLabel(target.getRole()) + ")", null);
    }

    private static String roleLabel(Role role) {
        return switch (role) {
            case WORKSHOP_ADMIN -> "Quản lý xưởng";
            case WORKSHOP_ACCOUNTANT -> "Kế toán xưởng";
            case DRIVER -> "Tài xế";
            case CUSTOMER_STAFF -> "Nhân viên khách hàng";
            case CUSTOMER_MANAGER -> "Quản lý khách hàng";
        };
    }

    private User find(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}

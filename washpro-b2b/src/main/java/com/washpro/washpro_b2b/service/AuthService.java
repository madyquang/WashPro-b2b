package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.AuthDtos.AuthResponse;
import com.washpro.washpro_b2b.dto.AuthDtos.ChangePasswordRequest;
import com.washpro.washpro_b2b.dto.AuthDtos.LoginRequest;
import com.washpro.washpro_b2b.dto.UserDtos;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.repository.UserRepository;
import com.washpro.washpro_b2b.security.CurrentUserService;
import com.washpro.washpro_b2b.security.JwtUtils;
import com.washpro.washpro_b2b.security.LoginAttemptService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final LoginAttemptService loginAttemptService;
    private final CurrentUserService currentUserService;

    /** Hash giả dùng để so sánh khi tên đăng nhập không tồn tại, tránh lộ thông tin qua thời gian phản hồi. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtils jwtUtils,
            LoginAttemptService loginAttemptService, CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.loginAttemptService = loginAttemptService;
        this.currentUserService = currentUserService;
        this.dummyHash = passwordEncoder.encode("washpro-dummy-password");
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String username = request.username().trim();
        loginAttemptService.checkNotLocked(username);

        User user = userRepository.findByUsername(username).orElse(null);
        String hash = user != null ? user.getPassword() : dummyHash;
        boolean passwordOk = passwordEncoder.matches(request.password(), hash);

        if (user == null || !passwordOk) {
            loginAttemptService.recordFailure(username);
            throw ApiException.unauthorized("Tên đăng nhập hoặc mật khẩu không chính xác");
        }
        if (!user.isEnabled()) {
            throw ApiException.forbidden("Tài khoản đã bị khóa. Vui lòng liên hệ quản lý xưởng");
        }

        loginAttemptService.reset(username);
        return issue(user);
    }

    private AuthResponse issue(User user) {
        String token = jwtUtils.generateToken(user.getUsername(), user.getRole().name(), user.getTokenVersion());
        return new AuthResponse(
                token,
                user.getUsername(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getRestaurant() != null ? user.getRestaurant().getName() : null,
                System.currentTimeMillis() + jwtUtils.getExpirationMs());
    }

    @Transactional(readOnly = true)
    public UserDtos.Response me() {
        return UserDtos.Response.from(currentUserService.getCurrentUser());
    }

    /** Đổi mật khẩu: mọi phiên đăng nhập khác bị đăng xuất; trả token mới để thiết bị đang dùng vẫn tiếp tục. */
    @Transactional
    public AuthResponse changePassword(ChangePasswordRequest request) {
        User user = currentUserService.getCurrentUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw ApiException.badRequest("Mật khẩu hiện tại không đúng");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw ApiException.badRequest("Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        return issue(userRepository.save(user));
    }

    /** Đăng xuất khỏi mọi thiết bị (kể cả thiết bị đang dùng), VD khi nghi bị lộ mật khẩu hoặc mất điện thoại. */
    @Transactional
    public void logoutEverywhere() {
        User user = currentUserService.getCurrentUser();
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
    }
}

package com.washpro.washpro_b2b.config;

import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Tạo Quản lý xưởng đầu tiên khi hệ thống chưa có ai, thay cho API đăng ký công khai (rất nguy hiểm).
 * Tên đăng nhập và mật khẩu lấy từ biến môi trường BOOTSTRAP_ADMIN_USERNAME / BOOTSTRAP_ADMIN_PASSWORD.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public BootstrapAdminRunner(UserRepository userRepository, PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.username}") String username,
            @Value("${app.bootstrap-admin.password}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.WORKSHOP_ADMIN)) {
            return;
        }
        if (username.isBlank() || password.isBlank()) {
            log.warn("Chưa có Quản lý xưởng nào. Hãy đặt BOOTSTRAP_ADMIN_USERNAME và BOOTSTRAP_ADMIN_PASSWORD "
                    + "trong file .env rồi khởi động lại để tạo tài khoản đầu tiên.");
            return;
        }
        if (password.length() < 8) {
            log.warn("BOOTSTRAP_ADMIN_PASSWORD phải có ít nhất 8 ký tự, chưa tạo tài khoản quản lý xưởng.");
            return;
        }
        userRepository.save(User.builder()
                .username(username.trim())
                .password(passwordEncoder.encode(password))
                .fullName("Quản lý xưởng")
                .role(Role.WORKSHOP_ADMIN)
                .build());
        log.info("Đã tạo tài khoản Quản lý xưởng đầu tiên: {}", username.trim());
    }
}

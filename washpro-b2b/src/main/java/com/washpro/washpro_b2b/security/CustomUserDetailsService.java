package com.washpro.washpro_b2b.security;

import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng"));
        return toDetails(user);
    }

    /** Tài khoản của token, nếu token chưa bị thu hồi (phiên bản khớp) và tài khoản còn tồn tại. */
    @Transactional(readOnly = true)
    public Optional<UserDetails> loadForToken(String username, int tokenVersion) {
        return userRepository.findByUsername(username)
                .filter(user -> user.getTokenVersion() == tokenVersion)
                .map(CustomUserDetailsService::toDetails);
    }

    private static UserDetails toDetails(User user) {
        // Quyền luôn được nạp lại từ CSDL cho mỗi request: đổi vai trò hoặc khóa tài khoản có hiệu lực ngay
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities("ROLE_" + user.getRole().name())
                .disabled(!user.isEnabled())
                .build();
    }
}

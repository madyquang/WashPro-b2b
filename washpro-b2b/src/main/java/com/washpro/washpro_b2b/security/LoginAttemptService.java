package com.washpro.washpro_b2b.security;

import com.washpro.washpro_b2b.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chống dò mật khẩu: nhập sai quá số lần cho phép thì tạm khóa đăng nhập một thời gian.
 * Lưu trong bộ nhớ (đủ cho một máy chủ; nhiều máy chủ nên chuyển sang Redis).
 */
@Service
public class LoginAttemptService {

    private static class Attempt {
        int failures;
        Instant lockedUntil;
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration lockDuration;

    public LoginAttemptService(@Value("${app.security.max-login-attempts}") int maxAttempts,
            @Value("${app.security.lock-minutes}") long lockMinutes) {
        this.maxAttempts = maxAttempts;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
    }

    public void checkNotLocked(String key) {
        Attempt attempt = attempts.get(normalize(key));
        if (attempt == null) {
            return;
        }
        synchronized (attempt) {
            if (attempt.lockedUntil != null) {
                if (Instant.now().isBefore(attempt.lockedUntil)) {
                    long minutes = Math.max(1, Duration.between(Instant.now(), attempt.lockedUntil).toMinutes() + 1);
                    throw ApiException.tooManyRequests(
                            "Đăng nhập sai quá nhiều lần. Vui lòng thử lại sau " + minutes + " phút");
                }
                attempts.remove(normalize(key));
            }
        }
    }

    public void recordFailure(String key) {
        Attempt attempt = attempts.computeIfAbsent(normalize(key), k -> new Attempt());
        synchronized (attempt) {
            attempt.failures++;
            if (attempt.failures >= maxAttempts) {
                attempt.lockedUntil = Instant.now().plus(lockDuration);
                attempt.failures = 0;
            }
        }
    }

    public void reset(String key) {
        attempts.remove(normalize(key));
    }

    private String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }
}

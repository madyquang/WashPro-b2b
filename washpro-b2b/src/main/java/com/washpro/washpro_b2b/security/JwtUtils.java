package com.washpro.washpro_b2b.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtUtils {

    private final Key key;
    private final long expirationMs;

    public JwtUtils(@Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET phải dài tối thiểu 32 ký tự");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = expirationMs;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    /** Nội dung đã kiểm chữ ký của token: tài khoản và phiên bản token lúc phát hành. */
    public record TokenClaims(String username, int version) {
    }

    public String generateToken(String username, String role, int version) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .claim("ver", version)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Trả về tài khoản và phiên bản nếu token hợp lệ (đúng chữ ký, chưa hết hạn), ngược lại trả về rỗng.
     * Token cũ không có phiên bản bị coi là không hợp lệ (phải đăng nhập lại).
     */
    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            Integer version = claims.get("ver", Integer.class);
            if (claims.getSubject() == null || version == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(claims.getSubject(), version));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

package com.washpro.washpro_b2b.integration;

import com.jayway.jsonpath.JsonPath;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.repository.CustomerPriceRepository;
import com.washpro.washpro_b2b.repository.OrderRepository;
import com.washpro.washpro_b2b.repository.PaymentRepository;
import com.washpro.washpro_b2b.repository.PickupRequestRepository;
import com.washpro.washpro_b2b.repository.RestaurantRepository;
import com.washpro.washpro_b2b.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thu hồi token: đổi / đặt lại mật khẩu hoặc "đăng xuất mọi thiết bị" làm mọi token cũ hết hiệu lực ngay.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
class AuthApiIntegrationTest {

    private static final String PASSWORD = "Test@1234";
    private static final String NEW_PASSWORD = "Moi@56789";

    @Autowired
    MockMvc mvc;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    UserRepository userRepository;
    @Autowired
    RestaurantRepository restaurantRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    PickupRequestRepository pickupRepository;
    @Autowired
    CustomerPriceRepository customerPriceRepository;
    @Value("${app.jwt.secret}")
    String jwtSecret;

    User driver;

    @BeforeEach
    void seed() {
        pickupRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        customerPriceRepository.deleteAll();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();

        user("admin", Role.WORKSHOP_ADMIN);
        driver = user("taixe", Role.DRIVER);
    }

    @Test
    void changingPasswordRevokesOtherSessionsButKeepsCurrentOne() throws Exception {
        String phone = login("taixe", PASSWORD);
        String laptop = login("taixe", PASSWORD);

        String body = mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + laptop)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String renewed = JsonPath.read(body, "$.token");

        me(phone).andExpect(status().isUnauthorized());
        me(laptop).andExpect(status().isUnauthorized());
        me(renewed).andExpect(status().isOk());
        me(login("taixe", NEW_PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void adminResetRevokesUserSessions() throws Exception {
        String session = login("taixe", PASSWORD);
        mvc.perform(put("/api/users/" + driver.getId() + "/password")
                .header("Authorization", "Bearer " + login("admin", PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"%s\"}".formatted(NEW_PASSWORD)))
                .andExpect(status().is2xxSuccessful());

        me(session).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEverywhereRevokesAllSessions() throws Exception {
        String a = login("taixe", PASSWORD);
        String b = login("taixe", PASSWORD);
        mvc.perform(post("/api/auth/logout-all").header("Authorization", "Bearer " + a))
                .andExpect(status().isNoContent());

        me(a).andExpect(status().isUnauthorized());
        me(b).andExpect(status().isUnauthorized());
        me(login("taixe", PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void tokenWithoutVersionIsRejected() throws Exception {
        // Token phát hành trước khi có phiên bản (đúng chữ ký, còn hạn) không còn dùng được
        long now = System.currentTimeMillis();
        String legacy = Jwts.builder()
                .setSubject("taixe")
                .claim("role", "DRIVER")
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + 60_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
        me(legacy).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions me(String token) throws Exception {
        return mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token));
    }

    private User user(String username, Role role) {
        return userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(PASSWORD))
                .fullName(username)
                .role(role)
                .build());
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}

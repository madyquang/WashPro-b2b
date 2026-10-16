package com.washpro.washpro_b2b.integration;

import com.jayway.jsonpath.JsonPath;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderItem;
import com.washpro.washpro_b2b.entity.OrderStatus;
import com.washpro.washpro_b2b.entity.Restaurant;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.repository.CustomerPriceRepository;
import com.washpro.washpro_b2b.repository.OrderRepository;
import com.washpro.washpro_b2b.repository.PaymentRepository;
import com.washpro.washpro_b2b.repository.PickupRequestRepository;
import com.washpro.washpro_b2b.repository.RestaurantRepository;
import com.washpro.washpro_b2b.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thanh toán: không thu vượt công nợ, kể cả khi hai người ghi nhận cùng lúc; khoản đã hủy không hủy lại được.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
class PaymentApiIntegrationTest {

    private static final String PASSWORD = "Test@1234";


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

    Restaurant hoaSen;

    @BeforeEach
    void seed() {
        pickupRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        customerPriceRepository.deleteAll();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();

        hoaSen = restaurantRepository.save(Restaurant.builder().name("Nhà hàng Hoa Sen").build());
        User driver = user("taixe", Role.DRIVER);
        user("ketoan", Role.WORKSHOP_ACCOUNTANT);
        user("ketoan2", Role.WORKSHOP_ACCOUNTANT);

        // Một đơn đã hoàn thành trị giá 100.000 đ: công nợ của khách là 100.000 đ
        Order order = Order.builder()
                .driver(driver)
                .restaurant(hoaSen)
                .status(OrderStatus.COMPLETED)
                .totalBags(1)
                .dueAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        order.addItem(OrderItem.builder().itemName("Khăn bàn").quantity(new BigDecimal("10.00")).unitPrice(new BigDecimal("10000.00")).build());
        orderRepository.save(order);
    }

    @Test
    void cannotPayMoreThanBalance() throws Exception {
        mvc.perform(post("/api/payments").header("Authorization", "Bearer " + login("ketoan"))
                .contentType(MediaType.APPLICATION_JSON).content(payment(100001)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentPaymentsCannotExceedBalance() throws Exception {
        // Hai kế toán cùng ghi nhận khách đã trả đủ 100.000 đ: chỉ một khoản được chấp nhận
        List<String> tokens = List.of(login("ketoan"), login("ketoan2"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String token : tokens) {
                Callable<Integer> pay = () -> {
                    start.await();
                    return mvc.perform(post("/api/payments").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(payment(100000)))
                            .andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(pay));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 400);
        } finally {
            pool.shutdownNow();
        }

        mvc.perform(get("/api/reports/debts").header("Authorization", "Bearer " + login("ketoan")))
                .andExpect(jsonPath("$[0].balance", is(0.0)));
    }

    @Test
    void voidedPaymentCannotBeVoidedAgain() throws Exception {
        String token = login("ketoan");
        String body = mvc.perform(post("/api/payments").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(payment(40000)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        String reason = "{\"reason\":\"Ghi nhầm số tiền\"}";
        mvc.perform(patch("/api/payments/" + id + "/void").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(reason))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/payments/" + id + "/void").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(reason))
                .andExpect(status().isBadRequest());

        // Khoản đã hủy không còn trừ vào công nợ
        mvc.perform(get("/api/reports/debts").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].balance", is(100000.0)));
    }

    private String payment(long amount) {
        return "{\"restaurantId\":%d,\"amount\":%d}".formatted(hoaSen.getId(), amount);
    }

    private User user(String username, Role role) {
        return userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(PASSWORD))
                .fullName(username)
                .role(role)
                .build());
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}

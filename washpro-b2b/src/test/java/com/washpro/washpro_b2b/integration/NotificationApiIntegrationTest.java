package com.washpro.washpro_b2b.integration;

import com.jayway.jsonpath.JsonPath;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderItem;
import com.washpro.washpro_b2b.entity.OrderStatus;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.repository.CustomerPriceRepository;
import com.washpro.washpro_b2b.repository.CustomerRepository;
import com.washpro.washpro_b2b.repository.NotificationRepository;
import com.washpro.washpro_b2b.repository.OrderRepository;
import com.washpro.washpro_b2b.repository.PaymentRepository;
import com.washpro.washpro_b2b.repository.PickupRequestRepository;
import com.washpro.washpro_b2b.repository.UserRepository;
import com.washpro.washpro_b2b.service.NotificationDigestJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Thông báo: đúng người nhận theo sự kiện, đọc / đánh dấu đã đọc, bản tin hằng ngày không gửi trùng. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
class NotificationApiIntegrationTest {

    private static final String PASSWORD = "Test@1234";

    @Autowired
    MockMvc mvc;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    UserRepository userRepository;
    @Autowired
    CustomerRepository customerRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    PickupRequestRepository pickupRepository;
    @Autowired
    CustomerPriceRepository customerPriceRepository;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    NotificationDigestJob digestJob;

    Customer hoaSen;
    User driver;

    @BeforeEach
    void seed() {
        notificationRepository.deleteAll();
        pickupRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        customerPriceRepository.deleteAll();
        userRepository.deleteAll();
        customerRepository.deleteAll();

        hoaSen = customerRepository.save(Customer.builder().name("Nhà hàng Hoa Sen").build());
        Customer bienXanh = customerRepository.save(Customer.builder().name("Khách sạn Biển Xanh").build());
        user("admin", Role.WORKSHOP_ADMIN, null, true);
        user("ketoan", Role.WORKSHOP_ACCOUNTANT, null, true);
        driver = user("taixe1", Role.DRIVER, null, true);
        user("taixe2", Role.DRIVER, null, true);
        user("taixe.nghi", Role.DRIVER, null, false);
        user("hoasen.nv", Role.CUSTOMER_STAFF, hoaSen, true);
        user("hoasen.ql", Role.CUSTOMER_MANAGER, hoaSen, true);
        user("bienxanh.nv", Role.CUSTOMER_STAFF, bienXanh, true);
    }

    @Test
    void pickupAndOrderEventsReachTheRightPeople() throws Exception {
        // Khách gửi yêu cầu lấy đồ: quản lý xưởng và mọi tài xế đang làm việc được báo; người gửi thì không
        String body = mvc.perform(post("/api/pickups").header("Authorization", "Bearer " + login("hoasen.nv"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredDate\":\"%s\",\"estimatedBags\":3}".formatted(LocalDate.now())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long pickupId = ((Number) JsonPath.read(body, "$.id")).longValue();

        mine("admin").andExpect(jsonPath("$.unread", is(1)))
                .andExpect(jsonPath("$.items[0].type", is("PICKUP_NEW")))
                .andExpect(jsonPath("$.items[0].title", is("Yêu cầu lấy đồ mới: Nhà hàng Hoa Sen")))
                .andExpect(jsonPath("$.items[0].link", is("/pickups")));
        mine("taixe2").andExpect(jsonPath("$.unread", is(1)));
        mine("hoasen.nv").andExpect(jsonPath("$.unread", is(0)));
        mine("ketoan").andExpect(jsonPath("$.unread", is(0)));
        mine("bienxanh.nv").andExpect(jsonPath("$.unread", is(0)));

        // Tài xế nhận việc: mọi tài khoản của khách được báo
        mvc.perform(put("/api/pickups/" + pickupId + "/accept").header("Authorization", "Bearer " + login("taixe1")))
                .andExpect(status().isOk());
        mine("hoasen.ql").andExpect(jsonPath("$.items[0].type", is("PICKUP_ACCEPTED")));

        // Tài xế tạo đơn: khách được báo xác nhận số lượng, kèm đường dẫn mở đơn
        String order = mvc.perform(post("/api/orders").header("Authorization", "Bearer " + login("taixe1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":%d,\"totalBags\":3,\"pickupRequestId\":%d,\"items\":[{\"itemName\":\"Khăn bàn\",\"quantity\":10}]}"
                        .formatted(hoaSen.getId(), pickupId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = ((Number) JsonPath.read(order, "$.id")).longValue();
        mine("hoasen.nv").andExpect(jsonPath("$.items[0].type", is("ORDER_TO_CONFIRM")))
                .andExpect(jsonPath("$.items[0].link", is("/orders?status=PENDING_CONFIRM&open=" + orderId)));
    }

    @Test
    void readAndReadAll() throws Exception {
        mvc.perform(post("/api/pickups").header("Authorization", "Bearer " + login("hoasen.nv"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredDate\":\"%s\"}".formatted(LocalDate.now())))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pickups").header("Authorization", "Bearer " + login("hoasen.ql"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredDate\":\"%s\"}".formatted(LocalDate.now())))
                .andExpect(status().isCreated());

        String admin = login("admin");
        String body = mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.unread", is(2)))
                .andReturn().getResponse().getContentAsString();
        long first = ((Number) JsonPath.read(body, "$.items[0].id")).longValue();

        // Người khác không đánh dấu được thông báo không phải của mình
        mvc.perform(post("/api/notifications/" + first + "/read").header("Authorization", "Bearer " + login("taixe1")))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/notifications/" + first + "/read").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.unread", is(1)));
        mvc.perform(post("/api/notifications/read-all").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mine("admin").andExpect(jsonPath("$.unread", is(0)))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].read", is(true)));
        // Tài xế vẫn còn thông báo chưa đọc của mình
        mine("taixe1").andExpect(jsonPath("$.unread", is(2)));
    }

    @Test
    void dailyDigestIsSentOncePerDay() throws Exception {
        // Một đơn trễ hạn trả và một khoản nợ quá hạn của Hoa Sen
        orderRepository.save(order(OrderStatus.WASHING, LocalDateTime.now().minusHours(2), null));
        orderRepository.save(order(OrderStatus.COMPLETED, null, LocalDateTime.now().minusDays(20)));

        digestJob.run();
        digestJob.run();

        mine("admin").andExpect(jsonPath("$.unread", is(2)))
                .andExpect(jsonPath("$.items[?(@.type == 'OVERDUE_ORDERS')].title", org.hamcrest.Matchers.contains("1 đơn đã quá hạn trả đồ")));
        mine("ketoan").andExpect(jsonPath("$.unread", is(1)))
                .andExpect(jsonPath("$.items[0].title", startsWith("1 khách có nợ quá hạn, tổng 100.000 đ")));
        mine("hoasen.ql").andExpect(jsonPath("$.unread", is(1)))
                .andExpect(jsonPath("$.items[0].title", is("Công nợ quá hạn: 100.000 đ")));
        mine("hoasen.nv").andExpect(jsonPath("$.unread", is(0)));
    }

    // ===================== Hàm hỗ trợ =====================

    private ResultActions mine(String username) throws Exception {
        return mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + login(username)))
                .andExpect(status().isOk());
    }

    private Order order(OrderStatus status, LocalDateTime dueAt, LocalDateTime completedAt) {
        Order order = Order.builder().driver(driver).customer(hoaSen).status(status).totalBags(1).dueAt(dueAt)
                .completedAt(completedAt).build();
        order.addItem(OrderItem.builder().itemName("Khăn bàn").quantity(new BigDecimal("10"))
                .unitPrice(new BigDecimal("10000")).build());
        return order;
    }

    private User user(String username, Role role, Customer customer, boolean enabled) {
        return userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(PASSWORD))
                .fullName(username)
                .role(role)
                .customer(customer)
                .enabled(enabled)
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

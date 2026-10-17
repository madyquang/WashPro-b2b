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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Báo cáo hiệu suất: đúng hạn, sai lệch, sự cố, đáp ứng lấy đồ; theo khách / tài xế; phạm vi theo vai trò. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
class PerformanceApiIntegrationTest {

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
    CustomerPriceRepository customerPriceRepository;
    @Autowired
    NotificationRepository notificationRepository;

    @BeforeEach
    void seed() {
        notificationRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        customerPriceRepository.deleteAll();
        userRepository.deleteAll();
        customerRepository.deleteAll();

        Customer hoaSen = customerRepository.save(Customer.builder().name("Nhà hàng Hoa Sen").build());
        Customer bienXanh = customerRepository.save(Customer.builder().name("Khách sạn Biển Xanh").build());
        user("admin", Role.WORKSHOP_ADMIN, null);
        user("ketoan", Role.WORKSHOP_ACCOUNTANT, null);
        user("hoasen.ql", Role.CUSTOMER_MANAGER, hoaSen);
        User tai = user("taixe.tai", Role.DRIVER, null);
        User hung = user("taixe.hung", Role.DRIVER, null);

        LocalDateTime now = LocalDateTime.now();
        // Hoa Sen, tài xế Tài: giao đúng hạn, 100.000 đ, 2 túi
        Order onTime = order(hoaSen, tai, 2, now.plusDays(1), 100000);
        onTime.setRequestedBy("hoasen.ql");
        onTime.addHistory(OrderStatus.WASHING, OrderStatus.DELIVERING, "admin", null);
        onTime = orderRepository.save(onTime);
        // Hoa Sen, tài xế Tài: có sai lệch, giao trễ hạn, khách báo sự cố; 50.000 đ, 1 túi
        Order late = order(hoaSen, tai, 1, now.minusHours(1), 50000);
        late.addHistory(OrderStatus.PENDING_CONFIRM, OrderStatus.DISCREPANCY, "hoasen.ql", null);
        late.addHistory(OrderStatus.WASHING, OrderStatus.DELIVERING, "admin", null);
        late.addHistory(OrderStatus.DELIVERING, OrderStatus.DELIVERY_ISSUE, "hoasen.ql", null);
        orderRepository.save(late);
        // Biển Xanh, tài xế Hùng: chưa giao, 3 túi, chưa có giá
        orderRepository.save(order(bienXanh, hung, 3, now.plusDays(2), 0));
        // Đơn đã hủy không tính
        Order cancelled = order(bienXanh, hung, 9, now.plusDays(2), 999000);
        cancelled.setDeleted(true);
        orderRepository.save(cancelled);

        // Yêu cầu lấy đồ: một đã lấy (đơn đúng hạn ở trên), một bị hủy, một đang chờ
        orderRepository.save(Order.builder().customer(hoaSen).requestedBy("hoasen.ql").preferredDate(LocalDate.now())
                .status(OrderStatus.PICKUP_PENDING).isDeleted(true).build());
        orderRepository.save(Order.builder().customer(bienXanh).requestedBy("admin").preferredDate(LocalDate.now())
                .status(OrderStatus.PICKUP_PENDING).build());
    }

    @Test
    void workshopSeesWholeReport() throws Exception {
        report("ketoan", "")
                .andExpect(jsonPath("$.summary.orders", is(3)))
                .andExpect(jsonPath("$.summary.bags", is(6)))
                .andExpect(jsonPath("$.summary.orderValue", is(150000.0)))
                .andExpect(jsonPath("$.summary.delivered", is(2)))
                .andExpect(jsonPath("$.summary.onTime", is(1)))
                .andExpect(jsonPath("$.summary.onTimeRate", is(0.5)))
                .andExpect(jsonPath("$.summary.discrepancyOrders", is(1)))
                .andExpect(jsonPath("$.summary.discrepancyRate", is(0.333)))
                .andExpect(jsonPath("$.summary.issueOrders", is(1)))
                .andExpect(jsonPath("$.summary.issueRate", is(0.5)))
                .andExpect(jsonPath("$.summary.pickupsRequested", is(3)))
                .andExpect(jsonPath("$.summary.pickupsDone", is(1)))
                .andExpect(jsonPath("$.summary.pickupsCancelled", is(1)))
                // Theo khách: nhiều đơn trước; khách chưa có đơn giao thì tỉ lệ đúng hạn để trống
                .andExpect(jsonPath("$.customers", hasSize(2)))
                .andExpect(jsonPath("$.customers[0].customerName", is("Nhà hàng Hoa Sen")))
                .andExpect(jsonPath("$.customers[0].onTimeRate", is(0.5)))
                .andExpect(jsonPath("$.customers[1].onTimeRate", nullValue()))
                // Theo tài xế
                .andExpect(jsonPath("$.drivers", hasSize(2)))
                .andExpect(jsonPath("$.drivers[0].driverName", is("taixe.tai")))
                .andExpect(jsonPath("$.drivers[0].orders", is(2)))
                .andExpect(jsonPath("$.drivers[0].pickupsDone", is(1)))
                .andExpect(jsonPath("$.drivers[1].pickupsDone", is(0)));
    }

    @Test
    void customerManagerSeesOnlyOwnFigures() throws Exception {
        report("hoasen.ql", "")
                .andExpect(jsonPath("$.summary.orders", is(2)))
                .andExpect(jsonPath("$.summary.pickupsRequested", is(2)))
                .andExpect(jsonPath("$.customers", hasSize(1)))
                .andExpect(jsonPath("$.drivers", nullValue()));
    }

    @Test
    void periodAndAccessAreChecked() throws Exception {
        // Khoảng ngày không chứa đơn nào
        report("admin", "?from=2020-01-01&to=2020-01-31")
                .andExpect(jsonPath("$.summary.orders", is(0)))
                .andExpect(jsonPath("$.summary.onTimeRate", nullValue()));
        mvc.perform(get("/api/reports/performance?from=2026-02-01&to=2026-01-01")
                .header("Authorization", "Bearer " + login("admin")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/performance").header("Authorization", "Bearer " + login("taixe.tai")))
                .andExpect(status().isForbidden());
    }

    private ResultActions report(String username, String query) throws Exception {
        return mvc.perform(get("/api/reports/performance" + query).header("Authorization", "Bearer " + login(username)))
                .andExpect(status().isOk());
    }

    private Order order(Customer customer, User driver, int bags, LocalDateTime dueAt, long amount) {
        Order order = Order.builder().driver(driver).customer(customer).status(OrderStatus.WASHING).totalBags(bags)
                .receivedAt(LocalDateTime.now().minusHours(2)).dueAt(dueAt).build();
        order.addItem(OrderItem.builder().itemName("Khăn bàn").quantity(new BigDecimal("10"))
                .unitPrice(amount == 0 ? null : BigDecimal.valueOf(amount / 10)).build());
        return order;
    }

    private User user(String username, Role role, Customer customer) {
        return userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .customer(customer)
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

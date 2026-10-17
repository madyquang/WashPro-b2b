package com.washpro.washpro_b2b.integration;

import com.jayway.jsonpath.JsonPath;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderItem;
import com.washpro.washpro_b2b.entity.OrderStatus;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.repository.AuditEntryRepository;
import com.washpro.washpro_b2b.repository.CustomerPriceRepository;
import com.washpro.washpro_b2b.repository.CustomerRepository;
import com.washpro.washpro_b2b.repository.NotificationRepository;
import com.washpro.washpro_b2b.repository.OrderRepository;
import com.washpro.washpro_b2b.repository.PaymentRepository;
import com.washpro.washpro_b2b.repository.PickupRequestRepository;
import com.washpro.washpro_b2b.repository.ServicePriceRepository;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nhật ký thay đổi: ghi đúng người, giá trị cũ → mới; thao tác lỗi không để lại nhật ký; quyền xem; lọc. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
class AuditApiIntegrationTest {

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
    ServicePriceRepository servicePriceRepository;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    AuditEntryRepository auditRepository;

    Customer hoaSen;
    long orderId;

    @BeforeEach
    void seed() {
        auditRepository.deleteAll();
        notificationRepository.deleteAll();
        pickupRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        customerPriceRepository.deleteAll();
        servicePriceRepository.deleteAll();
        userRepository.deleteAll();
        customerRepository.deleteAll();

        hoaSen = customerRepository.save(Customer.builder().name("Nhà hàng Hoa Sen").address("1 Lê Lợi").build());
        user("admin", Role.WORKSHOP_ADMIN, null);
        user("ketoan", Role.WORKSHOP_ACCOUNTANT, null);
        User driver = user("taixe", Role.DRIVER, null);
        user("hoasen.ql", Role.CUSTOMER_MANAGER, hoaSen);

        Order order = Order.builder().driver(driver).customer(hoaSen).status(OrderStatus.WASHING).totalBags(1)
                .dueAt(LocalDateTime.of(2026, 10, 20, 9, 0)).build();
        order.addItem(OrderItem.builder().itemName("Khăn bàn").quantity(new BigDecimal("10"))
                .unitPrice(new BigDecimal("5000")).build());
        orderId = orderRepository.save(order).getId();
        Order done = Order.builder().driver(driver).customer(hoaSen).status(OrderStatus.COMPLETED).totalBags(1)
                .completedAt(LocalDateTime.now()).build();
        done.addItem(OrderItem.builder().itemName("Khăn bàn").quantity(new BigDecimal("20"))
                .unitPrice(new BigDecimal("5000")).build());
        orderRepository.save(done);
    }

    @Test
    void moneyAndOrderChangesAreLoggedWithBeforeAndAfter() throws Exception {
        String ketoan = login("ketoan");
        // Sửa đơn giá trên đơn
        mvc.perform(put("/api/orders/" + orderId + "/items").header("Authorization", "Bearer " + ketoan)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"itemName\":\"Khăn bàn\",\"quantity\":10,\"unitPrice\":6000}]}"))
                .andExpect(status().isOk());
        // Thu tiền rồi hủy
        String pay = mvc.perform(post("/api/payments").header("Authorization", "Bearer " + ketoan)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":%d,\"amount\":30000,\"note\":\"CK Vietcombank\"}".formatted(hoaSen.getId())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long paymentId = ((Number) JsonPath.read(pay, "$.id")).longValue();
        mvc.perform(patch("/api/payments/" + paymentId + "/void").header("Authorization", "Bearer " + ketoan)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Ghi nhầm khách\"}"))
                .andExpect(status().isOk());
        // Quản lý xưởng đổi hạn trả và hủy đơn
        String admin = login("admin");
        mvc.perform(put("/api/orders/" + orderId + "/due").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"dueAt\":\"2026-10-22T17:00:00\",\"note\":\"Khách xin lùi\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/orders/" + orderId).param("reason", "Khách hủy").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        audit("ketoan", "")
                .andExpect(jsonPath("$.total", is(5)))
                // Mới nhất trước
                .andExpect(jsonPath("$.content[0].action", is("ORDER_CANCELLED")))
                .andExpect(jsonPath("$.content[0].actor", is("admin")))
                .andExpect(jsonPath("$.content[0].details", containsString("Lý do: Khách hủy")))
                .andExpect(jsonPath("$.content[1].action", is("ORDER_DUE_CHANGED")))
                .andExpect(jsonPath("$.content[1].details", containsString("Hạn trả: 20/10/2026 09:00 → 22/10/2026 17:00")))
                .andExpect(jsonPath("$.content[2].action", is("PAYMENT_VOIDED")))
                .andExpect(jsonPath("$.content[2].summary", is("Hủy khoản thu 30.000 đ của Nhà hàng Hoa Sen")))
                .andExpect(jsonPath("$.content[3].action", is("PAYMENT_RECORDED")))
                .andExpect(jsonPath("$.content[3].actor", is("ketoan")))
                .andExpect(jsonPath("$.content[3].actorRole", is("WORKSHOP_ACCOUNTANT")))
                .andExpect(jsonPath("$.content[4].action", is("ORDER_ITEMS_UPDATED")))
                .andExpect(jsonPath("$.content[4].customerId", is(hoaSen.getId().intValue())))
                .andExpect(jsonPath("$.content[4].details", containsString("Tổng tiền: 50.000 đ → 60.000 đ")))
                .andExpect(jsonPath("$.content[4].details", containsString("Trước: Khăn bàn 10 cái × 5.000 đ")));

        // Lọc theo nhóm, từ khóa, khách
        audit("admin", "?category=PAYMENT").andExpect(jsonPath("$.total", is(2)));
        audit("admin", "?q=vietcombank").andExpect(jsonPath("$.total", is(1)));
        audit("admin", "?customerId=" + hoaSen.getId() + "&size=2")
                .andExpect(jsonPath("$.total", is(5))).andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void priceCustomerAndAccountChangesAreLogged() throws Exception {
        String admin = login("admin");
        String created = mvc.perform(post("/api/prices").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Khăn tắm\",\"category\":\"TOWEL\",\"serviceType\":\"WASH\",\"unit\":\"PIECE\",\"unitPrice\":4000}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long priceId = ((Number) JsonPath.read(created, "$.id")).longValue();
        mvc.perform(put("/api/prices/" + priceId).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Khăn tắm\",\"category\":\"TOWEL\",\"serviceType\":\"WASH\",\"unit\":\"PIECE\",\"unitPrice\":4500}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/prices/customers/" + hoaSen.getId() + "/" + priceId).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"unitPrice\":4200}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/customers/" + hoaSen.getId()).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nhà hàng Hoa Sen\",\"address\":\"1 Lê Lợi\",\"paymentTermDays\":30}"))
                .andExpect(status().isOk());
        Long userId = userRepository.findByUsername("hoasen.ql").orElseThrow().getId();
        mvc.perform(patch("/api/users/" + userId + "/enabled").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        audit("admin", "?category=PRICE")
                .andExpect(jsonPath("$.total", is(3)))
                .andExpect(jsonPath("$.content[0].details", is("Đơn giá: giá chung 4.500 đ → 4.200 đ")))
                .andExpect(jsonPath("$.content[1].details", is("Đơn giá chung: 4.000 đ → 4.500 đ")));
        audit("admin", "?category=CUSTOMER")
                .andExpect(jsonPath("$.content[0].details", is("Kỳ thanh toán (ngày): 0 → 30")));
        audit("admin", "?category=ACCOUNT")
                .andExpect(jsonPath("$.content[0].summary", is("Khóa tài khoản hoasen.ql (Quản lý khách hàng)")));
    }

    @Test
    void failedChangesLeaveNoLogAndOnlyWorkshopCanRead() throws Exception {
        // Thu vượt công nợ bị từ chối: không có nhật ký
        mvc.perform(post("/api/payments").header("Authorization", "Bearer " + login("ketoan"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":%d,\"amount\":99999999}".formatted(hoaSen.getId())))
                .andExpect(status().isBadRequest());
        assertThat(auditRepository.count()).isZero();

        mvc.perform(get("/api/audit").header("Authorization", "Bearer " + login("hoasen.ql"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/audit").header("Authorization", "Bearer " + login("taixe"))).andExpect(status().isForbidden());
    }

    private ResultActions audit(String username, String query) throws Exception {
        return mvc.perform(get("/api/audit" + query).header("Authorization", "Bearer " + login(username)))
                .andExpect(status().isOk());
    }

    private User user(String username, Role role, Customer customer) {
        return userRepository.save(User.builder().username(username).password(passwordEncoder.encode(PASSWORD))
                .fullName(username).role(role).customer(customer).build());
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}

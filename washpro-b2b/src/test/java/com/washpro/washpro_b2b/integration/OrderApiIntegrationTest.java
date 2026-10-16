package com.washpro.washpro_b2b.integration;

import com.jayway.jsonpath.JsonPath;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.repository.CustomerPriceRepository;
import com.washpro.washpro_b2b.repository.OrderRepository;
import com.washpro.washpro_b2b.repository.PaymentRepository;
import com.washpro.washpro_b2b.repository.PickupRequestRepository;
import com.washpro.washpro_b2b.repository.CustomerRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test tích hợp: gọi API thật qua Spring Security (JWT) xuống CSDL H2.
 * Không dùng @Transactional ở lớp test để phát hiện được lỗi nạp lười (open-in-view đang tắt) như khi chạy thật.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
class OrderApiIntegrationTest {

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

    Customer hoaSen;
    Customer bienXanh;

    @BeforeEach
    void seed() {
        pickupRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        customerPriceRepository.deleteAll();
        userRepository.deleteAll();
        customerRepository.deleteAll();

        hoaSen = customerRepository.save(Customer.builder().name("Nhà hàng Hoa Sen").turnaroundHours(24).build());
        bienXanh = customerRepository.save(Customer.builder().name("Khách sạn Biển Xanh").build());

        user("admin", Role.WORKSHOP_ADMIN, null);
        user("ketoan", Role.WORKSHOP_ACCOUNTANT, null);
        user("taixe1", Role.DRIVER, null);
        user("taixe2", Role.DRIVER, null);
        user("hoasen.nv", Role.CUSTOMER_STAFF, hoaSen);
        user("hoasen.ql", Role.CUSTOMER_MANAGER, hoaSen);
        user("bienxanh.nv", Role.CUSTOMER_STAFF, bienXanh);
    }

    @Test
    void fullOrderLifecycleEndsInDebt() throws Exception {
        String driver = login("taixe1");
        long id = createOrder(driver, hoaSen, "Khăn bàn", 10);

        mvc.perform(auth(put("/api/orders/" + id + "/approve"), login("hoasen.nv")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("WASHING")));

        String admin = login("admin");
        // Chưa có đơn giá thì không được giao
        mvc.perform(auth(put("/api/orders/" + id + "/status"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DELIVERING\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(auth(put("/api/orders/" + id + "/items"), login("ketoan")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"itemName\":\"Khăn bàn\",\"quantity\":10,\"unitPrice\":5000}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billable", is(true)))
                .andExpect(jsonPath("$.totalAmount", is(50000.0)));

        mvc.perform(auth(put("/api/orders/" + id + "/status"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DELIVERING\"}"))
                .andExpect(status().isOk());

        mvc.perform(auth(put("/api/orders/" + id + "/confirm-receipt"), login("hoasen.nv")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.history", hasSize(5)));

        // Kỳ thanh toán 0 ngày: toàn bộ tiền đơn vào công nợ của khách
        mvc.perform(auth(get("/api/reports/debts"), login("hoasen.ql")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].balance", is(50000.0)));
    }

    @Test
    void ordersAreScopedToOwnerAndHiddenOtherwise() throws Exception {
        long id = createOrder(login("taixe1"), hoaSen, "Ga giường", 5);

        // Ngoài phạm vi trả 404 (không lộ việc đơn có tồn tại)
        mvc.perform(auth(get("/api/orders/" + id), login("taixe2"))).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/orders/" + id), login("bienxanh.nv"))).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/orders"), login("bienxanh.nv")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(0)));

        mvc.perform(auth(get("/api/orders/" + id), login("hoasen.nv"))).andExpect(status().isOk());
        // Khách khác không xác nhận hộ được
        mvc.perform(auth(put("/api/orders/" + id + "/approve"), login("bienxanh.nv"))).andExpect(status().isNotFound());
    }

    @Test
    void rolesAreEnforced() throws Exception {
        long id = createOrder(login("taixe1"), hoaSen, "Khăn tắm", 3);

        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mvc.perform(auth(put("/api/orders/" + id + "/status"), login("taixe1")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DELIVERING\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/orders"), login("hoasen.nv")).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(hoaSen, "Khăn tắm", 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listIsFilteredAndPagedOnServer() throws Exception {
        String driver = login("taixe1");
        long first = createOrder(driver, hoaSen, "Khăn bàn", 10);
        createOrder(driver, hoaSen, "Ga giường", 4);
        createOrder(driver, bienXanh, "Khăn bàn", 7);
        mvc.perform(auth(put("/api/orders/" + first + "/approve"), login("hoasen.nv"))).andExpect(status().isOk());

        String admin = login("admin");
        mvc.perform(auth(get("/api/orders").param("size", "2"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(3)))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.counts.PENDING_CONFIRM", is(2)))
                .andExpect(jsonPath("$.counts.WASHING", is(1)))
                .andExpect(jsonPath("$.counts.COMPLETED", is(0)));

        mvc.perform(auth(get("/api/orders").param("size", "2").param("page", "1"), admin))
                .andExpect(jsonPath("$.content", hasSize(1)));

        // Mặc định mới nhận trước; DUE: hạn trả gần nhất trước (Hoa Sen trả trong 24h, Biển Xanh 48h)
        mvc.perform(auth(get("/api/orders"), admin))
                .andExpect(jsonPath("$.content[0].customerName", is("Khách sạn Biển Xanh")));
        mvc.perform(auth(get("/api/orders").param("sort", "DUE"), admin))
                .andExpect(jsonPath("$.content[0].id", is((int) first)))
                .andExpect(jsonPath("$.content[2].customerName", is("Khách sạn Biển Xanh")));

        mvc.perform(auth(get("/api/orders").param("status", "PENDING_CONFIRM,WASHING").param("q", "khăn BÀN"), admin))
                .andExpect(jsonPath("$.total", is(2)));

        mvc.perform(auth(get("/api/orders").param("q", "biển xanh"), admin))
                .andExpect(jsonPath("$.total", is(1)))
                .andExpect(jsonPath("$.content[0].customerName", is("Khách sạn Biển Xanh")));

        mvc.perform(auth(get("/api/orders").param("q", "#" + first), admin))
                .andExpect(jsonPath("$.total", is(1)))
                .andExpect(jsonPath("$.content[0].id", is((int) first)));

        // Ký tự đặc biệt của LIKE được hiểu theo nghĩa đen
        mvc.perform(auth(get("/api/orders").param("q", "%"), admin))
                .andExpect(jsonPath("$.total", is(0)));

        mvc.perform(auth(get("/api/orders").param("category", "OTHER").param("overdue", "true"), admin))
                .andExpect(jsonPath("$.total", is(0)))
                .andExpect(jsonPath("$.overdueCount", is(0)));

        // Tài khoản khách chỉ đếm đơn của mình
        mvc.perform(auth(get("/api/orders"), login("hoasen.nv")))
                .andExpect(jsonPath("$.total", is(2)));

        mvc.perform(auth(get("/api/orders").param("size", "1000"), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Cỡ trang tối đa là 100")));
    }

    // ===================== Hàm hỗ trợ =====================

    private void user(String username, Role role, Customer customer) {
        userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(PASSWORD))
                .fullName(username)
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

    private long createOrder(String token, Customer customer, String itemName, int quantity) throws Exception {
        String body = mvc.perform(auth(post("/api/orders"), token).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(customer, itemName, quantity)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING_CONFIRM")))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String orderJson(Customer customer, String itemName, int quantity) {
        return "{\"customerId\":%d,\"totalBags\":1,\"items\":[{\"itemName\":\"%s\",\"quantity\":%d}]}"
                .formatted(customer.getId(), itemName, quantity);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}

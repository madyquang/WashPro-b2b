package com.washpro.washpro_b2b.integration;

import com.jayway.jsonpath.JsonPath;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderItem;
import com.washpro.washpro_b2b.entity.OrderStatus;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.TimeSlot;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lộ trình tài xế: đúng điểm dừng (đã nhận, quá hẹn, đơn đang giao), gom theo khu vực, phạm vi theo vai trò. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
class RouteApiIntegrationTest {

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

    User tai;

    @BeforeEach
    void seed() {
        notificationRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        customerPriceRepository.deleteAll();
        userRepository.deleteAll();
        customerRepository.deleteAll();

        Customer hoaSen = customer("Khách sạn Hoa Sen", "25 Nguyễn Huệ, Q.1, TP.HCM");
        Customer pho = customer("Nhà hàng Phố Cổ", "15 Hàng Bè, Hoàn Kiếm, Hà Nội");
        Customer an = customer("Spa An Nhiên", "3 Lê Lai, Q.1, TP.HCM");
        Customer bien = customer("Nhà hàng Biển Xanh", "88 Võ Văn Kiệt, Q.5, TP.HCM");
        Customer vague = customer("Quán Bảo Khánh", "Bảo Khánh");
        user("admin", Role.WORKSHOP_ADMIN, null);
        user("hoasen.nv", Role.CUSTOMER_STAFF, hoaSen);
        tai = user("taixe.tai", Role.DRIVER, null);
        User hung = user("taixe.hung", Role.DRIVER, null);

        LocalDate today = LocalDate.now();
        pickup(hoaSen, tai, today, TimeSlot.AFTERNOON, OrderStatus.PICKUP_ASSIGNED);
        pickup(pho, tai, today, TimeSlot.MORNING, OrderStatus.PICKUP_ASSIGNED);
        pickup(an, tai, today.minusDays(1), TimeSlot.EVENING, OrderStatus.PICKUP_ASSIGNED); // quá hẹn
        pickup(vague, tai, today, TimeSlot.MORNING, OrderStatus.PICKUP_ASSIGNED);
        pickup(hoaSen, tai, today.plusDays(1), TimeSlot.MORNING, OrderStatus.PICKUP_ASSIGNED); // ngày mai
        pickup(hoaSen, null, today, TimeSlot.MORNING, OrderStatus.PICKUP_PENDING); // chưa ai nhận
        pickup(hoaSen, tai, today, TimeSlot.MORNING, OrderStatus.RECEIVED); // đã lấy
        pickup(pho, hung, today, TimeSlot.MORNING, OrderStatus.PICKUP_ASSIGNED); // của tài xế khác

        Order delivering = Order.builder().driver(hung).deliveryDriver(tai).customer(bien).status(OrderStatus.DELIVERING)
                .totalBags(4).build();
        delivering.addItem(OrderItem.builder().itemName("Khăn bàn").quantity(new BigDecimal("10")).build());
        orderRepository.save(delivering);
    }

    @Test
    void todayRouteIsGroupedByArea() throws Exception {
        mvc.perform(get("/api/routes").header("Authorization", "Bearer " + login("taixe.tai")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverName", is("taixe.tai")))
                .andExpect(jsonPath("$.stops", hasSize(5)))
                // Hoàn Kiếm < Quận 1 (quá hẹn trước, rồi theo khung giờ) < Quận 5 (giao trả) < chưa rõ khu vực
                .andExpect(jsonPath("$.stops[*].customerName", contains("Nhà hàng Phố Cổ", "Spa An Nhiên",
                        "Khách sạn Hoa Sen", "Nhà hàng Biển Xanh", "Quán Bảo Khánh")))
                .andExpect(jsonPath("$.stops[*].area", contains("Hoàn Kiếm", "Quận 1", "Quận 1", "Quận 5", null)))
                .andExpect(jsonPath("$.stops[1].late", is(true)))
                .andExpect(jsonPath("$.stops[3].type", is("DELIVERY")))
                .andExpect(jsonPath("$.stops[3].bags", is(4)));
    }

    @Test
    void tomorrowHasNoDeliveries() throws Exception {
        mvc.perform(get("/api/routes").param("date", LocalDate.now().plusDays(1).toString())
                .header("Authorization", "Bearer " + login("taixe.tai")))
                .andExpect(jsonPath("$.stops", hasSize(5)))
                .andExpect(jsonPath("$.stops[?(@.type == 'DELIVERY')]", hasSize(0)));
    }

    @Test
    void accessByRole() throws Exception {
        String admin = login("admin");
        mvc.perform(get("/api/routes").param("driverId", String.valueOf(tai.getId())).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops", hasSize(5)));
        mvc.perform(get("/api/routes").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/routes").header("Authorization", "Bearer " + login("hoasen.nv")))
                .andExpect(status().isForbidden());
        // Tài xế khác chỉ thấy việc của mình (tham số driverId bị bỏ qua)
        mvc.perform(get("/api/routes").param("driverId", String.valueOf(tai.getId()))
                .header("Authorization", "Bearer " + login("taixe.hung")))
                .andExpect(jsonPath("$.stops", hasSize(1)));
    }

    private Customer customer(String name, String address) {
        return customerRepository.save(Customer.builder().name(name).address(address).build());
    }

    private void pickup(Customer c, User driver, LocalDate date, TimeSlot slot, OrderStatus status) {
        orderRepository.save(Order.builder().customer(c).driver(driver).requestedBy("x").preferredDate(date)
                .timeSlot(slot).status(status).build());
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

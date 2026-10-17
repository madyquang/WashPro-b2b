package com.washpro.washpro_b2b.integration;

import com.jayway.jsonpath.JsonPath;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderItem;
import com.washpro.washpro_b2b.entity.OrderStatus;
import com.washpro.washpro_b2b.entity.Payment;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.repository.CustomerPriceRepository;
import com.washpro.washpro_b2b.repository.OrderRepository;
import com.washpro.washpro_b2b.repository.PaymentRepository;
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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Số liệu tổng quan, công nợ (tuổi nợ) và sao kê tháng: kiểm trên một bộ dữ liệu có đủ trường hợp
 * (đơn đã hủy, đơn cũ chưa có ngày hoàn thành, dòng chưa có đơn giá, khoản thanh toán đã hủy, kỳ thanh toán khác nhau).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
class ReportApiIntegrationTest {

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

    Customer hoaSen;
    Customer bienXanh;
    User driver;
    YearMonth lastMonth;
    LocalDateTime twoMonthsAgo;

    @BeforeEach
    void seed() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        customerPriceRepository.deleteAll();
        userRepository.deleteAll();
        customerRepository.deleteAll();

        // Hoa Sen trả ngay khi hoàn thành (kỳ 0 ngày), Biển Xanh được nợ 30 ngày
        hoaSen = customerRepository.save(Customer.builder().name("Nhà hàng Hoa Sen").build());
        bienXanh = customerRepository.save(Customer.builder().name("Khách sạn Biển Xanh").paymentTermDays(30).build());
        user("admin", Role.WORKSHOP_ADMIN, null);
        user("ketoan", Role.WORKSHOP_ACCOUNTANT, null);
        user("hoasen.ql", Role.CUSTOMER_MANAGER, hoaSen);
        driver = user("taixe", Role.DRIVER, null);

        LocalDateTime now = LocalDateTime.now();
        lastMonth = YearMonth.now().minusMonths(1);
        twoMonthsAgo = lastMonth.minusMonths(1).atDay(15).atTime(9, 0);

        // Hoa Sen: 4 đơn hoàn thành = 230.000 đ
        completed(hoaSen, twoMonthsAgo, 100000);                       // trước tháng sao kê
        completed(hoaSen, lastMonth.atDay(10).atTime(14, 0), 50000);   // trong tháng sao kê
        completed(hoaSen, now, 70000);                                 // tháng này
        completed(hoaSen, null, 10000);                                // đơn cũ chưa có completed_at: lấy lần cập nhật cuối
        // Đơn đã hủy không tính
        Order deleted = order(hoaSen, OrderStatus.COMPLETED, null, now, 999000);
        deleted.setDeleted(true);
        orderRepository.save(deleted);
        // Đơn đang xử lý: một đơn quá hạn trả, một đơn hạn trả trong hôm nay
        orderRepository.save(order(hoaSen, OrderStatus.WASHING, now.minusHours(1), null, 0));
        LocalDateTime endOfToday = now.toLocalDate().plusDays(1).atStartOfDay();
        orderRepository.save(order(hoaSen, OrderStatus.RECEIVED,
                now.plusSeconds(java.time.Duration.between(now, endOfToday).getSeconds() / 2), null, 0));

        // Biển Xanh: hoàn thành 40 ngày trước, kỳ 30 ngày -> quá hạn 10 ngày; dòng chưa có giá không tính tiền
        Order bx = order(bienXanh, OrderStatus.COMPLETED, null, now.minusDays(40), 20000);
        bx.addItem(OrderItem.builder().itemName("Rèm").quantity(new BigDecimal("2")).build());
        orderRepository.save(bx);

        // Hoa Sen đã trả 20.000 (trước tháng sao kê) + 30.000 (trong tháng); khoản hủy không tính
        payment(hoaSen, twoMonthsAgo.toLocalDate().plusDays(1), 20000, false);
        payment(hoaSen, lastMonth.atDay(12), 30000, false);
        payment(hoaSen, lastMonth.atDay(13), 7000, true);
    }

    @Test
    void dashboardForWorkshop() throws Exception {
        dashboard("admin")
                .andExpect(jsonPath("$.totalOrders", is(7)))
                .andExpect(jsonPath("$.countsByStatus.COMPLETED", is(5)))
                .andExpect(jsonPath("$.countsByStatus.WASHING", is(1)))
                .andExpect(jsonPath("$.countsByStatus.RECEIVED", is(1)))
                .andExpect(jsonPath("$.countsByStatus.DELIVERING", is(0)))
                .andExpect(jsonPath("$.ordersToday", is(7)))
                .andExpect(jsonPath("$.overdueOrders", is(1)))
                .andExpect(jsonPath("$.dueToday", is(1)))
                .andExpect(jsonPath("$.billed", is(250000.0)))
                .andExpect(jsonPath("$.paid", is(50000.0)))
                .andExpect(jsonPath("$.balance", is(200000.0)))
                // Hoa Sen: 100.000 (trả FIFO còn 50.000) + 50.000 tháng trước; Biển Xanh: 20.000
                .andExpect(jsonPath("$.overdueDebt", is(120000.0)));
    }

    @Test
    void dashboardForCustomerManagerAndDriver() throws Exception {
        dashboard("hoasen.ql")
                .andExpect(jsonPath("$.totalOrders", is(6)))
                .andExpect(jsonPath("$.billed", is(230000.0)))
                .andExpect(jsonPath("$.balance", is(180000.0)));
        dashboard("taixe")
                .andExpect(jsonPath("$.totalOrders", is(7)))
                .andExpect(jsonPath("$.billed", nullValue()));
    }

    @Test
    void debtsWithAging() throws Exception {
        mvc.perform(get("/api/reports/debts").header("Authorization", "Bearer " + login("ketoan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                // Khách nợ quá hạn nhiều hơn lên đầu
                .andExpect(jsonPath("$[0].customerName", is("Nhà hàng Hoa Sen")))
                .andExpect(jsonPath("$[0].completedOrders", is(4)))
                .andExpect(jsonPath("$[0].billed", is(230000.0)))
                .andExpect(jsonPath("$[0].paid", is(50000.0)))
                .andExpect(jsonPath("$[0].balance", is(180000.0)))
                .andExpect(jsonPath("$[0].notDue", is(80000.0)))
                .andExpect(jsonPath("$[0].overdueTotal", is(100000.0)))
                .andExpect(jsonPath("$[0].oldestUnpaidDue", is(twoMonthsAgo.toLocalDate().toString())))
                .andExpect(jsonPath("$[1].customerName", is("Khách sạn Biển Xanh")))
                .andExpect(jsonPath("$[1].completedOrders", is(1)))
                .andExpect(jsonPath("$[1].overdue1to30", is(20000.0)));
    }

    @Test
    void monthlyStatement() throws Exception {
        mvc.perform(get("/api/reports/statement").param("customerId", String.valueOf(hoaSen.getId()))
                .param("month", lastMonth.toString()).header("Authorization", "Bearer " + login("ketoan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingBalance", is(80000.0)))
                .andExpect(jsonPath("$.orders", hasSize(1)))
                .andExpect(jsonPath("$.orders[0].amount", is(50000.0)))
                .andExpect(jsonPath("$.orders[0].paymentDue", is(lastMonth.atDay(10).toString())))
                .andExpect(jsonPath("$.payments", hasSize(1)))
                .andExpect(jsonPath("$.totalBilled", is(50000.0)))
                .andExpect(jsonPath("$.totalPaid", is(30000.0)))
                .andExpect(jsonPath("$.closingBalance", is(100000.0)));
    }

    @Test
    void paymentIsCheckedAgainstCurrentBalance() throws Exception {
        String token = login("ketoan");
        String over = "{\"customerId\":%d,\"amount\":180001}".formatted(hoaSen.getId());
        mvc.perform(post("/api/payments").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(over))
                .andExpect(status().isBadRequest());
        String exact = "{\"customerId\":%d,\"amount\":180000}".formatted(hoaSen.getId());
        mvc.perform(post("/api/payments").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(exact))
                .andExpect(status().isCreated());
    }

    // ===================== Hàm hỗ trợ =====================

    private ResultActions dashboard(String username) throws Exception {
        return mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + login(username)))
                .andExpect(status().isOk());
    }

    private void completed(Customer customer, LocalDateTime completedAt, long amount) {
        orderRepository.save(order(customer, OrderStatus.COMPLETED, null, completedAt, amount));
    }

    private Order order(Customer customer, OrderStatus status, LocalDateTime dueAt, LocalDateTime completedAt,
            long amount) {
        Order order = Order.builder()
                .driver(driver)
                .customer(customer)
                .status(status)
                .totalBags(1)
                .receivedAt(LocalDateTime.now())
                .dueAt(dueAt)
                .completedAt(completedAt)
                .build();
        order.addItem(OrderItem.builder().itemName("Khăn bàn").quantity(new BigDecimal("10"))
                .unitPrice(amount == 0 ? null : BigDecimal.valueOf(amount / 10)).build());
        return order;
    }

    private void payment(Customer customer, LocalDate paidDate, long amount, boolean voided) {
        paymentRepository.save(Payment.builder().customer(customer).amount(BigDecimal.valueOf(amount))
                .paidDate(paidDate).createdBy("ketoan").voided(voided).build());
    }

    private User user(String username, Role role, Customer customer) {
        return userRepository.save(User.builder()
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
}

package com.washpro.washpro_b2b.integration;

import com.jayway.jsonpath.JsonPath;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.repository.CustomerPriceRepository;
import com.washpro.washpro_b2b.repository.CustomerRepository;
import com.washpro.washpro_b2b.repository.OrderRepository;
import com.washpro.washpro_b2b.repository.PaymentRepository;
import com.washpro.washpro_b2b.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    /** PNG 1x1 hợp lệ (ảnh / chữ ký đính kèm). */
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

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

    @BeforeEach
    void seed() {
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
    void fullFlowFromPickupRequestToDebt() throws Exception {
        long id = request("hoasen.nv", null, 3, "Lấy ở cửa sau");
        mvc.perform(auth(get("/api/orders/" + id), login("hoasen.nv")))
                .andExpect(jsonPath("$.status", is("PICKUP_PENDING")))
                .andExpect(jsonPath("$.estimatedBags", is(3)))
                .andExpect(jsonPath("$.pickupNote", is("Lấy ở cửa sau")))
                .andExpect(jsonPath("$.driverId", nullValue()))
                .andExpect(jsonPath("$.dueAt", nullValue()));

        String driver = login("taixe1");
        mvc.perform(auth(put("/api/orders/" + id + "/accept"), driver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PICKUP_ASSIGNED")))
                .andExpect(jsonPath("$.driverName", is("taixe1")));

        // Nhận đồ phải có chữ ký khách hoặc ảnh túi đồ
        mvc.perform(auth(put("/api/orders/" + id + "/receive"), driver).contentType(MediaType.APPLICATION_JSON)
                .content(receiveJson("Khăn bàn", 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Cần chữ ký của khách hoặc ảnh túi đồ để xác nhận số lượng khi nhận đồ")));
        upload("taixe1", id, "PICKUP_SIGNATURE").andExpect(status().isCreated());
        mvc.perform(auth(put("/api/orders/" + id + "/receive"), driver).contentType(MediaType.APPLICATION_JSON)
                .content(receiveJson("Khăn bàn", 10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RECEIVED")))
                .andExpect(jsonPath("$.totalBags", is(2)))
                .andExpect(jsonPath("$.receivedAt", notNullValue()))
                .andExpect(jsonPath("$.dueAt", notNullValue()));

        String admin = login("admin");
        move(admin, id, "WASHING").andExpect(status().isOk());
        // Chưa có đơn giá thì chưa báo giặt xong được
        move(admin, id, "WASHED").andExpect(status().isBadRequest());
        mvc.perform(auth(put("/api/orders/" + id + "/items"), login("ketoan")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"itemName\":\"Khăn bàn\",\"quantity\":10,\"unitPrice\":5000}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billable", is(true)))
                .andExpect(jsonPath("$.totalAmount", is(50000.0)));
        move(admin, id, "WASHED").andExpect(status().isOk());

        // Đơn giặt xong là việc chờ mọi tài xế: tài xế khác nhận giao
        String other = login("taixe2");
        mvc.perform(auth(put("/api/orders/" + id + "/accept"), other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DELIVERING")))
                .andExpect(jsonPath("$.deliveryDriverName", is("taixe2")))
                .andExpect(jsonPath("$.driverName", is("taixe1")));
        // Chỉ tài xế đang giao mới báo giao xong; phải có chữ ký / ảnh lúc giao
        mvc.perform(auth(put("/api/orders/" + id + "/delivered"), driver)).andExpect(status().isBadRequest());
        mvc.perform(auth(put("/api/orders/" + id + "/delivered"), other))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Cần chữ ký của khách hoặc ảnh lúc giao trước khi báo giao xong")));
        upload("taixe2", id, "DELIVERY_SIGNATURE").andExpect(status().isCreated());
        mvc.perform(auth(put("/api/orders/" + id + "/delivered"), other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.history", hasSize(8)));

        // Kỳ thanh toán 0 ngày: toàn bộ tiền đơn vào công nợ của khách
        mvc.perform(auth(get("/api/reports/debts"), login("hoasen.ql")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].balance", is(50000.0)));
    }

    @Test
    void pickupRequestsAreScopedAndCancellableUntilPickedUp() throws Exception {
        long id = request("hoasen.nv", null, null, null);

        // Yêu cầu chờ tài xế: mọi tài xế thấy; khách khác không thấy
        mvc.perform(auth(get("/api/orders/" + id), login("taixe2"))).andExpect(status().isOk());
        mvc.perform(auth(get("/api/orders/" + id), login("bienxanh.nv"))).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/orders"), login("bienxanh.nv"))).andExpect(jsonPath("$.total", is(0)));

        // Đã có tài xế nhận: tài xế khác không thấy, không nhận trùng được
        mvc.perform(auth(put("/api/orders/" + id + "/accept"), login("taixe1"))).andExpect(status().isOk());
        mvc.perform(auth(get("/api/orders/" + id), login("taixe2"))).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/orders").param("mine", "true"), login("taixe1")))
                .andExpect(jsonPath("$.total", is(1)));
        mvc.perform(auth(get("/api/orders").param("mine", "true"), login("taixe2")))
                .andExpect(jsonPath("$.total", is(0)));

        // Trả lại việc: về chờ tài xế
        mvc.perform(auth(put("/api/orders/" + id + "/release"), login("taixe1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PICKUP_PENDING")))
                .andExpect(jsonPath("$.driverId", nullValue()));

        // Khách hủy yêu cầu của mình kèm lý do; vẫn xem lại được trong danh sách đã hủy
        mvc.perform(auth(delete("/api/orders/" + id).param("reason", "Đổi lịch"), login("bienxanh.nv")))
                .andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/orders/" + id).param("reason", "Đổi lịch"), login("hoasen.nv")))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/orders"), login("hoasen.nv"))).andExpect(jsonPath("$.total", is(0)));
        mvc.perform(auth(get("/api/orders").param("cancelled", "true"), login("hoasen.ql")))
                .andExpect(jsonPath("$.total", is(1)))
                .andExpect(jsonPath("$.content[0].cancelReason", is("Đổi lịch")))
                .andExpect(jsonPath("$.content[0].cancelledBy", is("hoasen.nv")));

        // Đã nhận đồ thì khách không tự hủy được nữa
        long received = receivedOrder(hoaSen, "Ga giường", 5);
        mvc.perform(auth(delete("/api/orders/" + received), login("hoasen.nv")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Tài xế đã lấy đồ, vui lòng gọi xưởng nếu cần hủy")));
    }

    @Test
    void pickupCanBeRescheduledUntilPickedUp() throws Exception {
        long id = request("hoasen.nv", null, null, null);
        mvc.perform(auth(put("/api/orders/" + id + "/accept"), login("taixe1"))).andExpect(status().isOk());
        String tomorrow = LocalDate.now().plusDays(1).toString();

        // Khách đổi lịch: tài xế giữ việc, lịch sử ghi lại lịch cũ → mới
        mvc.perform(auth(put("/api/orders/" + id + "/schedule"), login("hoasen.ql")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredDate\":\"%s\",\"timeSlot\":\"EVENING\"}".formatted(tomorrow)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredDate", is(tomorrow)))
                .andExpect(jsonPath("$.timeSlot", is("EVENING")))
                .andExpect(jsonPath("$.status", is("PICKUP_ASSIGNED")))
                .andExpect(jsonPath("$.history[-1].note", org.hamcrest.Matchers.startsWith("Đổi lịch lấy đồ: ")));

        String body = "{\"preferredDate\":\"%s\",\"timeSlot\":\"MORNING\"}";
        // Ngày trong quá khứ, khách khác, tài xế: không đổi được
        mvc.perform(auth(put("/api/orders/" + id + "/schedule"), login("hoasen.nv")).contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(LocalDate.now().minusDays(1)))).andExpect(status().isBadRequest());
        mvc.perform(auth(put("/api/orders/" + id + "/schedule"), login("bienxanh.nv")).contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(tomorrow))).andExpect(status().isNotFound());
        mvc.perform(auth(put("/api/orders/" + id + "/schedule"), login("taixe1")).contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(tomorrow))).andExpect(status().isForbidden());

        // Đã lấy đồ thì không đổi lịch nữa
        long received = receivedOrder(hoaSen, "Khăn bàn", 2);
        mvc.perform(auth(put("/api/orders/" + received + "/schedule"), login("admin")).contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(tomorrow)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Tài xế đã lấy đồ, không đổi ngày lấy được nữa")));
    }

    @Test
    void workshopAssignsDriversAndReceivesWalkInGoods() throws Exception {
        String admin = login("admin");
        long id = request("admin", bienXanh, null, null);
        long driverId = userRepository.findByUsername("taixe2").orElseThrow().getId();
        mvc.perform(auth(put("/api/orders/" + id + "/assign"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"driverId\":" + driverId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PICKUP_ASSIGNED")))
                .andExpect(jsonPath("$.driverName", is("taixe2")));
        // Tài xế được giao việc thấy trong "việc của tôi"
        mvc.perform(auth(get("/api/orders").param("mine", "true"), login("taixe2")))
                .andExpect(jsonPath("$.total", is(1)));

        // Khách mang đồ đến xưởng: quản lý xưởng nhận luôn, không cần tài xế, không bắt buộc chữ ký
        long walkIn = request("admin", hoaSen, null, null);
        mvc.perform(auth(put("/api/orders/" + walkIn + "/receive"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"totalBags\":1,\"items\":[{\"itemName\":\"Khăn bàn\",\"quantity\":4,\"unitPrice\":3000}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RECEIVED")))
                .andExpect(jsonPath("$.driverId", nullValue()));
        move(admin, walkIn, "WASHING").andExpect(status().isOk());
        move(admin, walkIn, "WASHED").andExpect(status().isOk());
        // Không nhảy thẳng sang đang giao: phải phân công tài xế giao
        move(admin, walkIn, "DELIVERING").andExpect(status().isBadRequest());
        mvc.perform(auth(put("/api/orders/" + walkIn + "/assign"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"driverId\":" + driverId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DELIVERING")))
                .andExpect(jsonPath("$.deliveryDriverName", is("taixe2")));
        // Tài xế trả lại việc giao: đơn về "Đã giặt xong" chờ tài xế khác
        mvc.perform(auth(put("/api/orders/" + walkIn + "/release"), login("taixe2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("WASHED")))
                .andExpect(jsonPath("$.deliveryDriverId", nullValue()));
    }

    @Test
    void rolesAreEnforced() throws Exception {
        long id = request("hoasen.nv", null, null, null);

        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        // Tài xế không tự tạo đơn; khách không nhận việc / không đổi trạng thái
        mvc.perform(auth(post("/api/orders"), login("taixe1")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":%d,\"preferredDate\":\"%s\"}".formatted(hoaSen.getId(), LocalDate.now())))
                .andExpect(status().isForbidden());
        mvc.perform(auth(put("/api/orders/" + id + "/accept"), login("hoasen.nv"))).andExpect(status().isForbidden());
        move(login("taixe1"), id, "WASHING").andExpect(status().isForbidden());
        // Ngày lấy đồ không được ở quá khứ
        mvc.perform(auth(post("/api/orders"), login("hoasen.nv")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredDate\":\"%s\"}".formatted(LocalDate.now().minusDays(1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listIsFilteredAndPagedOnServer() throws Exception {
        long first = receivedOrder(hoaSen, "Khăn bàn", 10);
        receivedOrder(hoaSen, "Ga giường", 4);
        receivedOrder(bienXanh, "Khăn bàn", 7);
        request("hoasen.nv", null, null, null);
        String admin = login("admin");
        move(admin, first, "WASHING").andExpect(status().isOk());

        mvc.perform(auth(get("/api/orders").param("size", "2"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(4)))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.counts.PICKUP_PENDING", is(1)))
                .andExpect(jsonPath("$.counts.RECEIVED", is(2)))
                .andExpect(jsonPath("$.counts.WASHING", is(1)))
                .andExpect(jsonPath("$.counts.COMPLETED", is(0)))
                .andExpect(jsonPath("$.counts.PENDING_CONFIRM").doesNotExist());

        mvc.perform(auth(get("/api/orders").param("size", "2").param("page", "1"), admin))
                .andExpect(jsonPath("$.content", hasSize(2)));

        // DUE: hạn trả gần nhất trước (Hoa Sen trả trong 24h, Biển Xanh 48h; yêu cầu chưa lấy chưa có hạn: cuối)
        mvc.perform(auth(get("/api/orders").param("sort", "DUE"), admin))
                .andExpect(jsonPath("$.content[0].id", is((int) first)))
                .andExpect(jsonPath("$.content[2].customerName", is("Khách sạn Biển Xanh")))
                .andExpect(jsonPath("$.content[3].status", is("PICKUP_PENDING")));

        mvc.perform(auth(get("/api/orders").param("status", "RECEIVED,WASHING").param("q", "khăn BÀN"), admin))
                .andExpect(jsonPath("$.total", is(2)));
        // Tìm theo tên tài xế (đơn chưa có tài xế vẫn không làm mất kết quả khác)
        mvc.perform(auth(get("/api/orders").param("q", "taixe1"), admin))
                .andExpect(jsonPath("$.total", is(3)));
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
                .andExpect(jsonPath("$.total", is(3)));
        // Tài xế khác chỉ thấy việc đang chờ tài xế
        mvc.perform(auth(get("/api/orders"), login("taixe2")))
                .andExpect(jsonPath("$.total", is(1)));

        mvc.perform(auth(get("/api/orders").param("size", "1000"), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Cỡ trang tối đa là 100")));
    }

    @Test
    void notesAreInternalToWorkshopAndDrivers() throws Exception {
        long id = receivedOrder(hoaSen, "Khăn bàn", 4);

        mvc.perform(auth(post("/api/orders/" + id + "/notes"), login("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"  Đã gọi chị Lan, hẹn bổ sung 2 khăn  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].body", is("Đã gọi chị Lan, hẹn bổ sung 2 khăn")))
                .andExpect(jsonPath("$[0].authorRole", is("WORKSHOP_ADMIN")));
        mvc.perform(auth(post("/api/orders/" + id + "/notes"), login("taixe1")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Khách dặn lần sau lấy cửa sau\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(auth(get("/api/orders/" + id), login("ketoan")))
                .andExpect(jsonPath("$.notes", hasSize(2)))
                .andExpect(jsonPath("$.notes[1].author", is("taixe1")));
        // Khách không thấy và không ghi được ghi chú nội bộ
        mvc.perform(auth(get("/api/orders/" + id), login("hoasen.ql")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").doesNotExist());
        mvc.perform(auth(post("/api/orders/" + id + "/notes"), login("hoasen.ql")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"x\"}"))
                .andExpect(status().isForbidden());
        // Tài xế khác không ghi vào đơn không thuộc mình; nội dung trống bị từ chối
        mvc.perform(auth(post("/api/orders/" + id + "/notes"), login("taixe2")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/orders/" + id + "/notes"), login("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void companyHotlineIsVisibleToEveryRole() throws Exception {
        mvc.perform(get("/api/company")).andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/api/company"), login("hoasen.nv")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotline", is("0909 123 456")));
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

    /** Gửi yêu cầu lấy đồ hôm nay (khách gửi cho mình; quản lý xưởng gửi hộ `customer`). */
    private long request(String username, Customer customer, Integer bags, String note) throws Exception {
        String json = "{\"preferredDate\":\"%s\",\"timeSlot\":\"MORNING\"%s%s%s}".formatted(LocalDate.now(),
                customer != null ? ",\"customerId\":" + customer.getId() : "",
                bags != null ? ",\"estimatedBags\":" + bags : "",
                note != null ? ",\"note\":\"" + note + "\"" : "");
        String body = mvc.perform(auth(post("/api/orders"), login(username)).contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PICKUP_PENDING")))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Đơn đã được taixe1 nhận đồ (yêu cầu → nhận việc → chữ ký khách → nhận đồ). */
    private long receivedOrder(Customer customer, String itemName, int quantity) throws Exception {
        long id = request("admin", customer, null, null);
        String driver = login("taixe1");
        mvc.perform(auth(put("/api/orders/" + id + "/accept"), driver)).andExpect(status().isOk());
        upload("taixe1", id, "PICKUP_SIGNATURE").andExpect(status().isCreated());
        mvc.perform(auth(put("/api/orders/" + id + "/receive"), driver).contentType(MediaType.APPLICATION_JSON)
                .content(receiveJson(itemName, quantity)))
                .andExpect(status().isOk());
        return id;
    }

    private static String receiveJson(String itemName, int quantity) {
        return "{\"totalBags\":2,\"items\":[{\"itemName\":\"%s\",\"quantity\":%d}]}".formatted(itemName, quantity);
    }

    private ResultActions move(String token, long id, String target) throws Exception {
        return mvc.perform(auth(put("/api/orders/" + id + "/status"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + target + "\"}"));
    }

    private ResultActions upload(String username, long orderId, String kind) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "anh.png", MediaType.IMAGE_PNG_VALUE, PNG);
        return mvc.perform(multipart("/api/orders/" + orderId + "/attachments").file(file).param("kind", kind)
                .header("Authorization", "Bearer " + login(username)));
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}

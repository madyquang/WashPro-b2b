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
import com.washpro.washpro_b2b.repository.OrderAttachmentRepository;
import com.washpro.washpro_b2b.repository.OrderRepository;
import com.washpro.washpro_b2b.repository.PaymentRepository;
import com.washpro.washpro_b2b.repository.PickupRequestRepository;
import com.washpro.washpro_b2b.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ảnh / chữ ký đính kèm đơn: quyền theo vai trò và phạm vi đơn, chỉ nhận ảnh thật, xóa cả file trên đĩa. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
class AttachmentApiIntegrationTest {

    private static final String PASSWORD = "Test@1234";
    /** PNG 1x1 hợp lệ. */
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
    PickupRequestRepository pickupRepository;
    @Autowired
    CustomerPriceRepository customerPriceRepository;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    OrderAttachmentRepository attachmentRepository;
    @Value("${app.uploads.dir}")
    String uploadsDir;

    long orderId;

    @BeforeEach
    void seed() {
        attachmentRepository.deleteAll();
        notificationRepository.deleteAll();
        pickupRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        customerPriceRepository.deleteAll();
        userRepository.deleteAll();
        customerRepository.deleteAll();

        Customer hoaSen = customerRepository.save(Customer.builder().name("Nhà hàng Hoa Sen").build());
        Customer bienXanh = customerRepository.save(Customer.builder().name("Khách sạn Biển Xanh").build());
        user("admin", Role.WORKSHOP_ADMIN, null);
        user("ketoan", Role.WORKSHOP_ACCOUNTANT, null);
        User driver = user("taixe1", Role.DRIVER, null);
        user("taixe2", Role.DRIVER, null);
        user("hoasen.nv", Role.CUSTOMER_STAFF, hoaSen);
        user("bienxanh.nv", Role.CUSTOMER_STAFF, bienXanh);

        Order order = Order.builder().driver(driver).customer(hoaSen).status(OrderStatus.PENDING_CONFIRM)
                .totalBags(1).dueAt(LocalDateTime.now().plusDays(1)).build();
        order.addItem(OrderItem.builder().itemName("Khăn bàn").quantity(new BigDecimal("10")).build());
        orderId = orderRepository.save(order).getId();
    }

    @Test
    void driverAttachesPhotoAndEveryoneInScopeCanSeeIt() throws Exception {
        String body = upload("taixe1", "PICKUP_PHOTO", PNG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind", is("PICKUP_PHOTO")))
                .andExpect(jsonPath("$.contentType", is("image/png")))
                .andExpect(jsonPath("$.uploadedBy", is("taixe1")))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        for (String who : new String[] { "admin", "ketoan", "hoasen.nv", "taixe1" }) {
            mvc.perform(get("/api/orders/" + orderId + "/attachments").header("Authorization", "Bearer " + login(who)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }
        mvc.perform(get("/api/attachments/" + id + "/file").header("Authorization", "Bearer " + login("hoasen.nv")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));

        // Ngoài phạm vi: không thấy cả danh sách lẫn file (404 như đơn)
        for (String who : new String[] { "taixe2", "bienxanh.nv" }) {
            mvc.perform(get("/api/orders/" + orderId + "/attachments").header("Authorization", "Bearer " + login(who)))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/attachments/" + id + "/file").header("Authorization", "Bearer " + login(who)))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void kindsAreLimitedByRole() throws Exception {
        upload("hoasen.nv", "PICKUP_PHOTO", PNG).andExpect(status().isForbidden());
        upload("hoasen.nv", "ISSUE_PHOTO", PNG).andExpect(status().isCreated());
        upload("taixe1", "ISSUE_PHOTO", PNG).andExpect(status().isForbidden());
        upload("taixe1", "DELIVERY_SIGNATURE", PNG).andExpect(status().isCreated());
        upload("ketoan", "PICKUP_PHOTO", PNG).andExpect(status().isForbidden());
        upload("admin", "DELIVERY_PHOTO", PNG).andExpect(status().isCreated());
        upload("taixe2", "PICKUP_PHOTO", PNG).andExpect(status().isNotFound());
    }

    @Test
    void onlyRealImagesAreAccepted() throws Exception {
        upload("taixe1", "PICKUP_PHOTO", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Chỉ nhận ảnh JPG, PNG hoặc WEBP")));
        upload("taixe1", "NOT_A_KIND", PNG).andExpect(status().isBadRequest());
    }

    @Test
    void deleteRemovesFileAndIsLimitedToUploaderOrAdmin() throws Exception {
        String body = upload("taixe1", "PICKUP_PHOTO", PNG).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        Path file = Path.of(uploadsDir).toAbsolutePath()
                .resolve(attachmentRepository.findById(id).orElseThrow().getStoredName());
        assertThat(file).exists();

        mvc.perform(delete("/api/attachments/" + id).header("Authorization", "Bearer " + login("hoasen.nv")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/attachments/" + id).header("Authorization", "Bearer " + login("taixe1")))
                .andExpect(status().isNoContent());
        assertThat(Files.exists(file)).isFalse();
        mvc.perform(get("/api/orders/" + orderId + "/attachments").header("Authorization", "Bearer " + login("admin")))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private ResultActions upload(String username, String kind, byte[] bytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "anh.png", MediaType.IMAGE_PNG_VALUE, bytes);
        return mvc.perform(multipart("/api/orders/" + orderId + "/attachments").file(file).param("kind", kind)
                .header("Authorization", "Bearer " + login(username)));
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

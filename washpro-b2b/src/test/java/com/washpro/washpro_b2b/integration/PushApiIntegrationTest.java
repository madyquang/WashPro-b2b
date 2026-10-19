package com.washpro.washpro_b2b.integration;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.repository.CustomerPriceRepository;
import com.washpro.washpro_b2b.repository.CustomerRepository;
import com.washpro.washpro_b2b.repository.NotificationRepository;
import com.washpro.washpro_b2b.repository.OrderRepository;
import com.washpro.washpro_b2b.repository.PaymentRepository;
import com.washpro.washpro_b2b.repository.PushSubscriptionRepository;
import com.washpro.washpro_b2b.repository.UserRepository;
import com.washpro.washpro_b2b.service.push.WebPushCrypto;
import com.washpro.washpro_b2b.service.push.WebPushCryptoTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thông báo đẩy: một máy chủ push giả chạy trên localhost nhận thông báo; kiểm tra chữ ký VAPID, giải mã nội dung
 * như trình duyệt, xóa thiết bị đã hủy đăng ký, đăng xuất mọi thiết bị thì không gửi nữa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
class PushApiIntegrationTest {

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
    @Autowired
    PushSubscriptionRepository subscriptionRepository;

    record Received(String path, Map<String, String> headers, byte[] body) {
    }

    HttpServer server;
    String base;
    final BlockingQueue<Received> received = new LinkedBlockingQueue<>();
    User admin;

    @BeforeEach
    void seed() throws Exception {
        subscriptionRepository.deleteAll();
        notificationRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        customerPriceRepository.deleteAll();
        userRepository.deleteAll();
        customerRepository.deleteAll();

        Customer hoaSen = customerRepository.save(Customer.builder().name("Nhà hàng Hoa Sen").build());
        admin = user("admin", Role.WORKSHOP_ADMIN, null);
        user("taixe", Role.DRIVER, null);
        user("hoasen.nv", Role.CUSTOMER_STAFF, hoaSen);

        // Máy chủ push giả: /gone trả 410 (thiết bị đã hủy đăng ký), còn lại 201
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            Map<String, String> headers = new java.util.HashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.getFirst()));
            received.add(new Received(exchange.getRequestURI().getPath(), headers, exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(exchange.getRequestURI().getPath().startsWith("/gone") ? 410 : 201, -1);
            exchange.close();
        });
        server.start();
        base = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void notificationIsPushedEncryptedAndSigned() throws Exception {
        KeyPair device = WebPushCrypto.generateKeyPair();
        byte[] devicePublic = WebPushCrypto.rawPublic((ECPublicKey) device.getPublic());
        byte[] auth = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        subscribe("admin", base + "/push/admin", devicePublic, auth);

        createPickup();

        Received r = received.poll(10, TimeUnit.SECONDS);
        assertThat(r).as("máy chủ push không nhận được thông báo").isNotNull();
        assertThat(r.path()).isEqualTo("/push/admin");
        assertThat(r.headers()).containsEntry("content-encoding", "aes128gcm").containsEntry("ttl", "86400");

        // Chữ ký VAPID: JWT ES256 ký bằng khóa có khóa công khai đúng như API công bố, aud = máy chủ push
        String publicKey = JsonPath.read(mvc.perform(get("/api/push/public-key")
                .header("Authorization", "Bearer " + login("taixe"))).andReturn().getResponse().getContentAsString(), "$.publicKey");
        String authorization = r.headers().get("authorization");
        assertThat(authorization).startsWith("vapid t=").endsWith(", k=" + publicKey);
        String jwt = authorization.substring("vapid t=".length(), authorization.indexOf(','));
        String[] parts = jwt.split("\\.");
        Signature es256 = Signature.getInstance("SHA256withECDSAinP1363Format");
        es256.initVerify(WebPushCrypto.publicKey(WebPushCrypto.unb64(publicKey)));
        es256.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(es256.verify(WebPushCrypto.unb64(parts[2]))).isTrue();
        assertThat(new String(WebPushCrypto.unb64(parts[1]), StandardCharsets.UTF_8)).contains("\"aud\":\"" + base + "\"");

        // Thiết bị giải mã được nội dung
        String payload = new String(WebPushCryptoTest.decrypt(r.body(), (ECPrivateKey) device.getPrivate(), devicePublic, auth),
                StandardCharsets.UTF_8);
        assertThat(JsonPath.<String>read(payload, "$.title")).isEqualTo("Yêu cầu lấy đồ mới: Nhà hàng Hoa Sen");
        assertThat(JsonPath.<String>read(payload, "$.link")).isEqualTo("/pickups?tab=open");
        assertThat(JsonPath.<String>read(payload, "$.tag")).isEqualTo("PICKUP_NEW");
    }

    @Test
    void unsubscribedDeviceIsRemoved() throws Exception {
        KeyPair device = WebPushCrypto.generateKeyPair();
        subscribe("admin", base + "/gone/1", WebPushCrypto.rawPublic((ECPublicKey) device.getPublic()), new byte[16]);
        assertThat(subscriptionRepository.count()).isEqualTo(1);

        createPickup();

        assertThat(received.poll(10, TimeUnit.SECONDS)).isNotNull();
        for (int i = 0; i < 50 && subscriptionRepository.count() > 0; i++) {
            Thread.sleep(100);
        }
        assertThat(subscriptionRepository.count()).isZero();
    }

    @Test
    void logoutEverywhereStopsPushAndOthersCannotRemoveMyDevice() throws Exception {
        KeyPair device = WebPushCrypto.generateKeyPair();
        String endpoint = base + "/push/admin";
        subscribe("admin", endpoint, WebPushCrypto.rawPublic((ECPublicKey) device.getPublic()), new byte[16]);

        // Người khác gửi yêu cầu tắt thông báo cho thiết bị của mình: không có tác dụng
        mvc.perform(post("/api/push/subscriptions/remove").header("Authorization", "Bearer " + login("taixe"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + endpoint + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(subscriptionRepository.countByUserId(admin.getId())).isEqualTo(1);

        mvc.perform(post("/api/auth/logout-all").header("Authorization", "Bearer " + login("admin")))
                .andExpect(status().isNoContent());
        assertThat(subscriptionRepository.countByUserId(admin.getId())).isZero();

        createPickup();
        assertThat(received.poll(1500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void invalidKeysAreRejected() throws Exception {
        mvc.perform(post("/api/push/subscriptions").header("Authorization", "Bearer " + login("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"" + base + "/x\",\"keys\":{\"p256dh\":\"abc\",\"auth\":\"def\"}}"))
                .andExpect(status().isBadRequest());
    }

    private void subscribe(String username, String endpoint, byte[] p256dh, byte[] auth) throws Exception {
        mvc.perform(post("/api/push/subscriptions").header("Authorization", "Bearer " + login(username))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"%s\",\"keys\":{\"p256dh\":\"%s\",\"auth\":\"%s\"}}"
                        .formatted(endpoint, WebPushCrypto.b64(p256dh), WebPushCrypto.b64(auth))))
                .andExpect(status().isNoContent());
    }

    private void createPickup() throws Exception {
        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + login("hoasen.nv"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredDate\":\"%s\",\"items\":[{\"itemName\":\"Khăn bàn\",\"quantity\":1}]}".formatted(LocalDate.now())))
                .andExpect(status().isCreated());
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

package com.washpro.washpro_b2b.service.push;

import com.washpro.washpro_b2b.entity.PushSubscription;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.repository.PushSubscriptionRepository;
import com.washpro.washpro_b2b.security.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thông báo đẩy (Web Push) tới điện thoại / máy tính đã bật nhận, kể cả khi không mở ứng dụng.
 * Gửi sau khi giao dịch nghiệp vụ đã lưu xong, chạy nền (không làm chậm thao tác); thiết bị đã hủy đăng ký
 * (máy chủ push trả 404 / 410) bị xóa khỏi danh sách.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    /** Chỉ gửi tới máy chủ push chính thống (địa chỉ do trình duyệt gửi lên: chặn dùng máy chủ để gọi nơi khác). */
    private static final Set<String> PUSH_HOSTS = Set.of("fcm.googleapis.com", "updates.push.services.mozilla.com",
            "push.services.mozilla.com", "web.push.apple.com");
    private static final List<String> PUSH_HOST_SUFFIXES = List.of(".push.apple.com", ".notify.windows.com");

    private final PushSubscriptionRepository repository;
    private final CurrentUserService currentUserService;
    private final VapidKeys vapid;
    private final TransactionTemplate tx;
    private final boolean allowAnyEndpoint;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public PushService(PushSubscriptionRepository repository, CurrentUserService currentUserService, VapidKeys vapid,
            TransactionTemplate tx, @Value("${app.push.allow-any-endpoint:false}") boolean allowAnyEndpoint) {
        this.repository = repository;
        this.currentUserService = currentUserService;
        this.vapid = vapid;
        this.tx = tx;
        this.allowAnyEndpoint = allowAnyEndpoint;
    }

    public String publicKey() {
        return vapid.publicKey();
    }

    /** Thiết bị đang dùng bật nhận thông báo (đăng ký lại cùng thiết bị thì cập nhật, kể cả khi đổi tài khoản). */
    @Transactional
    public void subscribe(String endpoint, String p256dh, String auth, String userAgent) {
        User user = currentUserService.getCurrentUser();
        checkEndpoint(endpoint);
        try {
            if (WebPushCrypto.unb64(auth).length != 16) {
                throw ApiException.badRequest("Khóa đăng ký không hợp lệ");
            }
            WebPushCrypto.publicKey(WebPushCrypto.unb64(p256dh));
        } catch (Exception e) {
            throw ApiException.badRequest("Khóa đăng ký không hợp lệ");
        }
        PushSubscription s = repository.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        s.setUser(user);
        s.setEndpoint(endpoint);
        s.setP256dh(p256dh);
        s.setAuth(auth);
        s.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 200)));
        repository.save(s);
    }

    /** Thiết bị đang dùng tắt nhận thông báo (chỉ xóa đăng ký của chính mình). */
    @Transactional
    public void unsubscribe(String endpoint) {
        User user = currentUserService.getCurrentUser();
        repository.findByEndpoint(endpoint).filter(s -> s.getUser().getId().equals(user.getId()))
                .ifPresent(repository::delete);
    }

    /** Đăng xuất mọi thiết bị: không gửi thông báo tới các thiết bị cũ nữa. */
    @Transactional
    public void removeAll(Long userId) {
        repository.deleteByUserId(userId);
    }

    /** Gửi thông báo tới mọi thiết bị của các người dùng; nếu đang trong giao dịch thì đợi giao dịch lưu xong. */
    public void notifyUsers(Collection<Long> userIds, String title, String body, String link, String tag) {
        if (userIds.isEmpty()) {
            return;
        }
        byte[] payload = ("{\"title\":" + json(title) + ",\"body\":" + json(body) + ",\"link\":" + json(link)
                + ",\"tag\":" + json(tag) + "}").getBytes(StandardCharsets.UTF_8);
        List<Long> ids = List.copyOf(userIds);
        Runnable send = () -> executor.submit(() -> sendToUsers(ids, payload));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private void sendToUsers(List<Long> userIds, byte[] payload) {
        List<PushSubscription> subs = tx.execute(status -> repository.findByUserIdIn(userIds));
        if (subs == null) {
            return;
        }
        for (PushSubscription s : subs) {
            try {
                int status = send(s, payload);
                if (status == 404 || status == 410) {
                    tx.executeWithoutResult(t -> repository.deleteByEndpoint(s.getEndpoint()));
                } else if (status >= 400) {
                    log.warn("Máy chủ push từ chối thông báo (HTTP {}) tới {}", status, URI.create(s.getEndpoint()).getHost());
                }
            } catch (Exception e) {
                log.warn("Không gửi được thông báo đẩy tới {}: {}", URI.create(s.getEndpoint()).getHost(), e.toString());
            }
        }
    }

    int send(PushSubscription s, byte[] payload) throws Exception {
        byte[] body = WebPushCrypto.encrypt(payload, WebPushCrypto.unb64(s.getP256dh()), WebPushCrypto.unb64(s.getAuth()));
        HttpRequest req = HttpRequest.newBuilder(URI.create(s.getEndpoint()))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", vapid.authorization(s.getEndpoint()))
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("TTL", "86400")
                .header("Urgency", "high")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private void checkEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (Exception e) {
            throw ApiException.badRequest("Địa chỉ đăng ký thông báo không hợp lệ");
        }
        if (!allowAnyEndpoint && !isPushServer(uri)) {
            throw ApiException.badRequest("Trình duyệt này dùng máy chủ thông báo không được hỗ trợ");
        }
    }

    /** Địa chỉ HTTPS của một máy chủ push chính thống. */
    static boolean isPushServer(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        boolean known = PUSH_HOSTS.contains(host) || PUSH_HOST_SUFFIXES.stream().anyMatch(host::endsWith);
        return "https".equals(uri.getScheme()) && known && uri.getUserInfo() == null;
    }

    private static String json(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}

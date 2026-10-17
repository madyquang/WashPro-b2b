package com.washpro.washpro_b2b.service.push;

import com.washpro.washpro_b2b.entity.AppSecret;
import com.washpro.washpro_b2b.repository.AppSecretRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;

/**
 * Khóa VAPID (RFC 8292) để máy chủ push biết thông báo đến từ đúng xưởng. Lấy từ VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY
 * nếu đặt; không thì tự sinh lần đầu và lưu trong CSDL (bảng app_secrets) để giữ nguyên qua các lần khởi động
 * (đổi khóa thì mọi thiết bị phải bật lại thông báo).
 */
@Component
public class VapidKeys {

    private static final String PUBLIC = "vapid.public";
    private static final String PRIVATE = "vapid.private";

    private final AppSecretRepository secrets;
    private final TransactionTemplate tx;
    private final String configuredPublic;
    private final String configuredPrivate;
    private final String subject;
    private volatile byte[] publicRaw;
    private volatile ECPrivateKey privateKey;

    public VapidKeys(AppSecretRepository secrets, TransactionTemplate tx,
            @Value("${app.push.vapid-public-key:}") String configuredPublic,
            @Value("${app.push.vapid-private-key:}") String configuredPrivate,
            @Value("${app.push.subject}") String subject) {
        this.secrets = secrets;
        this.tx = tx;
        this.configuredPublic = configuredPublic;
        this.configuredPrivate = configuredPrivate;
        this.subject = subject;
    }

    /** Khóa công khai (base64url, 65 byte) để trình duyệt đăng ký nhận thông báo. */
    public String publicKey() {
        load();
        return WebPushCrypto.b64(publicRaw);
    }

    /** Giá trị header Authorization cho một lần gửi tới máy chủ push của `endpoint`. */
    public String authorization(String endpoint) throws GeneralSecurityException {
        load();
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        String header = WebPushCrypto.b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = WebPushCrypto.b64(("{\"aud\":\"" + audience + "\",\"exp\":"
                + Instant.now().plusSeconds(12 * 3600).getEpochSecond() + ",\"sub\":\"" + subject + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        // ES256 của JWT cần chữ ký dạng R || S (64 byte), không phải DER
        Signature es256 = Signature.getInstance("SHA256withECDSAinP1363Format");
        es256.initSign(privateKey);
        es256.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        String jwt = header + "." + claims + "." + WebPushCrypto.b64(es256.sign());
        return "vapid t=" + jwt + ", k=" + WebPushCrypto.b64(publicRaw);
    }

    private void load() {
        if (privateKey != null) {
            return;
        }
        synchronized (this) {
            if (privateKey != null) {
                return;
            }
            try {
                if (!configuredPublic.isBlank() && !configuredPrivate.isBlank()) {
                    use(WebPushCrypto.unb64(configuredPublic), WebPushCrypto.unb64(configuredPrivate));
                    return;
                }
                tx.executeWithoutResult(status -> {
                    var pub = secrets.findById(PUBLIC);
                    var priv = secrets.findById(PRIVATE);
                    try {
                        if (pub.isPresent() && priv.isPresent()) {
                            use(WebPushCrypto.unb64(pub.get().getValue()), WebPushCrypto.unb64(priv.get().getValue()));
                        } else {
                            KeyPair kp = WebPushCrypto.generateKeyPair();
                            byte[] p = WebPushCrypto.rawPublic((ECPublicKey) kp.getPublic());
                            byte[] s = WebPushCrypto.rawPrivate((ECPrivateKey) kp.getPrivate());
                            secrets.save(new AppSecret(PUBLIC, WebPushCrypto.b64(p)));
                            secrets.save(new AppSecret(PRIVATE, WebPushCrypto.b64(s)));
                            use(p, s);
                        }
                    } catch (GeneralSecurityException e) {
                        throw new IllegalStateException(e);
                    }
                });
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Khóa VAPID không hợp lệ", e);
            }
        }
    }

    private void use(byte[] pub, byte[] priv) throws GeneralSecurityException {
        WebPushCrypto.publicKey(pub); // kiểm tra hợp lệ
        this.publicRaw = pub;
        this.privateKey = WebPushCrypto.privateKey(priv);
    }
}

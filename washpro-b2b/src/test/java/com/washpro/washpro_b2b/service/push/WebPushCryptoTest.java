package com.washpro.washpro_b2b.service.push;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import static com.washpro.washpro_b2b.service.push.WebPushCrypto.b64;
import static com.washpro.washpro_b2b.service.push.WebPushCrypto.concat;
import static com.washpro.washpro_b2b.service.push.WebPushCrypto.hkdfExpand;
import static com.washpro.washpro_b2b.service.push.WebPushCrypto.hmac;
import static com.washpro.washpro_b2b.service.push.WebPushCrypto.unb64;
import static org.assertj.core.api.Assertions.assertThat;

public class WebPushCryptoTest {

    /** Ví dụ trong RFC 8291, Phụ lục A. */
    @Test
    void matchesRfc8291Example() throws Exception {
        byte[] plaintext = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8);
        byte[] asPrivate = unb64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
        byte[] asPublic = unb64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        byte[] uaPublic = unb64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] auth = unb64("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] salt = unb64("DGv6ra1nlYgDCS1FRnbzlw");
        KeyPair as = new KeyPair(WebPushCrypto.publicKey(asPublic), WebPushCrypto.privateKey(asPrivate));

        byte[] body = WebPushCrypto.encrypt(plaintext, uaPublic, auth, as, salt);

        assertThat(b64(body)).isEqualTo("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6"
                + "e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    /** Trình duyệt (bên nhận) giải mã ra đúng nội dung. */
    @Test
    void receiverCanDecrypt() throws Exception {
        KeyPair ua = WebPushCrypto.generateKeyPair();
        byte[] auth = new byte[16];
        Arrays.fill(auth, (byte) 7);
        byte[] payload = "{\"title\":\"Yêu cầu lấy đồ mới\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = WebPushCrypto.encrypt(payload, WebPushCrypto.rawPublic((ECPublicKey) ua.getPublic()), auth);

        assertThat(decrypt(body, (ECPrivateKey) ua.getPrivate(), WebPushCrypto.rawPublic((ECPublicKey) ua.getPublic()), auth))
                .isEqualTo(payload);
    }

    @Test
    void rawKeysRoundTrip() throws Exception {
        KeyPair kp = WebPushCrypto.generateKeyPair();
        byte[] pub = WebPushCrypto.rawPublic((ECPublicKey) kp.getPublic());
        byte[] priv = WebPushCrypto.rawPrivate((ECPrivateKey) kp.getPrivate());
        assertThat(pub).hasSize(65);
        assertThat(priv).hasSize(32);
        assertThat(WebPushCrypto.publicKey(pub).getW()).isEqualTo(((ECPublicKey) kp.getPublic()).getW());
        assertThat(WebPushCrypto.privateKey(priv).getS()).isEqualTo(((ECPrivateKey) kp.getPrivate()).getS());
    }

    /** Giải mã như trình duyệt (RFC 8291 phía nhận), dùng trong test. */
    public static byte[] decrypt(byte[] body, ECPrivateKey uaPrivate, byte[] uaPublic, byte[] auth) throws Exception {
        ByteBuffer buf = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buf.get(salt);
        buf.getInt();
        byte[] asPublic = new byte[buf.get() & 0xFF];
        buf.get(asPublic);
        byte[] cipherText = new byte[buf.remaining()];
        buf.get(cipherText);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(uaPrivate);
        ka.doPhase(WebPushCrypto.publicKey(asPublic), true);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
        byte[] ikm = hkdfExpand(hmac(auth, ka.generateSecret()), keyInfo, 32);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = hkdfExpand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdfExpand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = gcm.doFinal(cipherText);
        // Bỏ byte phân cách 0x02 cuối bản ghi
        assertThat(plain[plain.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(plain, plain.length - 1);
    }
}

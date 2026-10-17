package com.washpro.washpro_b2b.service.push;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/** Chỉ gửi tới máy chủ push chính thống: địa chỉ do trình duyệt gửi lên không được dùng để gọi máy khác (SSRF). */
class PushEndpointTest {

    @ParameterizedTest
    @CsvSource({
            "https://fcm.googleapis.com/fcm/send/abc:xyz, true",
            "https://updates.push.services.mozilla.com/wpush/v2/gAAA, true",
            "https://web.push.apple.com/QGZ..., true",
            "https://wns2-sg2p.notify.windows.com/w/?token=1, true",
            "http://fcm.googleapis.com/fcm/send/abc, false",
            "https://fcm.googleapis.com.evil.com/x, false",
            "https://evilfcm.googleapis.com/x, false",
            "https://localhost:8080/api/users, false",
            "https://169.254.169.254/latest/meta-data, false",
            "https://user@fcm.googleapis.com/x, false",
    })
    void onlyKnownPushServers(String endpoint, boolean allowed) {
        assertThat(PushService.isPushServer(URI.create(endpoint))).isEqualTo(allowed);
    }
}

package com.washpro.washpro_b2b.controller;

import com.washpro.washpro_b2b.service.push.PushService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Bật / tắt thông báo đẩy trên thiết bị đang dùng (mọi vai trò). */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;

    /** Dạng PushSubscription.toJSON() của trình duyệt. */
    public record SubscriptionRequest(@NotBlank @Size(max = 700) String endpoint, @NotNull @Valid Keys keys) {
        public record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {
        }
    }

    public record EndpointRequest(@NotBlank String endpoint) {
    }

    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> publicKey() {
        return ResponseEntity.ok(Map.of("publicKey", pushService.publicKey()));
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody SubscriptionRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        pushService.subscribe(request.endpoint(), request.keys().p256dh(), request.keys().auth(), userAgent);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/subscriptions/remove")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody EndpointRequest request) {
        pushService.unsubscribe(request.endpoint());
        return ResponseEntity.noContent().build();
    }
}

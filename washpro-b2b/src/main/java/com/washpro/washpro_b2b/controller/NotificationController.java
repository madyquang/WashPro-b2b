package com.washpro.washpro_b2b.controller;

import com.washpro.washpro_b2b.dto.NotificationDtos.ListResponse;
import com.washpro.washpro_b2b.dto.NotificationDtos.UnreadResponse;
import com.washpro.washpro_b2b.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Thông báo của chính người đăng nhập (mọi vai trò). */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ListResponse> mine(@RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(notificationService.mine(limit));
    }

    /** Gọi định kỳ để cập nhật số trên biểu tượng chuông. */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadResponse> unreadCount() {
        return ResponseEntity.ok(new UnreadResponse(notificationService.unreadCount()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        notificationService.markAllRead();
        return ResponseEntity.noContent().build();
    }
}

package com.washpro.washpro_b2b.dto;

import com.washpro.washpro_b2b.entity.Notification;
import com.washpro.washpro_b2b.entity.NotificationType;

import java.time.LocalDateTime;
import java.util.List;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record Item(Long id, NotificationType type, String title, String body, String link,
            LocalDateTime createdAt, boolean read) {

        public static Item from(Notification n) {
            return new Item(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(), n.getCreatedAt(),
                    n.getReadAt() != null);
        }
    }

    /** Các thông báo mới nhất và số chưa đọc. */
    public record ListResponse(List<Item> items, long unread) {
    }

    public record UnreadResponse(long unread) {
    }
}

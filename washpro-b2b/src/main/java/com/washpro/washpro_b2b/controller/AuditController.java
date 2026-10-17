package com.washpro.washpro_b2b.controller;

import com.washpro.washpro_b2b.entity.AuditAction;
import com.washpro.washpro_b2b.entity.AuditEntry;
import com.washpro.washpro_b2b.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Nhật ký thay đổi (chỉ đọc) cho quản lý xưởng và kế toán. */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    public record Entry(Long id, LocalDateTime createdAt, String actor, String actorRole,
            AuditAction.Category category, AuditAction action, String actionLabel, Long entityId, Long customerId,
            String summary, String details) {

        static Entry from(AuditEntry e) {
            return new Entry(e.getId(), e.getCreatedAt(), e.getActor(), e.getActorRole(), e.getCategory(), e.getAction(),
                    e.getAction().getLabel(), e.getEntityId(), e.getCustomerId(), e.getSummary(), e.getDetails());
        }
    }

    public record PageResponse(List<Entry> content, long total, int page, int size) {
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT')")
    public ResponseEntity<PageResponse> search(@RequestParam(required = false) AuditAction.Category category,
            @RequestParam(required = false) String q, @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(required = false) Long customerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        Page<AuditEntry> result = auditService.search(new AuditService.Filter(category, q, from, to, customerId), page, size);
        return ResponseEntity.ok(new PageResponse(result.getContent().stream().map(Entry::from).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize()));
    }
}

package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.entity.AuditAction;
import com.washpro.washpro_b2b.entity.AuditEntry;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.repository.AuditEntryRepository;
import com.washpro.washpro_b2b.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Nhật ký thay đổi về giá, tiền, hạn, hủy, khách hàng, tài khoản. Ghi trong cùng giao dịch với thao tác:
 * thao tác lỗi thì không có nhật ký, có nhật ký thì thao tác chắc chắn đã lưu.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AuditEntryRepository repository;
    private final CurrentUserService currentUserService;

    /** Ghi một thay đổi của người đang đăng nhập. */
    public void record(AuditAction action, Long entityId, Long customerId, String summary, Changes changes) {
        User user = currentUserService.getCurrentUser();
        String details = changes == null || changes.isEmpty() ? null : cut(changes.text(), 2000);
        repository.save(AuditEntry.builder()
                .actor(user.getUsername())
                .actorRole(user.getRole().name())
                .category(action.getCategory())
                .action(action)
                .entityId(entityId)
                .customerId(customerId)
                .summary(cut(summary, 300))
                .details(details)
                .build());
    }

    public record Filter(AuditAction.Category category, String keyword, LocalDate from, LocalDate to,
            Long customerId) {
    }

    @Transactional(readOnly = true)
    public Page<AuditEntry> search(Filter f, int page, int size) {
        if (size > 100) {
            throw ApiException.badRequest("Cỡ trang tối đa là 100");
        }
        Specification<AuditEntry> spec = (root, query, cb) -> cb.conjunction();
        if (f.category() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), f.category()));
        }
        if (f.customerId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("customerId"), f.customerId()));
        }
        if (f.from() != null) {
            LocalDateTime start = f.from().atStartOfDay();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
        }
        if (f.to() != null) {
            LocalDateTime end = f.to().plusDays(1).atStartOfDay();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), end));
        }
        if (f.keyword() != null && !f.keyword().isBlank()) {
            String p = "%" + f.keyword().trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                    .replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("summary")), p, '\\'),
                    cb.like(cb.lower(root.get("actor")), p, '\\'),
                    cb.like(cb.lower(root.get("details")), p, '\\')));
        }
        return repository.findAll(spec, PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    }

    // ===================== Ghi giá trị cũ → mới =====================

    /** Các thay đổi của một lần sửa; trường không đổi thì bỏ qua. */
    public static final class Changes {
        private final List<String> lines = new ArrayList<>();

        public Changes field(String label, Object before, Object after) {
            String b = show(before);
            String a = show(after);
            if (!Objects.equals(b, a)) {
                lines.add(label + ": " + b + " → " + a);
            }
            return this;
        }

        public Changes line(String text) {
            if (text != null && !text.isBlank()) {
                lines.add(text);
            }
            return this;
        }

        public boolean isEmpty() {
            return lines.isEmpty();
        }

        public String text() {
            return String.join("\n", lines);
        }
    }

    /** Hiển thị giá trị cho người đọc: tiền "12.000 đ", ngày giờ dd/MM/yyyy HH:mm, trống là "(trống)". */
    public static String show(Object v) {
        if (v == null || (v instanceof String s && s.isBlank())) {
            return "(trống)";
        }
        if (v instanceof BigDecimal d) {
            return money(d);
        }
        if (v instanceof LocalDateTime t) {
            return TS.format(t);
        }
        if (v instanceof LocalDate d) {
            return DateTimeFormatter.ofPattern("dd/MM/yyyy").format(d);
        }
        if (v instanceof Boolean b) {
            return b ? "có" : "không";
        }
        return v.toString();
    }

    public static String money(BigDecimal v) {
        return v == null ? "(trống)"
                : String.format(Locale.US, "%,.0f", v).replace(',', '.') + " đ";
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.entity.AttachmentKind;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderAttachment;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.repository.OrderAttachmentRepository;
import com.washpro.washpro_b2b.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ảnh / chữ ký đính kèm đơn (không bắt buộc): ai xem được đơn thì xem được ảnh;
 * ai được đính kèm loại nào xem AttachmentKind#allowedFor.
 */
@Service
@RequiredArgsConstructor
public class AttachmentService {

    /** Ảnh đã được giao diện thu nhỏ trước khi gửi; giới hạn này chặn file bất thường. */
    static final long MAX_BYTES = 8L * 1024 * 1024;
    static final int MAX_PER_ORDER = 30;
    /** Người tải lên được tự xóa ảnh của mình trong khoảng này (chụp nhầm); sau đó chỉ quản lý xưởng xóa được. */
    static final int SELF_DELETE_HOURS = 24;

    private final OrderAttachmentRepository attachmentRepository;
    private final OrderService orderService;
    private final FileStorage storage;
    private final CurrentUserService currentUserService;

    public record FileData(byte[] content, String contentType) {
    }

    @Transactional(readOnly = true)
    public List<OrderAttachment> list(Long orderId) {
        orderService.requireAccessible(orderId, currentUserService.getCurrentUser());
        return attachmentRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId);
    }

    @Transactional
    public OrderAttachment upload(Long orderId, AttachmentKind kind, MultipartFile file) {
        User user = currentUserService.getCurrentUser();
        Order order = orderService.requireAccessible(orderId, user);
        if (!kind.allowedFor(user.getRole())) {
            throw ApiException.forbidden("Bạn không được đính kèm \"" + kind.getLabel().toLowerCase() + "\"");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Chưa chọn ảnh");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("Ảnh quá lớn (tối đa 8 MB)");
        }
        if (attachmentRepository.countByOrderId(orderId) >= MAX_PER_ORDER) {
            throw ApiException.badRequest("Mỗi đơn đính kèm tối đa " + MAX_PER_ORDER + " ảnh");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Không đọc được ảnh gửi lên");
        }
        // Xác định loại ảnh theo nội dung file, không tin phần mở rộng / Content-Type do máy khách gửi
        ImageType type = ImageType.detect(content);
        if (type == null) {
            throw ApiException.badRequest("Chỉ nhận ảnh JPG, PNG hoặc WEBP");
        }

        String stored = storage.save("orders/" + orderId, content, type.extension);
        // Giao dịch lỗi (không ghi được CSDL): xóa file vừa ghi để không sót file mồ côi
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        storage.delete(stored);
                    }
                }
            });
        }
        return attachmentRepository.save(OrderAttachment.builder()
                .order(order)
                .kind(kind)
                .storedName(stored)
                .contentType(type.contentType)
                .sizeBytes(content.length)
                .uploadedBy(user.getUsername())
                .build());
    }

    @Transactional(readOnly = true)
    public FileData file(Long attachmentId) {
        OrderAttachment a = loadAccessible(attachmentId, currentUserService.getCurrentUser());
        return new FileData(storage.read(a.getStoredName()), a.getContentType());
    }

    @Transactional
    public void delete(Long attachmentId) {
        User user = currentUserService.getCurrentUser();
        OrderAttachment a = loadAccessible(attachmentId, user);
        boolean ownRecent = a.getUploadedBy().equals(user.getUsername())
                && a.getCreatedAt().isAfter(LocalDateTime.now().minusHours(SELF_DELETE_HOURS));
        if (user.getRole() != Role.WORKSHOP_ADMIN && !ownRecent) {
            throw ApiException.forbidden("Chỉ người tải lên (trong " + SELF_DELETE_HOURS
                    + " giờ) hoặc quản lý xưởng được xóa ảnh này");
        }
        attachmentRepository.delete(a);
        String stored = a.getStoredName();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(stored);
            }
        });
    }

    private OrderAttachment loadAccessible(Long attachmentId, User user) {
        OrderAttachment a = attachmentRepository.findWithOrderById(attachmentId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy ảnh"));
        // Không lộ ảnh của đơn ngoài phạm vi (trả 404 như đơn)
        orderService.requireAccessible(a.getOrder().getId(), user);
        return a;
    }

    /** Nhận dạng ảnh theo vài byte đầu file. */
    enum ImageType {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        final String contentType;
        final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        static ImageType detect(byte[] b) {
            if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
                return JPEG;
            }
            if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                    && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
                return PNG;
            }
            if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
                return WEBP;
            }
            return null;
        }
    }
}

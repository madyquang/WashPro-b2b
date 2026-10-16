package com.washpro.washpro_b2b.controller;

import com.washpro.washpro_b2b.entity.AttachmentKind;
import com.washpro.washpro_b2b.entity.OrderAttachment;
import com.washpro.washpro_b2b.service.AttachmentService;
import com.washpro.washpro_b2b.service.AttachmentService.FileData;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Ảnh / chữ ký đính kèm đơn. Quyền xem theo quyền xem đơn; quyền đính kèm theo loại (xem AttachmentKind). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    public record AttachmentResponse(Long id, AttachmentKind kind, String kindLabel, String contentType,
            long sizeBytes, String uploadedBy, LocalDateTime createdAt) {

        static AttachmentResponse from(OrderAttachment a) {
            return new AttachmentResponse(a.getId(), a.getKind(), a.getKind().getLabel(), a.getContentType(),
                    a.getSizeBytes(), a.getUploadedBy(), a.getCreatedAt());
        }
    }

    @GetMapping("/orders/{orderId}/attachments")
    public ResponseEntity<List<AttachmentResponse>> list(@PathVariable Long orderId) {
        return ResponseEntity.ok(attachmentService.list(orderId).stream().map(AttachmentResponse::from).toList());
    }

    @PostMapping(value = "/orders/{orderId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> upload(@PathVariable Long orderId, @RequestParam AttachmentKind kind,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AttachmentResponse.from(attachmentService.upload(orderId, kind, file)));
    }

    /** Nội dung ảnh. Trình duyệt tải bằng axios (kèm token) rồi hiển thị, không dùng thẳng trong thẻ img. */
    @GetMapping("/attachments/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        FileData data = attachmentService.file(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(data.contentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(data.content());
    }

    @DeleteMapping("/attachments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        attachmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package com.washpro.washpro_b2b.dto;

import com.washpro.washpro_b2b.entity.CustomerType;
import com.washpro.washpro_b2b.entity.ItemCategory;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderHistory;
import com.washpro.washpro_b2b.entity.OrderItem;
import com.washpro.washpro_b2b.entity.OrderNote;
import com.washpro.washpro_b2b.entity.OrderStatus;
import com.washpro.washpro_b2b.entity.PriceUnit;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.ServiceType;
import com.washpro.washpro_b2b.entity.TimeSlot;
import com.washpro.washpro_b2b.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class OrderDtos {

    private OrderDtos() {
    }

    // ===== Request =====

    /**
     * Một dòng đồ giặt. Chọn từ bảng giá (servicePriceId) để hệ thống tự điền nhóm, dịch vụ, đơn vị và giá;
     * hoặc nhập tay itemName cho đồ ngoài danh mục.
     */
    public record ItemRequest(
            Long servicePriceId,
            @Size(max = 100, message = "Tên đồ giặt tối đa 100 ký tự") String itemName,
            /** Chỉ dùng cho đồ nhập tay; mặc định "cái". */
            PriceUnit unit,
            @NotNull(message = "Vui lòng nhập số lượng") @DecimalMin(value = "0.01", message = "Số lượng phải lớn hơn 0") @DecimalMax(value = "100000", message = "Số lượng quá lớn") @Digits(integer = 6, fraction = 2, message = "Số lượng tối đa 2 chữ số thập phân") BigDecimal quantity,
            @DecimalMin(value = "0", message = "Đơn giá không được âm") @Digits(integer = 12, fraction = 2, message = "Đơn giá không hợp lệ") BigDecimal unitPrice) {
    }

    /** Yêu cầu lấy đồ: mở một đơn mới ở trạng thái "Chờ lấy đồ". */
    public record CreateRequest(
            /** Chỉ Quản lý xưởng cần gửi (tạo hộ khách gọi điện); tài khoản khách hàng tự lấy theo tài khoản. */
            Long customerId,
            @NotNull(message = "Vui lòng chọn ngày lấy đồ") @FutureOrPresent(message = "Ngày lấy đồ không được ở quá khứ") LocalDate preferredDate,
            TimeSlot timeSlot,
            @Min(value = 1, message = "Số túi tối thiểu là 1") @Max(value = 10000, message = "Số túi quá lớn") Integer estimatedBags,
            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) {
    }

    /** Tài xế (hoặc xưởng khi khách mang đồ đến) nhận đồ: số túi và số lượng từng loại đã kiểm cùng khách. */
    public record ReceiveRequest(
            @NotNull(message = "Vui lòng nhập số túi") @Min(value = 1, message = "Số túi tối thiểu là 1") @Max(value = 10000, message = "Số túi quá lớn") Integer totalBags,
            @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự") String description,
            @NotEmpty(message = "Cần đếm ít nhất một loại đồ") @Size(max = 200, message = "Tối đa 200 dòng chi tiết") @Valid List<ItemRequest> items) {
    }

    /** Quản lý xưởng phân công tài xế lấy đồ (đơn chờ lấy) hoặc giao trả (đơn đã giặt xong / đang giao). */
    public record AssignRequest(@NotNull(message = "Vui lòng chọn tài xế") Long driverId) {
    }

    /** Khách báo thiếu/hỏng khi nhận đồ sạch. */
    public record ReceiptIssueRequest(
            @NotBlank(message = "Vui lòng mô tả đồ bị thiếu hoặc hỏng") @Size(max = 500, message = "Mô tả tối đa 500 ký tự") String note) {
    }

    /** Quản lý xưởng đổi hạn trả đồ (khách đồng ý lùi hạn, đơn số lượng lớn...). */
    public record DueRequest(
            @NotNull(message = "Vui lòng chọn hạn trả mới") LocalDateTime dueAt,
            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) {
    }

    public record StatusRequest(
            @NotNull(message = "Vui lòng chọn trạng thái") OrderStatus status,
            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) {
    }

    public record ItemsRequest(
            @NotEmpty(message = "Cần có ít nhất một dòng đồ giặt") @Size(max = 200, message = "Tối đa 200 dòng chi tiết") @Valid List<ItemRequest> items) {
    }

    public record NoteRequest(
            @NotBlank(message = "Vui lòng nhập nội dung ghi chú") @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự") String body) {
    }

    /** Thứ tự danh sách đơn: mới nhận trước, hạn trả gần nhất trước, hoặc ngày hẹn lấy đồ gần nhất trước. */
    public enum Sort {
        NEWEST, DUE, PICKUP
    }

    /**
     * Bộ lọc danh sách đơn; mọi trường đều không bắt buộc.
     * mine: (tài xế) chỉ việc đang giao cho mình; cancelled: xem đơn / yêu cầu đã hủy thay vì đơn đang theo dõi.
     */
    public record Filter(List<OrderStatus> statuses, boolean overdueOnly, ItemCategory category, String keyword,
            boolean mine, boolean cancelled) {
    }

    // ===== Response =====

    /**
     * Một trang đơn hàng.
     * counts: số đơn theo trạng thái (áp mọi bộ lọc trừ trạng thái) để hiện trên các tab;
     * overdueCount: số đơn trễ hạn (áp mọi bộ lọc trừ trạng thái và "chỉ trễ hạn").
     */
    public record PageResponse(List<Response> content, long total, int page, int size,
            Map<OrderStatus, Long> counts, long overdueCount) {
    }

    public record ItemResponse(Long id, Long servicePriceId, String itemName, ItemCategory category,
            ServiceType serviceType, PriceUnit unit, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {

        public static ItemResponse from(OrderItem i) {
            BigDecimal line = i.getUnitPrice() == null ? null : i.getUnitPrice().multiply(i.getQuantity());
            Long priceId = i.getServicePrice() == null ? null : i.getServicePrice().getId();
            return new ItemResponse(i.getId(), priceId, i.getItemName(), i.getCategory(), i.getServiceType(),
                    i.getUnit(), i.getQuantity(), i.getUnitPrice(), line);
        }
    }

    public record HistoryResponse(OrderStatus fromStatus, OrderStatus toStatus, String actor, String note,
            LocalDateTime createdAt) {

        public static HistoryResponse from(OrderHistory h) {
            return new HistoryResponse(h.getFromStatus(), h.getToStatus(), h.getActor(), h.getNote(),
                    h.getCreatedAt());
        }
    }

    public record NoteResponse(Long id, String author, Role authorRole, String body, LocalDateTime createdAt) {

        public static NoteResponse from(OrderNote n) {
            return new NoteResponse(n.getId(), n.getAuthor(), n.getAuthorRole(), n.getBody(), n.getCreatedAt());
        }
    }

    public record Response(
            Long id,
            Long customerId,
            String customerName,
            CustomerType customerType,
            /** Địa chỉ, điện thoại, người liên hệ: để tài xế lấy đồ / giao trả. */
            String customerAddress,
            String customerPhone,
            String contactName,
            /** Tài xế lấy đồ; null khi chưa có ai nhận việc. */
            Long driverId,
            String driverName,
            String driverUsername,
            /** Tài xế giao trả; null khi chưa giao. */
            Long deliveryDriverId,
            String deliveryDriverName,
            String deliveryDriverUsername,
            OrderStatus status,
            /** Số túi thực tế khi nhận đồ; null khi chưa lấy đồ. */
            Integer totalBags,
            String description,
            /** Yêu cầu lấy đồ: người gửi, ngày / khung giờ hẹn, số túi ước tính, lời dặn tài xế. */
            String requestedBy,
            LocalDate preferredDate,
            TimeSlot timeSlot,
            Integer estimatedBags,
            String pickupNote,
            /** Đã qua ngày hẹn lấy mà chưa lấy. */
            boolean pickupLate,
            List<ItemResponse> items,
            /** Tổng tiền các dòng đã có đơn giá. */
            BigDecimal totalAmount,
            /** true khi đã có chi tiết và mọi dòng đều có đơn giá: đủ điều kiện giao trả/tính tiền. */
            boolean billable,
            /** Lúc tài xế nhận đồ. */
            LocalDateTime receivedAt,
            /** Hạn trả đồ sạch cho khách. */
            LocalDateTime dueAt,
            /** true khi đơn chưa hoàn thành mà đã quá hạn trả. */
            boolean overdue,
            LocalDateTime completedAt,
            /** Chỉ có với đơn / yêu cầu đã hủy. */
            String cancelReason,
            String cancelledBy,
            /** Lúc tạo đơn (khách gửi yêu cầu lấy đồ). */
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            /** Chỉ có khi xem chi tiết một đơn. */
            List<HistoryResponse> history,
            /** Ghi chú nội bộ: chỉ có khi phía xưởng / tài xế xem chi tiết một đơn. */
            List<NoteResponse> notes) {

        public static Response from(Order o, boolean withHistory) {
            return from(o, withHistory, null);
        }

        public static Response from(Order o, boolean withHistory, List<NoteResponse> notes) {
            List<ItemResponse> items = o.getItems().stream().map(ItemResponse::from).toList();
            var r = o.getCustomer();
            User d = o.getDriver();
            User dd = o.getDeliveryDriver();
            LocalDateTime now = LocalDateTime.now();
            return new Response(
                    o.getId(),
                    r.getId(),
                    r.getName(),
                    r.getCustomerType(),
                    r.getAddress(),
                    r.getPhone(),
                    r.getContactName(),
                    d == null ? null : d.getId(),
                    d == null ? null : d.getDisplayName(),
                    d == null ? null : d.getUsername(),
                    dd == null ? null : dd.getId(),
                    dd == null ? null : dd.getDisplayName(),
                    dd == null ? null : dd.getUsername(),
                    o.getStatus(),
                    o.getTotalBags(),
                    o.getDescription(),
                    o.getRequestedBy(),
                    o.getPreferredDate(),
                    o.getTimeSlot(),
                    o.getEstimatedBags(),
                    o.getPickupNote(),
                    !o.isDeleted() && o.isPickupLate(now.toLocalDate()),
                    items,
                    o.getTotalAmount(),
                    o.isBillable(),
                    o.getReceivedAt(),
                    o.getDueAt(),
                    !o.isDeleted() && o.isOverdue(now),
                    o.getStatus() == OrderStatus.COMPLETED ? o.getEffectiveCompletedAt() : null,
                    o.getCancelReason(),
                    o.getCancelledBy(),
                    o.getCreatedAt(),
                    o.getUpdatedAt(),
                    withHistory ? o.getHistory().stream().map(HistoryResponse::from).toList() : null,
                    notes);
        }
    }
}

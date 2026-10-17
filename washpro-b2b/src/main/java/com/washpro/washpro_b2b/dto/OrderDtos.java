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
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
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

    public record CreateRequest(
            @NotNull(message = "Vui lòng chọn khách hàng") Long customerId,
            @NotNull(message = "Vui lòng nhập số túi") @Min(value = 1, message = "Số túi tối thiểu là 1") @Max(value = 10000, message = "Số túi quá lớn") Integer totalBags,
            @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự") String description,
            @NotEmpty(message = "Cần đếm ít nhất một loại đồ") @Size(max = 200, message = "Tối đa 200 dòng chi tiết") @Valid List<ItemRequest> items,
            /** Tạo đơn từ yêu cầu lấy đồ của khách (không bắt buộc). */
            Long pickupRequestId) {
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

    /**
     * Số lượng thực tế của một dòng khi báo sai lệch.
     * Có itemId: sửa dòng đó (0 = không có món này). Không có itemId: thêm loại đồ bị sót.
     */
    public record ItemCountRequest(
            Long itemId,
            Long servicePriceId,
            @Size(max = 100, message = "Tên đồ giặt tối đa 100 ký tự") String itemName,
            PriceUnit unit,
            @NotNull(message = "Vui lòng nhập số lượng thực tế") @DecimalMin(value = "0", message = "Số lượng không được âm") @DecimalMax(value = "100000", message = "Số lượng quá lớn") @Digits(integer = 6, fraction = 2, message = "Số lượng tối đa 2 chữ số thập phân") BigDecimal quantity) {
    }

    public record DiscrepancyRequest(
            @NotNull(message = "Vui lòng nhập số túi thực tế") @Min(value = 1, message = "Số túi tối thiểu là 1") @Max(value = 10000, message = "Số túi quá lớn") Integer totalBags,
            @Size(max = 200, message = "Tối đa 200 dòng chi tiết") @Valid List<ItemCountRequest> items,
            @NotBlank(message = "Bắt buộc nhập lý do khi báo sai lệch") @Size(max = 500, message = "Lý do tối đa 500 ký tự") String note) {
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

    /** Thứ tự danh sách đơn: mới nhận trước, hoặc hạn trả gần nhất trước (đơn trễ hạn tự đứng đầu). */
    public enum Sort {
        NEWEST, DUE
    }

    /** Bộ lọc danh sách đơn; mọi trường đều không bắt buộc. */
    public record Filter(List<OrderStatus> statuses, boolean overdueOnly, ItemCategory category, String keyword) {
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
            /** Địa chỉ, điện thoại, người liên hệ: để tài xế giao trả. */
            String customerAddress,
            String customerPhone,
            String contactName,
            Long driverId,
            String driverName,
            OrderStatus status,
            Integer totalBags,
            String description,
            List<ItemResponse> items,
            /** Tổng tiền các dòng đã có đơn giá. */
            BigDecimal totalAmount,
            /** true khi đã có chi tiết và mọi dòng đều có đơn giá: đủ điều kiện giao trả/tính tiền. */
            boolean billable,
            /** Hạn trả đồ sạch cho khách. */
            LocalDateTime dueAt,
            /** true khi đơn chưa hoàn thành mà đã quá hạn trả. */
            boolean overdue,
            LocalDateTime completedAt,
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
            return new Response(
                    o.getId(),
                    r.getId(),
                    r.getName(),
                    r.getCustomerType(),
                    r.getAddress(),
                    r.getPhone(),
                    r.getContactName(),
                    o.getDriver().getId(),
                    o.getDriver().getDisplayName(),
                    o.getStatus(),
                    o.getTotalBags(),
                    o.getDescription(),
                    items,
                    o.getTotalAmount(),
                    o.isBillable(),
                    o.getDueAt(),
                    o.isOverdue(LocalDateTime.now()),
                    o.getStatus() == OrderStatus.COMPLETED ? o.getEffectiveCompletedAt() : null,
                    o.getCreatedAt(),
                    o.getUpdatedAt(),
                    withHistory ? o.getHistory().stream().map(HistoryResponse::from).toList() : null,
                    notes);
        }
    }
}

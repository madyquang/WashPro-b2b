package com.washpro.washpro_b2b.controller;

import com.washpro.washpro_b2b.dto.OrderDtos.AssignRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.CreateRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.DueRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.Filter;
import com.washpro.washpro_b2b.dto.OrderDtos.ItemsRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.NoteRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.NoteResponse;
import com.washpro.washpro_b2b.dto.OrderDtos.PageResponse;
import com.washpro.washpro_b2b.dto.OrderDtos.ReceiptIssueRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.ReceiveRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.Response;
import com.washpro.washpro_b2b.dto.OrderDtos.Sort;
import com.washpro.washpro_b2b.dto.OrderDtos.StatusRequest;
import com.washpro.washpro_b2b.entity.ItemCategory;
import com.washpro.washpro_b2b.entity.OrderStatus;
import com.washpro.washpro_b2b.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Đơn giặt từ lúc khách yêu cầu lấy đồ đến khi giao xong.
 * Phân quyền theo vai trò ở đây; phân quyền theo dữ liệu (đơn của ai) nằm trong OrderService.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Danh sách đơn trong phạm vi của người đăng nhập, lọc và phân trang phía máy chủ.
     * status nhận nhiều giá trị ngăn cách bằng dấu phẩy; page bắt đầu từ 0.
     * mine: (tài xế) chỉ việc đang giao cho mình; cancelled: đơn / yêu cầu đã hủy.
     */
    @GetMapping
    public ResponseEntity<PageResponse> list(
            @RequestParam(required = false) List<OrderStatus> status,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(required = false) ItemCategory category,
            @RequestParam(required = false) @Size(max = 100, message = "Từ khóa tối đa 100 ký tự") String q,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "false") boolean cancelled,
            @RequestParam(defaultValue = "NEWEST") Sort sort,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Trang không hợp lệ") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "Cỡ trang tối thiểu là 1") @Max(value = 100, message = "Cỡ trang tối đa là 100") int size) {
        return ResponseEntity.ok(orderService.searchOrders(new Filter(status, overdue, category, q, mine, cancelled),
                sort, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Response> get(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    /** Khách yêu cầu lấy đồ (Quản lý xưởng tạo hộ): mở đơn "Chờ lấy đồ". */
    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER_STAFF', 'CUSTOMER_MANAGER', 'WORKSHOP_ADMIN')")
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request));
    }

    /** Tài xế nhận việc đang chờ: đi lấy đồ hoặc đi giao trả. */
    @PutMapping("/{id}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Response> accept(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.accept(id));
    }

    /** Tài xế trả lại việc mình đang nhận. */
    @PutMapping("/{id}/release")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Response> release(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.release(id));
    }

    /** Quản lý xưởng phân công tài xế lấy đồ / giao trả. */
    @PutMapping("/{id}/assign")
    @PreAuthorize("hasRole('WORKSHOP_ADMIN')")
    public ResponseEntity<Response> assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request) {
        return ResponseEntity.ok(orderService.assign(id, request.driverId()));
    }

    /** Nhận đồ: tài xế kiểm số lượng cùng khách (đã đính kèm chữ ký / ảnh), hoặc xưởng nhận đồ khách mang đến. */
    @PutMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('DRIVER', 'WORKSHOP_ADMIN')")
    public ResponseEntity<Response> receive(@PathVariable Long id, @Valid @RequestBody ReceiveRequest request) {
        return ResponseEntity.ok(orderService.receive(id, request));
    }

    /** Tài xế giao xong (đã đính kèm chữ ký / ảnh lúc giao): đơn hoàn thành. */
    @PutMapping("/{id}/delivered")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Response> delivered(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.delivered(id));
    }

    /** Khách hàng xác nhận đã nhận đủ đồ sạch: đơn hoàn thành. */
    @PutMapping("/{id}/confirm-receipt")
    @PreAuthorize("hasAnyRole('CUSTOMER_STAFF', 'CUSTOMER_MANAGER')")
    public ResponseEntity<Response> confirmReceipt(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.confirmReceipt(id));
    }

    /** Khách hàng báo thiếu/hỏng khi nhận đồ sạch. */
    @PutMapping("/{id}/receipt-issue")
    @PreAuthorize("hasAnyRole('CUSTOMER_STAFF', 'CUSTOMER_MANAGER')")
    public ResponseEntity<Response> reportReceiptIssue(@PathVariable Long id,
            @Valid @RequestBody ReceiptIssueRequest request) {
        return ResponseEntity.ok(orderService.reportReceiptIssue(id, request));
    }

    /** Quản lý xưởng đổi hạn trả đồ. */
    @PutMapping("/{id}/due")
    @PreAuthorize("hasRole('WORKSHOP_ADMIN')")
    public ResponseEntity<Response> updateDue(@PathVariable Long id, @Valid @RequestBody DueRequest request) {
        return ResponseEntity.ok(orderService.updateDueDate(id, request));
    }

    /** Quản lý xưởng: bắt đầu giặt, đã giặt xong, giao bù, chốt hoàn thành. */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('WORKSHOP_ADMIN')")
    public ResponseEntity<Response> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return ResponseEntity.ok(orderService.updateStatus(id, request));
    }

    /** Nhập chi tiết đồ giặt và đơn giá. */
    @PutMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT')")
    public ResponseEntity<Response> updateItems(@PathVariable Long id, @Valid @RequestBody ItemsRequest request) {
        return ResponseEntity.ok(orderService.updateItems(id, request));
    }

    /** Thêm ghi chú nội bộ; trả về toàn bộ ghi chú của đơn. */
    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'DRIVER')")
    public ResponseEntity<List<NoteResponse>> addNote(@PathVariable Long id, @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.addNote(id, request.body()));
    }

    /**
     * Hủy (xóa mềm, dữ liệu vẫn được giữ để đối soát). Khách hủy được yêu cầu của mình khi tài xế chưa lấy đồ;
     * Quản lý xưởng hủy được mọi đơn chưa hoàn thành.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('WORKSHOP_ADMIN', 'CUSTOMER_STAFF', 'CUSTOMER_MANAGER')")
    public ResponseEntity<Response> cancel(@PathVariable Long id,
            @RequestParam(required = false) @Size(max = 500, message = "Lý do tối đa 500 ký tự") String reason) {
        return ResponseEntity.ok(orderService.cancelOrder(id, reason));
    }
}

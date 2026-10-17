package com.washpro.washpro_b2b.controller;

import com.washpro.washpro_b2b.dto.OrderDtos.CreateRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.DiscrepancyRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.DueRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.Filter;
import com.washpro.washpro_b2b.dto.OrderDtos.PageResponse;
import com.washpro.washpro_b2b.dto.OrderDtos.ReceiptIssueRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.ItemsRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.NoteRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.NoteResponse;
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
     */
    @GetMapping
    public ResponseEntity<PageResponse> list(
            @RequestParam(required = false) List<OrderStatus> status,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(required = false) ItemCategory category,
            @RequestParam(required = false) @Size(max = 100, message = "Từ khóa tối đa 100 ký tự") String q,
            @RequestParam(defaultValue = "NEWEST") Sort sort,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Trang không hợp lệ") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "Cỡ trang tối thiểu là 1") @Max(value = 100, message = "Cỡ trang tối đa là 100") int size) {
        return ResponseEntity.ok(orderService.searchOrders(new Filter(status, overdue, category, q), sort, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Response> get(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    /** Khách hàng xác nhận số lượng: đơn chuyển sang xưởng giặt. */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('CUSTOMER_STAFF', 'CUSTOMER_MANAGER')")
    public ResponseEntity<Response> approve(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.approveOrder(id));
    }

    /** Tài xế (đơn của mình) hoặc khách hàng (đơn của khách hàng) báo lệch số lượng. */
    @PutMapping("/{id}/discrepancy")
    @PreAuthorize("hasAnyRole('DRIVER', 'CUSTOMER_STAFF', 'CUSTOMER_MANAGER')")
    public ResponseEntity<Response> reportDiscrepancy(@PathVariable Long id,
            @Valid @RequestBody DiscrepancyRequest request) {
        return ResponseEntity.ok(orderService.reportDiscrepancy(id, request));
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

    /** Quản lý xưởng điều phối: mở lại chờ xác nhận, giao hàng, giao bù, chốt hoàn thành. */
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

    /** Hủy đơn (xóa mềm, dữ liệu vẫn được giữ để đối soát). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('WORKSHOP_ADMIN')")
    public ResponseEntity<Response> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(orderService.cancelOrder(id, reason));
    }
}

package com.washpro.washpro_b2b.controller;

import com.washpro.washpro_b2b.dto.OrderDtos.CreateRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.DiscrepancyRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.DueRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.ReceiptIssueRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.ItemsRequest;
import com.washpro.washpro_b2b.dto.OrderDtos.Response;
import com.washpro.washpro_b2b.dto.OrderDtos.StatusRequest;
import com.washpro.washpro_b2b.entity.OrderStatus;
import com.washpro.washpro_b2b.service.OrderService;
import jakarta.validation.Valid;
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

    /** Danh sách đơn trong phạm vi của người đăng nhập, có thể lọc theo trạng thái. */
    @GetMapping
    public ResponseEntity<List<Response>> list(@RequestParam(required = false) OrderStatus status) {
        return ResponseEntity.ok(orderService.listOrders(status));
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
    @PreAuthorize("hasAnyRole('RESTAURANT_STAFF', 'RESTAURANT_MANAGER')")
    public ResponseEntity<Response> approve(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.approveOrder(id));
    }

    /** Tài xế (đơn của mình) hoặc khách hàng (đơn của khách hàng) báo lệch số lượng. */
    @PutMapping("/{id}/discrepancy")
    @PreAuthorize("hasAnyRole('DRIVER', 'RESTAURANT_STAFF', 'RESTAURANT_MANAGER')")
    public ResponseEntity<Response> reportDiscrepancy(@PathVariable Long id,
            @Valid @RequestBody DiscrepancyRequest request) {
        return ResponseEntity.ok(orderService.reportDiscrepancy(id, request));
    }

    /** Khách hàng xác nhận đã nhận đủ đồ sạch: đơn hoàn thành. */
    @PutMapping("/{id}/confirm-receipt")
    @PreAuthorize("hasAnyRole('RESTAURANT_STAFF', 'RESTAURANT_MANAGER')")
    public ResponseEntity<Response> confirmReceipt(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.confirmReceipt(id));
    }

    /** Khách hàng báo thiếu/hỏng khi nhận đồ sạch. */
    @PutMapping("/{id}/receipt-issue")
    @PreAuthorize("hasAnyRole('RESTAURANT_STAFF', 'RESTAURANT_MANAGER')")
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

    /** Hủy đơn (xóa mềm, dữ liệu vẫn được giữ để đối soát). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('WORKSHOP_ADMIN')")
    public ResponseEntity<Response> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(orderService.cancelOrder(id, reason));
    }
}

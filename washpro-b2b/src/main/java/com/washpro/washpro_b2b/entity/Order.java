package com.washpro.washpro_b2b.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tên thực thể đặt là "LaundryOrder" để tránh trùng từ khóa ORDER trong JPQL;
 * bảng trong CSDL vẫn là "orders".
 */
@Entity(name = "LaundryOrder")
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    // Lưu dạng VARCHAR để thêm trạng thái mới không bị vướng kiểu ENUM của MySQL
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_bags")
    private Integer totalBags;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Xóa mềm: đơn bị hủy vẫn giữ trong CSDL để đối soát. */
    @Column(name = "is_deleted")
    @Builder.Default
    private boolean isDeleted = false;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    /** Nhật ký thay đổi trạng thái/số lượng: phục vụ đối soát, không bao giờ sửa hay xóa. */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("createdAt ASC, id ASC")
    @Builder.Default
    private List<OrderHistory> history = new ArrayList<>();

    /** Hạn trả đồ sạch cho khách: lúc nhận đồ + thời gian trả đồ đã cam kết với khách. */
    @Column(name = "due_at")
    private LocalDateTime dueAt;

    /** Lúc khách xác nhận đã nhận đủ (hoặc xưởng chốt): mốc tính hạn thanh toán. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Đơn chưa hoàn thành mà đã quá hạn trả đồ. */
    public boolean isOverdue(LocalDateTime now) {
        return status.isOpen() && dueAt != null && now.isAfter(dueAt);
    }

    /** Thời điểm hoàn thành; đơn cũ trước khi có cột completed_at thì lấy lần cập nhật cuối. */
    public LocalDateTime getEffectiveCompletedAt() {
        return completedAt != null ? completedAt : updatedAt;
    }

    /** Tổng tiền các dòng đã có đơn giá. */
    public BigDecimal getTotalAmount() {
        return items.stream()
                .filter(i -> i.getUnitPrice() != null)
                .map(i -> i.getUnitPrice().multiply(i.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Đủ điều kiện tính tiền: có chi tiết và mọi dòng đều có đơn giá. */
    public boolean isBillable() {
        return !items.isEmpty() && items.stream().allMatch(i -> i.getUnitPrice() != null);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    public void addHistory(OrderStatus from, OrderStatus to, String actor, String note) {
        history.add(OrderHistory.builder()
                .order(this)
                .fromStatus(from)
                .toStatus(to)
                .actor(actor)
                .note(note)
                .build());
    }
}

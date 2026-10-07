package com.washpro.washpro_b2b.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * Một dòng đồ giặt trong đơn. Tên, nhóm, dịch vụ, đơn vị và đơn giá được chép lại từ bảng giá
 * tại thời điểm nhập, để sửa bảng giá sau này không làm thay đổi đơn cũ.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Mục bảng giá gốc; null nếu là đồ ngoài danh mục (nhập tay). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_price_id")
    private ServicePrice servicePrice;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30, columnDefinition = "varchar(30) default 'OTHER'")
    @Builder.Default
    private ItemCategory category = ItemCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "service_type", nullable = false, length = 30, columnDefinition = "varchar(30) default 'WASH'")
    @Builder.Default
    private ServiceType serviceType = ServiceType.WASH;

    @Convert(converter = PriceUnitConverter.class)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'PIECE'")
    @Builder.Default
    private PriceUnit unit = PriceUnit.PIECE;

    /** Số cái/bộ/đôi, hoặc số kg (được lẻ 2 chữ số) khi tính theo cân. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    /** Đơn giá tại thời điểm chốt đơn (lưu lại để đổi bảng giá sau này không ảnh hưởng đơn cũ). */
    @Column(name = "unit_price", precision = 15, scale = 2)
    private BigDecimal unitPrice;

    /** Hiển thị ngắn gọn cho nhật ký: "Ga giường đôi (Giặt + là ủi) 20 cái". */
    public String describe() {
        return itemName + " (" + serviceType.getLabel() + ") " + quantity.stripTrailingZeros().toPlainString()
                + " " + unit.getLabel();
    }
}

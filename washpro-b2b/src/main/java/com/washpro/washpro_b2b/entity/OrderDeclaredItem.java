package com.washpro.washpro_b2b.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/** Một dòng khách tự khai khi gửi yêu cầu lấy đồ (không có giá, không sửa sau khi gửi). */
@Entity
@Table(name = "order_declared_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDeclaredItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Order order;

    /** Mục bảng giá; null nếu khách gõ tên đồ ngoài danh mục. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_price_id")
    private ServicePrice servicePrice;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "service_type", nullable = false, length = 30)
    @Builder.Default
    private ServiceType serviceType = ServiceType.WASH;

    @Convert(converter = PriceUnitConverter.class)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PriceUnit unit = PriceUnit.PIECE;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    /** Khóa so khớp với dòng thực nhận: cùng mục bảng giá, hoặc cùng tên + đơn vị với đồ nhập tay. */
    public String matchKey() {
        return servicePrice != null ? "p" + servicePrice.getId() : "n" + itemName.trim().toLowerCase() + "|" + unit;
    }
}

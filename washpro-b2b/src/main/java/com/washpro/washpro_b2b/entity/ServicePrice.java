package com.washpro.washpro_b2b.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * Một mục trong bảng giá chung: loại đồ + dịch vụ + đơn vị tính + đơn giá.
 * Ví dụ: "Ga giường đôi · Giặt + là ủi · 18.000đ/cái", "Chăn ga (tính cân) · Giặt sấy · 25.000đ/kg".
 * Cùng một tên đồ được có nhiều dòng nếu khác dịch vụ.
 */
@Entity
@Table(name = "service_prices", uniqueConstraints = @UniqueConstraint(
        name = "uk_service_prices_name_service", columnNames = { "name", "service_type" }))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServicePrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

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
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PriceUnit unit = PriceUnit.PIECE;

    /** Giá chung. Khách hàng có giá hợp đồng riêng thì dùng CustomerPrice. */
    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean active = true;
}

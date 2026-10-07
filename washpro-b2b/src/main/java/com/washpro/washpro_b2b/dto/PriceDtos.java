package com.washpro.washpro_b2b.dto;

import com.washpro.washpro_b2b.entity.CustomerPrice;
import com.washpro.washpro_b2b.entity.ItemCategory;
import com.washpro.washpro_b2b.entity.PriceUnit;
import com.washpro.washpro_b2b.entity.ServicePrice;
import com.washpro.washpro_b2b.entity.ServiceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class PriceDtos {

    private PriceDtos() {
    }

    public record Request(
            @NotBlank(message = "Vui lòng nhập tên loại đồ") @Size(max = 100, message = "Tên loại đồ tối đa 100 ký tự") String name,
            @NotNull(message = "Vui lòng chọn nhóm đồ") ItemCategory category,
            @NotNull(message = "Vui lòng chọn dịch vụ") ServiceType serviceType,
            @NotNull(message = "Vui lòng chọn đơn vị tính") PriceUnit unit,
            @NotNull(message = "Vui lòng nhập đơn giá") @DecimalMin(value = "0", message = "Đơn giá không được âm") @Digits(integer = 12, fraction = 2, message = "Đơn giá không hợp lệ") BigDecimal unitPrice) {
    }

    public record Response(Long id, String name, ItemCategory category, ServiceType serviceType, PriceUnit unit,
            BigDecimal unitPrice, boolean active) {

        public static Response from(ServicePrice p) {
            return new Response(p.getId(), p.getName(), p.getCategory(), p.getServiceType(), p.getUnit(),
                    p.getUnitPrice(), p.isActive());
        }
    }

    /** Danh mục đồ giặt không kèm giá: cho tài xế và khách hàng chọn khi đếm đồ. */
    public record CatalogItem(Long id, String name, ItemCategory category, ServiceType serviceType, PriceUnit unit) {

        public static CatalogItem from(ServicePrice p) {
            return new CatalogItem(p.getId(), p.getName(), p.getCategory(), p.getServiceType(), p.getUnit());
        }
    }

    /** Bảng giá áp dụng cho một khách hàng: giá chung, giá riêng (nếu có) và giá thực tính. */
    public record CustomerPriceRow(
            Long servicePriceId,
            String name,
            ItemCategory category,
            ServiceType serviceType,
            PriceUnit unit,
            boolean active,
            BigDecimal basePrice,
            BigDecimal customPrice,
            BigDecimal effectivePrice) {

        public static CustomerPriceRow of(ServicePrice p, CustomerPrice custom) {
            BigDecimal customPrice = custom == null ? null : custom.getUnitPrice();
            return new CustomerPriceRow(p.getId(), p.getName(), p.getCategory(), p.getServiceType(), p.getUnit(),
                    p.isActive(), p.getUnitPrice(), customPrice, customPrice != null ? customPrice : p.getUnitPrice());
        }
    }

    public record CustomerPriceRequest(
            @NotNull(message = "Vui lòng nhập đơn giá") @DecimalMin(value = "0", message = "Đơn giá không được âm") @Digits(integer = 12, fraction = 2, message = "Đơn giá không hợp lệ") BigDecimal unitPrice) {
    }
}

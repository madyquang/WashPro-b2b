package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.PriceDtos.CatalogItem;
import com.washpro.washpro_b2b.dto.PriceDtos.CustomerPriceRow;
import com.washpro.washpro_b2b.dto.PriceDtos.Request;
import com.washpro.washpro_b2b.dto.PriceDtos.Response;
import com.washpro.washpro_b2b.entity.CustomerPrice;
import com.washpro.washpro_b2b.entity.AuditAction;
import com.washpro.washpro_b2b.service.AuditService.Changes;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.ServicePrice;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.repository.CustomerPriceRepository;
import com.washpro.washpro_b2b.repository.CustomerRepository;
import com.washpro.washpro_b2b.repository.ServicePriceRepository;
import com.washpro.washpro_b2b.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bảng giá chung do Kế toán / Quản lý xưởng cấu hình, cộng giá hợp đồng riêng theo từng khách hàng.
 * Giá thực tính cho một khách = giá riêng nếu có, ngược lại là giá chung.
 */
@Service
@RequiredArgsConstructor
public class PriceService {

    /** Sắp theo nhóm đồ (đúng thứ tự khai báo), rồi tên, rồi dịch vụ. */
    private static final Comparator<ServicePrice> CATALOG_ORDER = Comparator
            .comparing((ServicePrice p) -> p.getCategory().ordinal())
            .thenComparing(p -> p.getName().toLowerCase())
            .thenComparing(p -> p.getServiceType().ordinal());

    private final ServicePriceRepository priceRepository;
    private final CustomerPriceRepository customerPriceRepository;
    private final CustomerRepository customerRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    // ===================== Bảng giá chung =====================

    @Transactional(readOnly = true)
    public List<Response> list(boolean activeOnly) {
        List<ServicePrice> prices = activeOnly ? priceRepository.findByActiveTrue() : priceRepository.findAll();
        return prices.stream().sorted(CATALOG_ORDER).map(Response::from).toList();
    }

    /** Danh mục đang áp dụng, không có giá (giá là thông tin nội bộ của xưởng). */
    @Transactional(readOnly = true)
    public List<CatalogItem> catalog() {
        return priceRepository.findByActiveTrue().stream().sorted(CATALOG_ORDER).map(CatalogItem::from).toList();
    }

    @Transactional
    public Response create(Request request) {
        String name = request.name().trim();
        assertUnique(name, request, null);
        ServicePrice price = ServicePrice.builder()
                .name(name)
                .category(request.category())
                .serviceType(request.serviceType())
                .unit(request.unit())
                .unitPrice(money(request.unitPrice()))
                .build();
        ServicePrice saved = priceRepository.save(price);
        auditService.record(AuditAction.PRICE_CREATED, saved.getId(), null,
                "Thêm \"" + label(saved) + "\": " + AuditService.money(saved.getUnitPrice()), null);
        return Response.from(saved);
    }

    @Transactional
    public Response update(Long id, Request request) {
        ServicePrice price = find(id);
        String name = request.name().trim();
        assertUnique(name, request, id);
        Changes changes = new Changes()
                .field("Tên", price.getName(), name)
                .field("Nhóm đồ", price.getCategory().getLabel(), request.category().getLabel())
                .field("Dịch vụ", price.getServiceType().getLabel(), request.serviceType().getLabel())
                .field("Đơn vị", price.getUnit().getLabel(), request.unit().getLabel())
                .field("Đơn giá chung", price.getUnitPrice(), money(request.unitPrice()));
        price.setName(name);
        price.setCategory(request.category());
        price.setServiceType(request.serviceType());
        price.setUnit(request.unit());
        price.setUnitPrice(money(request.unitPrice()));
        if (!changes.isEmpty()) {
            auditService.record(AuditAction.PRICE_UPDATED, id, null, "Sửa \"" + label(price) + "\"", changes);
        }
        return Response.from(priceRepository.save(price));
    }

    @Transactional
    public Response setActive(Long id, boolean active) {
        ServicePrice price = find(id);
        if (price.isActive() != active) {
            auditService.record(active ? AuditAction.PRICE_SHOWN : AuditAction.PRICE_HIDDEN, id, null,
                    (active ? "Áp dụng lại \"" : "Ẩn \"") + label(price) + "\"", null);
        }
        price.setActive(active);
        return Response.from(priceRepository.save(price));
    }

    /** Tên mục giá cho nhật ký, VD "Khăn bàn · Giặt sấy (cái)". */
    private static String label(ServicePrice p) {
        return p.getName() + " · " + p.getServiceType().getLabel() + " (" + p.getUnit().getLabel() + ")";
    }

    // ===================== Giá riêng theo khách hàng =====================

    /** Toàn bộ bảng giá (kể cả mục đã ẩn) kèm giá riêng của khách hàng này. */
    @Transactional(readOnly = true)
    public List<CustomerPriceRow> customerPrices(Long customerId) {
        findCustomer(customerId);
        Map<Long, CustomerPrice> custom = customByServicePrice(customerId);
        return priceRepository.findAll().stream()
                .sorted(CATALOG_ORDER)
                .map(p -> CustomerPriceRow.of(p, custom.get(p.getId())))
                .toList();
    }

    @Transactional
    public CustomerPriceRow setCustomerPrice(Long customerId, Long servicePriceId, BigDecimal unitPrice) {
        Customer customer = findCustomer(customerId);
        ServicePrice price = find(servicePriceId);
        CustomerPrice custom = customerPriceRepository
                .findByCustomerIdAndServicePriceId(customerId, servicePriceId)
                .orElseGet(() -> CustomerPrice.builder().customer(customer).servicePrice(price).build());
        BigDecimal before = custom.getUnitPrice();
        custom.setUnitPrice(money(unitPrice));
        custom.setUpdatedBy(currentUserService.getCurrentUser().getUsername());
        if (before == null || before.compareTo(custom.getUnitPrice()) != 0) {
            auditService.record(AuditAction.CUSTOMER_PRICE_SET, price.getId(), customerId,
                    "Giá riêng \"" + label(price) + "\" cho " + customer.getName(),
                    new Changes().field("Đơn giá", before != null ? before : "giá chung " + AuditService.money(price.getUnitPrice()),
                            custom.getUnitPrice()));
        }
        return CustomerPriceRow.of(price, customerPriceRepository.save(custom));
    }

    /** Bỏ giá riêng: khách hàng quay về dùng giá chung. */
    @Transactional
    public CustomerPriceRow removeCustomerPrice(Long customerId, Long servicePriceId) {
        Customer customer = findCustomer(customerId);
        ServicePrice price = find(servicePriceId);
        customerPriceRepository.findByCustomerIdAndServicePriceId(customerId, servicePriceId).ifPresent(c -> {
            auditService.record(AuditAction.CUSTOMER_PRICE_REMOVED, price.getId(), customerId,
                    "Bỏ giá riêng \"" + label(price) + "\" của " + customer.getName(),
                    new Changes().field("Đơn giá", c.getUnitPrice(), "giá chung " + AuditService.money(price.getUnitPrice())));
            customerPriceRepository.delete(c);
        });
        return CustomerPriceRow.of(price, null);
    }

    /**
     * Hàm tính giá thực của từng mục bảng giá cho một khách hàng (dùng khi chốt giá dòng đồ giặt).
     * Giá riêng được nạp một lần, tránh truy vấn lặp cho mỗi dòng.
     */
    @Transactional(readOnly = true)
    public Function<ServicePrice, BigDecimal> pricerFor(Long customerId) {
        Map<Long, BigDecimal> custom = customByServicePrice(customerId).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getUnitPrice()));
        return price -> custom.getOrDefault(price.getId(), price.getUnitPrice());
    }

    // ===================== Hàm dùng nội bộ =====================

    ServicePrice find(Long id) {
        return priceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy mục trong bảng giá"));
    }

    private Customer findCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
    }

    private Map<Long, CustomerPrice> customByServicePrice(Long customerId) {
        return customerPriceRepository.findByCustomerId(customerId).stream()
                .collect(Collectors.toMap(c -> c.getServicePrice().getId(), Function.identity()));
    }

    private void assertUnique(String name, Request request, Long selfId) {
        priceRepository.findByNameIgnoreCaseAndServiceType(name, request.serviceType()).ifPresent(other -> {
            if (!other.getId().equals(selfId)) {
                throw ApiException.conflict("Bảng giá đã có \"" + name + "\" với dịch vụ \""
                        + request.serviceType().getLabel() + "\"");
            }
        });
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}

package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.CustomerDtos.Request;
import com.washpro.washpro_b2b.dto.CustomerDtos.Response;
import com.washpro.washpro_b2b.entity.CustomerType;
import com.washpro.washpro_b2b.entity.AuditAction;
import com.washpro.washpro_b2b.service.AuditService.Changes;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.repository.CustomerRepository;
import com.washpro.washpro_b2b.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Khách hàng của xưởng: nhà hàng, khách sạn, spa, công ty... */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    /** Tài xế chỉ thấy khách hàng đang hợp tác (để chọn khi tạo đơn); các vai trò xưởng thấy tất cả. */
    @Transactional(readOnly = true)
    public List<Response> list() {
        boolean driver = currentUserService.getCurrentUser().getRole() == Role.DRIVER;
        List<Customer> customers = driver
                ? customerRepository.findByActiveTrueOrderByNameAsc()
                : customerRepository.findAllByOrderByNameAsc();
        return customers.stream().map(Response::from).toList();
    }

    @Transactional
    public Response create(Request request) {
        String name = request.name().trim();
        if (customerRepository.findByNameIgnoreCase(name).isPresent()) {
            throw ApiException.conflict("Đã có khách hàng trùng tên này");
        }
        Customer customer = Customer.builder().name(name).build();
        apply(customer, request);
        Customer saved = customerRepository.save(customer);
        auditService.record(AuditAction.CUSTOMER_CREATED, saved.getId(), saved.getId(), "Thêm khách hàng " + saved.getName(),
                new Changes().line("Kỳ thanh toán: " + saved.getPaymentTermDays() + " ngày, thời gian trả đồ: "
                        + saved.getTurnaroundHours() + " giờ"));
        return Response.from(saved);
    }

    @Transactional
    public Response update(Long id, Request request) {
        Customer customer = find(id);
        String name = request.name().trim();
        customerRepository.findByNameIgnoreCase(name).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw ApiException.conflict("Đã có khách hàng trùng tên này");
            }
        });
        String oldName = customer.getName();
        String oldType = customer.getCustomerType().getLabel();
        String oldAddress = customer.getAddress();
        String oldPhone = customer.getPhone();
        String oldContact = customer.getContactName();
        int oldTurnaround = customer.getTurnaroundHours();
        int oldTerm = customer.getPaymentTermDays();
        customer.setName(name);
        apply(customer, request);
        Changes changes = new Changes()
                .field("Tên", oldName, customer.getName())
                .field("Loại khách", oldType, customer.getCustomerType().getLabel())
                .field("Địa chỉ", oldAddress, customer.getAddress())
                .field("Điện thoại", oldPhone, customer.getPhone())
                .field("Người liên hệ", oldContact, customer.getContactName())
                .field("Thời gian trả đồ (giờ)", oldTurnaround, customer.getTurnaroundHours())
                .field("Kỳ thanh toán (ngày)", oldTerm, customer.getPaymentTermDays());
        if (!changes.isEmpty()) {
            auditService.record(AuditAction.CUSTOMER_UPDATED, id, id, "Sửa thông tin khách hàng " + customer.getName(), changes);
        }
        return Response.from(customerRepository.save(customer));
    }

    /** Chép thông tin từ form; thời gian trả đồ / kỳ thanh toán không gửi thì giữ mặc định hoặc giá trị cũ. */
    private static void apply(Customer customer, Request request) {
        customer.setCustomerType(typeOrDefault(request.customerType()));
        customer.setAddress(request.address().trim());
        customer.setPhone(blankToNull(request.phone()));
        customer.setContactName(blankToNull(request.contactName()));
        if (request.turnaroundHours() != null) {
            customer.setTurnaroundHours(request.turnaroundHours());
        }
        if (request.paymentTermDays() != null) {
            customer.setPaymentTermDays(request.paymentTermDays());
        }
    }

    @Transactional
    public Response setActive(Long id, boolean active) {
        Customer customer = find(id);
        if (customer.isActive() != active) {
            auditService.record(active ? AuditAction.CUSTOMER_REACTIVATED : AuditAction.CUSTOMER_DEACTIVATED, id, id,
                    (active ? "Hợp tác lại với " : "Ngừng hợp tác với ") + customer.getName(), null);
        }
        customer.setActive(active);
        return Response.from(customerRepository.save(customer));
    }

    private Customer find(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
    }

    private static CustomerType typeOrDefault(CustomerType type) {
        return type == null ? CustomerType.RESTAURANT : type;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}

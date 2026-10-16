package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.PickupDtos.CreateRequest;
import com.washpro.washpro_b2b.dto.PickupDtos.Response;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.PickupRequest;
import com.washpro.washpro_b2b.entity.PickupStatus;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.TimeSlot;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.repository.PickupRequestRepository;
import com.washpro.washpro_b2b.repository.CustomerRepository;
import com.washpro.washpro_b2b.repository.UserRepository;
import com.washpro.washpro_b2b.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Yêu cầu lấy đồ:
 * - Khách hàng tạo/hủy yêu cầu của chính mình; Quản lý xưởng tạo hộ khi khách gọi điện.
 * - Tài xế thấy mọi yêu cầu chưa ai nhận và yêu cầu của mình; nhận việc hoặc trả lại.
 * - Quản lý xưởng phân công tài xế. Yêu cầu hoàn tất khi tài xế tạo đơn từ nó (OrderService).
 */
@Service
@RequiredArgsConstructor
public class PickupService {

    private final PickupRequestRepository pickupRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;

    @Transactional
    public Response create(CreateRequest request) {
        User user = currentUserService.getCurrentUser();
        Customer customer;
        if (user.getRole().isCustomerRole()) {
            customer = requireCustomer(user);
        } else if (user.getRole() == Role.WORKSHOP_ADMIN) {
            if (request.customerId() == null) {
                throw ApiException.badRequest("Vui lòng chọn khách hàng");
            }
            customer = customerRepository.findById(request.customerId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        } else {
            throw ApiException.forbidden("Bạn không có quyền tạo yêu cầu lấy đồ");
        }
        if (!customer.isActive()) {
            throw ApiException.badRequest("Khách hàng này đã ngừng hợp tác");
        }

        PickupRequest pickup = PickupRequest.builder()
                .customer(customer)
                .requestedBy(user.getUsername())
                .preferredDate(request.preferredDate())
                .timeSlot(request.timeSlot() != null ? request.timeSlot() : TimeSlot.ANYTIME)
                .estimatedBags(request.estimatedBags())
                .note(blankToNull(request.note()))
                .build();
        PickupRequest saved = pickupRepository.save(pickup);
        notificationService.pickupCreated(saved, user);
        return Response.from(saved);
    }

    @Transactional(readOnly = true)
    public List<Response> list() {
        User user = currentUserService.getCurrentUser();
        List<PickupRequest> rows = switch (user.getRole()) {
            case WORKSHOP_ADMIN, WORKSHOP_ACCOUNTANT -> pickupRepository.findAllByOrderByPreferredDateDescIdDesc();
            case DRIVER -> pickupRepository.findForDriver(user.getId());
            case CUSTOMER_STAFF, CUSTOMER_MANAGER ->
                pickupRepository.findByCustomerIdOrderByPreferredDateDescIdDesc(requireCustomer(user).getId());
        };
        return rows.stream().map(Response::from).toList();
    }

    /** Tài xế nhận việc. */
    @Transactional
    public Response accept(Long id) {
        User driver = currentUserService.getCurrentUser();
        PickupRequest pickup = loadForUpdate(id);
        if (pickup.getStatus() != PickupStatus.OPEN) {
            throw ApiException.conflict("Yêu cầu này đã có tài xế nhận hoặc không còn hiệu lực");
        }
        pickup.setDriver(driver);
        pickup.setStatus(PickupStatus.ACCEPTED);
        notificationService.pickupAccepted(pickup, driver);
        return Response.from(pickupRepository.save(pickup));
    }

    /** Tài xế trả lại việc đã nhận (để người khác nhận). */
    @Transactional
    public Response release(Long id) {
        User driver = currentUserService.getCurrentUser();
        PickupRequest pickup = loadForUpdate(id);
        if (pickup.getStatus() != PickupStatus.ACCEPTED || pickup.getDriver() == null
                || !pickup.getDriver().getId().equals(driver.getId())) {
            throw ApiException.badRequest("Bạn chỉ trả lại được việc mình đang nhận");
        }
        pickup.setDriver(null);
        pickup.setStatus(PickupStatus.OPEN);
        return Response.from(pickupRepository.save(pickup));
    }

    /** Quản lý xưởng phân công (hoặc đổi) tài xế. */
    @Transactional
    public Response assign(Long id, Long driverId) {
        User admin = currentUserService.getCurrentUser();
        PickupRequest pickup = loadForUpdate(id);
        if (!pickup.getStatus().isActive()) {
            throw ApiException.badRequest("Yêu cầu này đã xong hoặc đã hủy");
        }
        User driver = userRepository.findById(driverId)
                .filter(u -> u.getRole() == Role.DRIVER && u.isEnabled())
                .orElseThrow(() -> ApiException.badRequest("Tài xế không hợp lệ hoặc đã bị khóa"));
        pickup.setDriver(driver);
        pickup.setStatus(PickupStatus.ACCEPTED);
        notificationService.pickupAssigned(pickup, admin);
        return Response.from(pickupRepository.save(pickup));
    }

    @Transactional
    public Response cancel(Long id, String reason) {
        User user = currentUserService.getCurrentUser();
        PickupRequest pickup = loadForUpdate(id);
        boolean owner = user.getRole().isCustomerRole()
                && pickup.getCustomer().getId().equals(requireCustomer(user).getId());
        if (!owner && user.getRole() != Role.WORKSHOP_ADMIN) {
            throw ApiException.notFound("Không tìm thấy yêu cầu lấy đồ");
        }
        if (!pickup.getStatus().isActive()) {
            throw ApiException.badRequest("Yêu cầu này đã xong hoặc đã hủy");
        }
        pickup.setStatus(PickupStatus.CANCELLED);
        pickup.setCancelledBy(user.getUsername());
        pickup.setCancelReason(blankToNull(reason));
        notificationService.pickupCancelled(pickup, user);
        return Response.from(pickupRepository.save(pickup));
    }

    /**
     * Gắn đơn vừa tạo vào yêu cầu lấy đồ (gọi từ OrderService trong cùng giao dịch).
     * Tài xế được tạo đơn từ yêu cầu chưa ai nhận hoặc yêu cầu của chính mình, đúng khách hàng của đơn.
     */
    @Transactional
    public void completeWithOrder(Long pickupId, Order order, User driver) {
        PickupRequest pickup = loadForUpdate(pickupId);
        if (!pickup.getStatus().isActive()) {
            throw ApiException.badRequest("Yêu cầu lấy đồ này đã xong hoặc đã hủy");
        }
        if (!pickup.getCustomer().getId().equals(order.getCustomer().getId())) {
            throw ApiException.badRequest("Yêu cầu lấy đồ không thuộc khách hàng của đơn");
        }
        if (pickup.getDriver() != null && !pickup.getDriver().getId().equals(driver.getId())) {
            throw ApiException.badRequest("Yêu cầu này đã được giao cho tài xế khác");
        }
        pickup.setDriver(driver);
        pickup.setOrder(order);
        pickup.setStatus(PickupStatus.DONE);
        pickupRepository.save(pickup);
    }

    private PickupRequest loadForUpdate(Long id) {
        return pickupRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu lấy đồ"));
    }

    private static Customer requireCustomer(User user) {
        if (user.getCustomer() == null) {
            throw ApiException.forbidden("Tài khoản này chưa được liên kết với khách hàng nào");
        }
        return user.getCustomer();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}

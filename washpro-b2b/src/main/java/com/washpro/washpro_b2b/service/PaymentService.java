package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.FinanceDtos.PaymentRequest;
import com.washpro.washpro_b2b.dto.FinanceDtos.PaymentResponse;
import com.washpro.washpro_b2b.entity.Payment;
import com.washpro.washpro_b2b.entity.Restaurant;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.repository.PaymentRepository;
import com.washpro.washpro_b2b.repository.RestaurantRepository;
import com.washpro.washpro_b2b.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RestaurantRepository restaurantRepository;
    private final ReportService reportService;
    private final CurrentUserService currentUserService;

    @Transactional
    public PaymentResponse create(PaymentRequest request) {
        User user = currentUserService.getCurrentUser();
        // Khóa dòng khách hàng tới hết giao dịch: thanh toán của cùng một khách được kiểm tra công nợ lần lượt
        Restaurant restaurant = restaurantRepository.findByIdForUpdate(request.restaurantId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));

        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal balance = reportService.balanceOf(restaurant.getId());
        if (amount.compareTo(balance) > 0) {
            throw ApiException.badRequest("Số tiền thanh toán vượt quá công nợ hiện tại của khách hàng ("
                    + balance.toPlainString() + " đ)");
        }

        Payment payment = Payment.builder()
                .restaurant(restaurant)
                .amount(amount)
                .paidDate(request.paidDate() != null ? request.paidDate() : LocalDate.now())
                .note(request.note() == null || request.note().isBlank() ? null : request.note().trim())
                .createdBy(user.getUsername())
                .build();
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse voidPayment(Long id, String reason) {
        User user = currentUserService.getCurrentUser();
        Payment payment = paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khoản thanh toán"));
        if (payment.isVoided()) {
            throw ApiException.badRequest("Khoản thanh toán này đã được hủy trước đó");
        }
        payment.setVoided(true);
        payment.setVoidedBy(user.getUsername());
        payment.setVoidedAt(java.time.LocalDateTime.now());
        payment.setVoidReason(reason.trim());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    /** Kế toán/Quản lý xưởng xem tất cả (hoặc lọc theo khách hàng); Quản lý khách hàng chỉ xem của khách hàng mình. */
    @Transactional(readOnly = true)
    public List<PaymentResponse> list(Long restaurantId) {
        User user = currentUserService.getCurrentUser();

        List<Payment> payments;
        if (user.getRole() == Role.RESTAURANT_MANAGER) {
            if (user.getRestaurant() == null) {
                throw ApiException.forbidden("Tài khoản này chưa được liên kết với khách hàng nào");
            }
            payments = paymentRepository.findByRestaurantIdOrderByPaidDateDescIdDesc(user.getRestaurant().getId());
        } else if (restaurantId != null) {
            payments = paymentRepository.findByRestaurantIdOrderByPaidDateDescIdDesc(restaurantId);
        } else {
            payments = paymentRepository.findAllByOrderByPaidDateDescIdDesc();
        }
        return payments.stream().map(PaymentResponse::from).toList();
    }
}

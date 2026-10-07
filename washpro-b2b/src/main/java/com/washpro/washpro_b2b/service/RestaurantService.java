package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.RestaurantDtos.Request;
import com.washpro.washpro_b2b.dto.RestaurantDtos.Response;
import com.washpro.washpro_b2b.entity.CustomerType;
import com.washpro.washpro_b2b.entity.Restaurant;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.repository.RestaurantRepository;
import com.washpro.washpro_b2b.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Khách hàng của xưởng: nhà hàng, khách sạn, spa, công ty... */
@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final CurrentUserService currentUserService;

    /** Tài xế chỉ thấy khách hàng đang hợp tác (để chọn khi tạo đơn); các vai trò xưởng thấy tất cả. */
    @Transactional(readOnly = true)
    public List<Response> list() {
        boolean driver = currentUserService.getCurrentUser().getRole() == Role.DRIVER;
        List<Restaurant> restaurants = driver
                ? restaurantRepository.findByActiveTrueOrderByNameAsc()
                : restaurantRepository.findAllByOrderByNameAsc();
        return restaurants.stream().map(Response::from).toList();
    }

    @Transactional
    public Response create(Request request) {
        String name = request.name().trim();
        if (restaurantRepository.findByNameIgnoreCase(name).isPresent()) {
            throw ApiException.conflict("Đã có khách hàng trùng tên này");
        }
        Restaurant restaurant = Restaurant.builder().name(name).build();
        apply(restaurant, request);
        return Response.from(restaurantRepository.save(restaurant));
    }

    @Transactional
    public Response update(Long id, Request request) {
        Restaurant restaurant = find(id);
        String name = request.name().trim();
        restaurantRepository.findByNameIgnoreCase(name).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw ApiException.conflict("Đã có khách hàng trùng tên này");
            }
        });
        restaurant.setName(name);
        apply(restaurant, request);
        return Response.from(restaurantRepository.save(restaurant));
    }

    /** Chép thông tin từ form; thời gian trả đồ / kỳ thanh toán không gửi thì giữ mặc định hoặc giá trị cũ. */
    private static void apply(Restaurant restaurant, Request request) {
        restaurant.setCustomerType(typeOrDefault(request.customerType()));
        restaurant.setAddress(request.address().trim());
        restaurant.setPhone(blankToNull(request.phone()));
        restaurant.setContactName(blankToNull(request.contactName()));
        if (request.turnaroundHours() != null) {
            restaurant.setTurnaroundHours(request.turnaroundHours());
        }
        if (request.paymentTermDays() != null) {
            restaurant.setPaymentTermDays(request.paymentTermDays());
        }
    }

    @Transactional
    public Response setActive(Long id, boolean active) {
        Restaurant restaurant = find(id);
        restaurant.setActive(active);
        return Response.from(restaurantRepository.save(restaurant));
    }

    private Restaurant find(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
    }

    private static CustomerType typeOrDefault(CustomerType type) {
        return type == null ? CustomerType.RESTAURANT : type;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}

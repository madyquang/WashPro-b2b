package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Map;

public interface OrderRepositoryCustom {

    /** Số đơn theo từng trạng thái (một truy vấn GROUP BY); trạng thái không có đơn sẽ vắng mặt. */
    Map<OrderStatus, Long> countByStatus(Specification<Order> spec);
}

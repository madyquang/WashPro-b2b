package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import org.springframework.data.jpa.domain.Specification;

import java.util.EnumMap;
import java.util.Map;

class OrderRepositoryCustomImpl implements OrderRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Map<OrderStatus, Long> countByStatus(Specification<Order> spec) {
        var cb = em.getCriteriaBuilder();
        var query = cb.createTupleQuery();
        var root = query.from(Order.class);
        var status = root.<OrderStatus>get("status");
        query.select(cb.tuple(status, cb.count(root)))
                .where(spec.toPredicate(root, query, cb))
                .groupBy(status);

        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (Tuple t : em.createQuery(query).getResultList()) {
            counts.put(t.get(0, OrderStatus.class), t.get(1, Long.class));
        }
        return counts;
    }
}

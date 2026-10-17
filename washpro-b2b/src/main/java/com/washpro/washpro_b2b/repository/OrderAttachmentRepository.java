package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.AttachmentKind;
import com.washpro.washpro_b2b.entity.OrderAttachment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderAttachmentRepository extends JpaRepository<OrderAttachment, Long> {

    List<OrderAttachment> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);

    long countByOrderId(Long orderId);

    boolean existsByOrderIdAndKindIn(Long orderId, Collection<AttachmentKind> kinds);

    @EntityGraph(attributePaths = { "order", "order.driver", "order.customer" })
    Optional<OrderAttachment> findWithOrderById(Long id);
}

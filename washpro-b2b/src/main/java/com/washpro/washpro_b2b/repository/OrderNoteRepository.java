package com.washpro.washpro_b2b.repository;

import com.washpro.washpro_b2b.entity.OrderNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderNoteRepository extends JpaRepository<OrderNote, Long> {

    List<OrderNote> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);
}

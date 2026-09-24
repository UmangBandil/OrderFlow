package com.orderflow.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** Claims a batch of pending events with SKIP LOCKED so multiple publisher instances don't clash. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")) // SKIP LOCKED
    @Query(
            """
            SELECT e FROM OutboxEvent e
            WHERE e.status = 'PENDING'
            ORDER BY e.id
            """)
    List<OutboxEvent> claimBatch(Pageable pageable);
}

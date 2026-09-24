package com.orderflow.idempotency;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByIdempotencyKeyAndUserIdAndEndpoint(
            String idempotencyKey, Long userId, String endpoint);
}

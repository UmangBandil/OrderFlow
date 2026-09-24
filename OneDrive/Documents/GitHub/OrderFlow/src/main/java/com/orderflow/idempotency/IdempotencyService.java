package com.orderflow.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores idempotency keys with their serialized responses. Redis first for fast replay,
 * Postgres as the durable record. Duplicate request detection is enforced by the DB
 * unique constraint; replay is served from either store.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration REDIS_TTL = Duration.ofHours(24);

    private final IdempotencyKeyRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(
            IdempotencyKeyRepository repository, StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** Looks up a stored response for this key. */
    public <T> Optional<T> findResponse(String key, Long userId, String endpoint, Class<T> type) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        // Fast path: Redis
        try {
            String cached = redisTemplate.opsForValue().get(redisKey(key, userId, endpoint));
            if (cached != null) {
                return Optional.of(objectMapper.readValue(cached, type));
            }
        } catch (Exception e) {
            log.debug("Redis idempotency lookup failed, falling back to DB: {}", e.getMessage());
        }
        // Durable path: Postgres
        return repository
                .findByIdempotencyKeyAndUserIdAndEndpoint(key, userId, endpoint)
                .map(record -> deserialize(record.getResponseBody(), type));
    }

    /** Persists the response for future replays. Best-effort: never fails the caller. */
    @Transactional
    public void storeResponse(String key, Long userId, String endpoint, Object response) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(response);
            repository.save(new IdempotencyKey(key, userId, endpoint, json));
            try {
                redisTemplate.opsForValue().set(redisKey(key, userId, endpoint), json, REDIS_TTL);
            } catch (Exception e) {
                log.debug("Redis idempotency store failed (DB record is durable): {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to persist idempotency key {}: {}", key, e.getMessage());
        }
    }

    private String redisKey(String key, Long userId, String endpoint) {
        return KEY_PREFIX + userId + ":" + endpoint + ":" + key;
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupted idempotency record", e);
        }
    }
}

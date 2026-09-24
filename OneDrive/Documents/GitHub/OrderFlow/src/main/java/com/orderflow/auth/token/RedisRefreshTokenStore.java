package com.orderflow.auth.token;

import com.orderflow.auth.jwt.JwtService;
import com.orderflow.common.config.JwtProperties;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed refresh token store. Refresh tokens are stored as
 * refresh:{userId}:{jti} -> token hash so they can be revoked per user.
 */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRefreshTokenStore.class);
    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;
    private final JwtService jwtService;

    public RedisRefreshTokenStore(
            StringRedisTemplate redisTemplate, JwtProperties jwtProperties, JwtService jwtService) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
        this.jwtService = jwtService;
    }

    @Override
    public void store(Long userId, String refreshToken) {
        String key = KEY_PREFIX + userId + ":" + jwtServiceJti(refreshToken);
        try {
            redisTemplate
                    .opsForValue()
                    .set(key, refreshToken, Duration.ofDays(jwtProperties.getRefreshTokenValidityDays()));
        } catch (Exception e) {
            log.warn("Redis unavailable, refresh token not persisted in Redis: {}", e.getMessage());
        }
    }

    @Override
    public Optional<Long> consume(String refreshToken) {
        String jti = jwtServiceJti(refreshToken);
        String pattern = KEY_PREFIX + "*:" + jti;
        try {
            var keys = redisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                return Optional.empty();
            }
            String key = keys.iterator().next();
            String stored = redisTemplate.opsForValue().get(key);
            if (stored != null && stored.equals(refreshToken)) {
                redisTemplate.delete(key); // rotation
                return Optional.of(Long.parseLong(key.split(":")[1]));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Redis unavailable during refresh token consumption: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void revoke(String refreshToken) {
        try {
            String jti = jwtServiceJti(refreshToken);
            var keys = redisTemplate.keys(KEY_PREFIX + "*:" + jti);
            if (keys != null) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable during refresh token revocation: {}", e.getMessage());
        }
    }

    private String jwtServiceJti(String token) {
        return jwtService.extractJti(token);
    }
}

package com.orderflow.auth.token;

import java.util.Optional;

/** Persists refresh tokens; Redis first, Postgres fallback if Redis is down. */
public interface RefreshTokenStore {

    void store(Long userId, String refreshToken);

    /** Validates and consumes the refresh token (rotation) returning the user id. */
    Optional<Long> consume(String refreshToken);

    void revoke(String refreshToken);
}

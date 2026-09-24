package com.orderflow.auth.jwt;

import com.orderflow.common.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "token_type";

    private final SecretKey key;
    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes());
    }

    public String generateAccessToken(Long userId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TOKEN_TYPE, "access")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(props.getAccessTokenValidityMinutes() * 60L)))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(Long userId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TOKEN_TYPE, "refresh")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(props.getRefreshTokenValidityDays() * 86400L)))
                .signWith(key)
                .compact();
    }

    /** Returns the jti claim of a token, or null if unparseable. */
    public String extractJti(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getId();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Returns claims if the token is a valid access token, otherwise null. */
    public Claims validateAccessToken(String token) {
        return validate(token, "access");
    }

    /** Returns claims if the token is a valid refresh token, otherwise null. */
    public Claims validateRefreshToken(String token) {
        return validate(token, "refresh");
    }

    private Claims validate(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                return null;
            }
            return claims;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }
}

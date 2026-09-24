package com.orderflow.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.common.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-for-orderflow-unit-tests-0123456789abcdef");
        jwtService = new JwtService(props);
    }

    @Test
    @DisplayName("generated access token validates and carries role claim")
    void accessTokenRoundTrip() {
        String token = jwtService.generateAccessToken(42L, "user@example.com", "CUSTOMER");
        Claims claims = jwtService.validateAccessToken(token);
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("refresh token has refresh type claim")
    void refreshTokenRoundTrip() {
        String token = jwtService.generateRefreshToken(42L, "user@example.com", "ADMIN");
        Claims claims = jwtService.validateRefreshToken(token);
        assertThat(claims).isNotNull();
        assertThat(claims.get("token_type", String.class)).isEqualTo("refresh");
    }

    @Test
    @DisplayName("access token is not valid as refresh token (type separation)")
    void typeSeparation() {
        String access = jwtService.generateAccessToken(42L, "user@example.com", "CUSTOMER");
        assertThat(jwtService.validateRefreshToken(access)).isNull();
        String refresh = jwtService.generateRefreshToken(42L, "user@example.com", "CUSTOMER");
        assertThat(jwtService.validateAccessToken(refresh)).isNull();
    }

    @Test
    @DisplayName("tampered token is rejected")
    void tamperedTokenRejected() {
        String token = jwtService.generateAccessToken(42L, "user@example.com", "CUSTOMER");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertThat(jwtService.validateAccessToken(tampered)).isNull();
    }

    @Test
    @DisplayName("garbage string is rejected")
    void garbageRejected() {
        assertThat(jwtService.validateAccessToken("not-a-jwt")).isNull();
        assertThat(jwtService.extractJti("not-a-jwt")).isNull();
    }

    @Test
    @DisplayName("extractJti returns the jti claim")
    void extractJtiWorks() {
        String token = jwtService.generateAccessToken(42L, "user@example.com", "CUSTOMER");
        assertThat(jwtService.extractJti(token)).isNotBlank();
    }

    @Test
    @DisplayName("tokens for different users have different subjects")
    void differentSubjects() {
        String a = jwtService.generateAccessToken(1L, "a@example.com", "CUSTOMER");
        String b = jwtService.generateAccessToken(2L, "b@example.com", "CUSTOMER");
        assertThat(jwtService.validateAccessToken(a).getSubject()).isEqualTo("1");
        assertThat(jwtService.validateAccessToken(b).getSubject()).isEqualTo("2");
    }
}

package com.orderflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret = "local-dev-only-secret-key-change-me-in-production!";
    private int accessTokenValidityMinutes = 15;
    private int refreshTokenValidityDays = 7;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getAccessTokenValidityMinutes() {
        return accessTokenValidityMinutes;
    }

    public void setAccessTokenValidityMinutes(int accessTokenValidityMinutes) {
        this.accessTokenValidityMinutes = accessTokenValidityMinutes;
    }

    public int getRefreshTokenValidityDays() {
        return refreshTokenValidityDays;
    }

    public void setRefreshTokenValidityDays(int refreshTokenValidityDays) {
        this.refreshTokenValidityDays = refreshTokenValidityDays;
    }
}

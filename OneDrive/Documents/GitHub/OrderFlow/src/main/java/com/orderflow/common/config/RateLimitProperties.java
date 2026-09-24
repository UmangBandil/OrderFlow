package com.orderflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private int loginPerMinute = 5;
    private int orderCreatePerMinute = 10;
    private int defaultPerMinute = 100;

    public int getLoginPerMinute() {
        return loginPerMinute;
    }

    public void setLoginPerMinute(int loginPerMinute) {
        this.loginPerMinute = loginPerMinute;
    }

    public int getOrderCreatePerMinute() {
        return orderCreatePerMinute;
    }

    public void setOrderCreatePerMinute(int orderCreatePerMinute) {
        this.orderCreatePerMinute = orderCreatePerMinute;
    }

    public int getDefaultPerMinute() {
        return defaultPerMinute;
    }

    public void setDefaultPerMinute(int defaultPerMinute) {
        this.defaultPerMinute = defaultPerMinute;
    }
}

package com.orderflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class OrderFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderFlowApplication.class, args);
    }
}

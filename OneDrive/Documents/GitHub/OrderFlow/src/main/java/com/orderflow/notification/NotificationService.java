package com.orderflow.notification;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Simulated email notification sender. In production this would delegate to an
 * email provider client; here it logs in the PRD-specified format and counts metrics.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final Counter emailSent;

    public NotificationService(MeterRegistry registry) {
        this.emailSent = Counter.builder("orderflow_notification_email_total")
                .description("Simulated notification emails sent")
                .register(registry);
    }

    public void sendEmail(String to, String template, String orderNumber) {
        log.info("EMAIL SENT");
        log.info("To: {}", to);
        log.info("Template: {}", template);
        log.info("Order: {}", orderNumber);
        emailSent.increment();
    }
}

package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.payment.PaymentStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

class OrderSpecificationsTest {

    @Test
    @DisplayName("no filters still yields a usable specification")
    void emptyFilters() {
        Specification<com.orderflow.order.Order> spec =
                OrderSpecifications.withFilters(null, null, null, null, null, null);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("all filters combine into a single specification")
    void allFilters() {
        Specification<com.orderflow.order.Order> spec = OrderSpecifications.withFilters(
                OrderStatus.SHIPPED,
                42L,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 25),
                PaymentStatus.SUCCESS.name(),
                1L);
        assertThat(spec).isNotNull();
    }
}

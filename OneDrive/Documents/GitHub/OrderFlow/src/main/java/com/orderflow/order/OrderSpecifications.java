package com.orderflow.order;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Dynamic filters for GET /orders. */
public final class OrderSpecifications {

    private OrderSpecifications() {}

    public static Specification<Order> withFilters(
            OrderStatus status, Long customerId, LocalDate from, LocalDate to, String paymentStatus, Long warehouseId) {
        List<Specification<Order>> specs = new ArrayList<>();
        if (status != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (customerId != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("userId"), customerId));
        }
        if (from != null) {
            specs.add(
                    (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay(ZoneOffset.UTC)));
        }
        if (to != null) {
            specs.add((root, q, cb) -> cb.lessThanOrEqualTo(
                    root.get("createdAt"),
                    to.plusDays(1).atStartOfDay(ZoneOffset.UTC).minusNanos(1)));
        }
        if (paymentStatus != null) {
            specs.add((root, q, cb) -> {
                var subquery = q.subquery(Long.class);
                var payment = subquery.from(com.orderflow.payment.Payment.class);
                subquery.select(payment.get("orderId"))
                        .where(cb.equal(
                                payment.get("status"), com.orderflow.payment.PaymentStatus.valueOf(paymentStatus)));
                return root.get("id").in(subquery);
            });
        }
        if (warehouseId != null) {
            specs.add((root, q, cb) -> {
                var subquery = q.subquery(Long.class);
                var shipment = subquery.from(com.orderflow.shipment.Shipment.class);
                subquery.select(shipment.get("orderId")).where(cb.equal(shipment.get("warehouseId"), warehouseId));
                return root.get("id").in(subquery);
            });
        }
        return specs.stream().reduce(Specification.where(null), Specification::and);
    }
}

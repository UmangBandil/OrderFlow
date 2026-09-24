package com.orderflow.shipment;

import com.orderflow.common.error.ApiException;
import com.orderflow.common.error.ErrorCode;
import com.orderflow.common.error.ResourceNotFoundException;
import com.orderflow.common.events.EventPublisher;
import com.orderflow.order.Order;
import com.orderflow.order.OrderRepository;
import com.orderflow.order.OrderStatus;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final com.orderflow.inventory.WarehouseRepository warehouseRepository;
    private final EventPublisher eventPublisher;

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            OrderRepository orderRepository,
            com.orderflow.inventory.WarehouseRepository warehouseRepository,
            EventPublisher eventPublisher) {
        this.shipmentRepository = shipmentRepository;
        this.orderRepository = orderRepository;
        this.warehouseRepository = warehouseRepository;
        this.eventPublisher = eventPublisher;
    }

    /** Creates a shipment for a confirmed/paid order (warehouse manager action). */
    @Transactional
    public Shipment createForOrder(Long orderId, String carrier) {
        Order order =
                orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PAID) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Order is not ready for fulfillment");
        }
        if (shipmentRepository.findByOrderId(orderId).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "Shipment already exists for this order");
        }
        Shipment shipment = new Shipment(
                orderId,
                carrier,
                "TRK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        shipment.setWarehouseId(warehouseRepository
                .findFirstByOrderByIdAsc()
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "default"))
                .getId());
        Shipment saved = shipmentRepository.save(shipment);
        eventPublisher.publish(
                "shipment.created",
                "shipment",
                String.valueOf(saved.getId()),
                Map.of(
                        "orderId", orderId,
                        "trackingNumber", saved.getTrackingNumber(),
                        "carrier", carrier));
        return saved;
    }

    @Transactional
    public Shipment updateStatus(Long shipmentId, ShipmentStatus newStatus) {
        Shipment shipment = shipmentRepository
                .findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", shipmentId));
        ShipmentStatus previous = shipment.getStatus();
        shipment.transitionTo(newStatus);
        eventPublisher.publish(
                "shipment.status_changed",
                "shipment",
                String.valueOf(shipment.getId()),
                Map.of(
                        "orderId", shipment.getOrderId(),
                        "from", previous.name(),
                        "to", newStatus.name()));
        if (newStatus == ShipmentStatus.DELIVERED) {
            eventPublisher.publish(
                    "shipment.delivered",
                    "shipment",
                    String.valueOf(shipment.getId()),
                    Map.of("orderId", shipment.getOrderId()));
            orderRepository
                    .findById(shipment.getOrderId())
                    .ifPresent(order -> order.transitionTo(OrderStatus.DELIVERED));
        } else if (newStatus == ShipmentStatus.SHIPPED) {
            orderRepository.findById(shipment.getOrderId()).ifPresent(order -> {
                if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PROCESSING) {
                    order.transitionTo(OrderStatus.SHIPPED);
                }
            });
        }
        return shipment;
    }

    @Transactional(readOnly = true)
    public Shipment get(Long id) {
        return shipmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Shipment", id));
    }

    @Transactional(readOnly = true)
    public Shipment getByOrder(Long orderId) {
        return shipmentRepository
                .findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment for order", orderId));
    }
}

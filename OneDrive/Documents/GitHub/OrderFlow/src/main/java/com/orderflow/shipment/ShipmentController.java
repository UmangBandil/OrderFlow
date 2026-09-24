package com.orderflow.shipment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipments")
@Tag(name = "Shipments", description = "Fulfillment and shipment tracking (ADMIN / WAREHOUSE_MANAGER)")
@PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    public record CreateShipmentRequest(@NotNull Long orderId, @NotBlank String carrier) {}

    public record UpdateStatusRequest(@NotNull ShipmentStatus status) {}

    public record ShipmentDto(
            Long id,
            Long orderId,
            String carrier,
            String trackingNumber,
            String status,
            java.time.OffsetDateTime shippedAt,
            java.time.OffsetDateTime deliveredAt) {

        public static ShipmentDto from(Shipment shipment) {
            return new ShipmentDto(
                    shipment.getId(),
                    shipment.getOrderId(),
                    shipment.getCarrier(),
                    shipment.getTrackingNumber(),
                    shipment.getStatus().name(),
                    shipment.getShippedAt(),
                    shipment.getDeliveredAt());
        }
    }

    @PostMapping
    @Operation(summary = "Create a shipment for a confirmed order")
    public ResponseEntity<ShipmentDto> create(@Valid @RequestBody CreateShipmentRequest request) {
        Shipment shipment = shipmentService.createForOrder(request.orderId(), request.carrier());
        return ResponseEntity.status(HttpStatus.CREATED).body(ShipmentDto.from(shipment));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get shipment by id")
    public ShipmentDto get(@PathVariable Long id) {
        return ShipmentDto.from(shipmentService.get(id));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get shipment for an order")
    public ShipmentDto getByOrder(@PathVariable Long orderId) {
        return ShipmentDto.from(shipmentService.getByOrder(orderId));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Advance shipment status")
    public ShipmentDto updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return ShipmentDto.from(shipmentService.updateStatus(id, request.status()));
    }
}

package com.orderflow.shipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShipmentTest {

    private Shipment newShipment() {
        return new Shipment(1L, "UPS", "TRK-123");
    }

    @Test
    @DisplayName("valid transitions advance the status and set timestamps")
    void validTransitions() {
        Shipment shipment = newShipment();
        shipment.transitionTo(ShipmentStatus.PACKED);
        shipment.transitionTo(ShipmentStatus.SHIPPED);
        assertThat(shipment.getShippedAt()).isNotNull();
        shipment.transitionTo(ShipmentStatus.IN_TRANSIT);
        shipment.transitionTo(ShipmentStatus.OUT_FOR_DELIVERY);
        shipment.transitionTo(ShipmentStatus.DELIVERED);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(shipment.getDeliveredAt()).isNotNull();
    }

    @Test
    @DisplayName("skipping states is rejected")
    void skippingRejected() {
        Shipment shipment = newShipment();
        assertThatThrownBy(() -> shipment.transitionTo(ShipmentStatus.DELIVERED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("delivered is terminal")
    void deliveredTerminal() {
        Shipment shipment = newShipment();
        shipment.transitionTo(ShipmentStatus.PACKED);
        shipment.transitionTo(ShipmentStatus.SHIPPED);
        shipment.transitionTo(ShipmentStatus.IN_TRANSIT);
        shipment.transitionTo(ShipmentStatus.OUT_FOR_DELIVERY);
        shipment.transitionTo(ShipmentStatus.DELIVERED);
        assertThatThrownBy(() -> shipment.transitionTo(ShipmentStatus.FAILED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("failed can happen from early stages")
    void failedFromCreated() {
        Shipment shipment = newShipment();
        shipment.transitionTo(ShipmentStatus.FAILED);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.FAILED);
    }
}

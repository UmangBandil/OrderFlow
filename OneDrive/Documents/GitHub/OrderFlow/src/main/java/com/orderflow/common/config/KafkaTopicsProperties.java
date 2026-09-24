package com.orderflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orderflow.topics")
public class KafkaTopicsProperties {

    private String orderEvents = "order.events";
    private String paymentEvents = "payment.events";
    private String inventoryEvents = "inventory.events";
    private String shipmentEvents = "shipment.events";
    private String orderEventsDlt = "order.events.dlt";

    public String getOrderEvents() {
        return orderEvents;
    }

    public void setOrderEvents(String orderEvents) {
        this.orderEvents = orderEvents;
    }

    public String getPaymentEvents() {
        return paymentEvents;
    }

    public void setPaymentEvents(String paymentEvents) {
        this.paymentEvents = paymentEvents;
    }

    public String getInventoryEvents() {
        return inventoryEvents;
    }

    public void setInventoryEvents(String inventoryEvents) {
        this.inventoryEvents = inventoryEvents;
    }

    public String getShipmentEvents() {
        return shipmentEvents;
    }

    public void setShipmentEvents(String shipmentEvents) {
        this.shipmentEvents = shipmentEvents;
    }

    public String getOrderEventsDlt() {
        return orderEventsDlt;
    }

    public void setOrderEventsDlt(String orderEventsDlt) {
        this.orderEventsDlt = orderEventsDlt;
    }
}

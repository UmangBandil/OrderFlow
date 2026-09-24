package com.orderflow.order;

import com.orderflow.common.error.ApiException;
import com.orderflow.common.error.ErrorCode;

public class OrderNotFoundException extends ApiException {

    public OrderNotFoundException(String orderNumber) {
        super(ErrorCode.RESOURCE_NOT_FOUND, "Order not found: " + orderNumber);
    }
}

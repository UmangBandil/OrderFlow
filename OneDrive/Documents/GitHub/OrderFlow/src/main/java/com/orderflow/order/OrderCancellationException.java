package com.orderflow.order;

import com.orderflow.common.error.ApiException;
import com.orderflow.common.error.ErrorCode;

public class OrderCancellationException extends ApiException {

    public OrderCancellationException(String message) {
        super(ErrorCode.ORDER_NOT_CANCELLABLE, message);
    }
}

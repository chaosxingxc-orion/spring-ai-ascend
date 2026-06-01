package com.huawei.ascend.service.access.egress;

public class EgressDeliveryException extends RuntimeException {
    public EgressDeliveryException(String message) {
        super(message);
    }

    public EgressDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}



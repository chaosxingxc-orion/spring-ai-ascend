package com.huawei.ascend.examples.workmate.audit;

public class AuditLedgerException extends RuntimeException {

    public AuditLedgerException(String message) {
        super(message);
    }

    public AuditLedgerException(String message, Throwable cause) {
        super(message, cause);
    }
}

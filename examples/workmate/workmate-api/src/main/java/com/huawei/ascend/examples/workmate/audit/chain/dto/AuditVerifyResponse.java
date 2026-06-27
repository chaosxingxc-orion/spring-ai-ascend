package com.huawei.ascend.examples.workmate.audit.chain.dto;

import com.huawei.ascend.examples.workmate.audit.chain.AuditChainVerifier.AuditVerifyResult;

public record AuditVerifyResponse(
        boolean ok,
        long verifiedThroughSeq,
        Long brokenSeqGlobal,
        String field,
        String expected,
        String actual) {

    public static AuditVerifyResponse from(AuditVerifyResult result) {
        return new AuditVerifyResponse(
                result.ok(),
                result.verifiedThroughSeq(),
                result.brokenSeqGlobal(),
                result.field(),
                result.expected(),
                result.actual());
    }
}

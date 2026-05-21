package com.huawei.ascend.service.runtime.probe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OssApiProbeTest {

    @Test
    void classIsLoadable() throws Exception {
        Class.forName("com.huawei.ascend.service.runtime.probe.OssApiProbe");
    }

    @Test
    void probeReturnsNonNullString() {
        // OssApiProbe.probe() assembles a string of class simple names for each
        // critical-path dep (Spring AI, Temporal, MCP, Tika). A successful call
        // proves all referenced Class<?> literals resolve at the pinned versions.
        String result = OssApiProbe.probe();
        assertThat(result).isNotNull();
        // Probe label retained pre-Phase-C verbatim for back-compat (ADR-0078 consolidation kept the string).
        assertThat(result).startsWith("agent-runtime W0-U2 probe:");
    }

    @Test
    void temporalGetVersionShapeReturnsMinusOne() {
        // temporalGetVersionShape() contains an unreachable invocation of
        // Workflow.getVersion() that is present only to type-check the ADR-03
        // method signature at SDK 1.35.0. Outside a workflow context it must
        // return -1 (the safe sentinel value, never the real method).
        int shape = OssApiProbe.temporalGetVersionShape();
        assertThat(shape).isEqualTo(-1);
    }
}

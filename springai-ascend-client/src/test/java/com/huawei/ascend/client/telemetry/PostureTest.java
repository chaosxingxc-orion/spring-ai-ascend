package com.huawei.ascend.client.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.Test;

/**
 * The posture contract: head-sampling ratios match the platform's posture
 * (dev 100%, research 10%, prod 1%), PROD structurally forbids PII on
 * telemetry, and the otlpHttp pipeline turns the ratio into a parent-based
 * ratio sampler — asserted on sampler config, not on sampling statistics.
 */
class PostureTest {

    @Test
    void ratiosMirrorThePlatformHeadSamplingPosture() {
        assertThat(Posture.DEV.samplingRatio()).isEqualTo(1.0);
        assertThat(Posture.RESEARCH.samplingRatio()).isEqualTo(0.10);
        assertThat(Posture.PROD.samplingRatio()).isEqualTo(0.01);
    }

    @Test
    void onlyProdForbidsPiiOnTelemetry() {
        assertThat(Posture.DEV.allowsPiiOnTelemetry()).isTrue();
        assertThat(Posture.RESEARCH.allowsPiiOnTelemetry()).isTrue();
        assertThat(Posture.PROD.allowsPiiOnTelemetry()).isFalse();
    }

    @Test
    void otlpPipelineSamplerIsParentBasedRatioAtThePostureRatio() {
        Sampler prod = OtlpClientTelemetry.sampler(Posture.PROD);
        assertThat(prod.getDescription())
                .contains("ParentBased")
                .contains("TraceIdRatioBased");
        // The ratio itself is asserted via the enum (locale-safe), and the
        // sampler is built from exactly that value.
        assertThat(prod).isEqualTo(
                Sampler.parentBased(Sampler.traceIdRatioBased(Posture.PROD.samplingRatio())));
    }
}

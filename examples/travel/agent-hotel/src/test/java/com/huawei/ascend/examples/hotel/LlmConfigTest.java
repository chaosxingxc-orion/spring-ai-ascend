/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmConfigTest {

    @Test
    void fromEnvFallsBackToDefaultsWhenVarsUnset() {
        // Note: we cannot reliably set env vars in-process, so this just checks the
        // defaults are applied when the env vars are not set in CI / dev.
        LlmConfig cfg = LlmConfig.fromEnv();
        assertThat(cfg.provider()).isNotBlank();
        assertThat(cfg.apiBase()).isNotBlank();
        assertThat(cfg.modelName()).isNotBlank();
    }

    @Test
    void recordExposesConstructorArgs() {
        LlmConfig cfg = new LlmConfig("openai", "sk-x", "http://x/v1", "gpt-test", true);
        assertThat(cfg.provider()).isEqualTo("openai");
        assertThat(cfg.apiKey()).isEqualTo("sk-x");
        assertThat(cfg.apiBase()).isEqualTo("http://x/v1");
        assertThat(cfg.modelName()).isEqualTo("gpt-test");
        assertThat(cfg.sslVerify()).isTrue();
    }
}

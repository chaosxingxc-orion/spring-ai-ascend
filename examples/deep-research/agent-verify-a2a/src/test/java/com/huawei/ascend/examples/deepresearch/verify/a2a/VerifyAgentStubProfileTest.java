/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.verify.a2a;

import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = VerifyAgentApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "VERIFY_LLM_API_KEY=test-key",
                "VERIFY_LLM_API_BASE=http://localhost:4000/v1",
                "VERIFY_LLM_MODEL=test-model"
        })
@ActiveProfiles("stub")
class VerifyAgentStubProfileTest {

    @Autowired(required = false)
    private OpenJiuwenAgentRuntimeHandler handler;

    @Test
    void contextLoads_withStubProfile() {
        // If the context loads without exception, stub wiring is correct
        assertThat(true).isTrue();
    }

    @Test
    void handler_shouldBePresent() {
        assertThat(handler).isNotNull();
    }
}

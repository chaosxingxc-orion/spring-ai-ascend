/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenMcpToolInstaller;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = DeepResearchApplication.class)
@ActiveProfiles("mcp-smoke")
class DeepResearchMcpAutoConfigurationTest {

    @Autowired
    private AgentRuntimeHandler deepResearchAgentHandler;

    @Autowired(required = false)
    private OpenJiuwenMcpToolInstaller openJiuwenMcpToolInstaller;

    @Test
    void mcpSmokeProfileWiresMcpInstallerBean() {
        assertThat(deepResearchAgentHandler).isNotNull();
        assertThat(openJiuwenMcpToolInstaller).isNotNull();
    }
}

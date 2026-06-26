/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.verify.a2a;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class VerifyAgentHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void handler_shouldConstructWithoutException() throws Exception {
        // Write a minimal valid YAML for testing
        Path yamlPath = tempDir.resolve("agent.yaml");
        String minimalYaml = """
                schema: ascend-agent/v1
                name: test-verify-agent
                description: Test agent.
                framework:
                  type: openjiuwen
                  agent: react
                model:
                  provider: OpenAI
                  name: test-model
                  baseUrl: http://localhost:4000/v1
                  apiKey: test-key
                  sslVerify: false
                prompt:
                  system: You are a test agent.
                """;
        Files.writeString(yamlPath, minimalYaml);

        VerifyAgentHandler handler = new VerifyAgentHandler(yamlPath);
        assertThat(handler).isNotNull();
    }

    @Test
    void nullYamlPath_shouldThrow() {
        assertThatCode(() -> new VerifyAgentHandler(null))
                .isInstanceOf(NullPointerException.class);
    }
}

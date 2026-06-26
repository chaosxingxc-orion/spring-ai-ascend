/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.verify.client;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TavilySearchClientTest {

    @Test
    void extractDomain_shouldStripProtocolAndWww() {
        assertThat(TavilySearchClient.extractDomain("https://www.volcengine.com/docs"))
                .isEqualTo("volcengine.com");
        assertThat(TavilySearchClient.extractDomain("http://blog.example.com/post/1"))
                .isEqualTo("blog.example.com");
        assertThat(TavilySearchClient.extractDomain(""))
                .isEqualTo("");
        assertThat(TavilySearchClient.extractDomain(null))
                .isEqualTo("");
    }

    @Test
    void setApiKey_shouldAcceptNonBlankKey() {
        TavilySearchClient.setApiKey("tvly-test-key");
        // No exception thrown
    }

    @Test
    void search_withoutApiKey_shouldThrow() {
        TavilySearchClient.setApiKey("");
        TavilySearchClient client = new TavilySearchClient();
        try {
            client.search("test", 5, "month", "zh");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).contains("Tavily API key not set");
        }
    }
}

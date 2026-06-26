/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.deepresearch.mock.MockSubAgentFixtures;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeepResearchMockFixtureTest {

    @Test
    void searchFixtureReturnsOfficialResults() {
        assertThat(MockSubAgentFixtures.searchResults().get("results")).asList().isNotEmpty();
    }

    @Test
    void readFixturesCoverSpaBlockedAndCloudflare403() {
        assertThat(docType(MockSubAgentFixtures.readSpaBlocked())).isEqualTo("spa_blocked");
        assertThat(docType(MockSubAgentFixtures.readCloudflare403())).isEqualTo("cloudflare_403");
        assertThat(docType(MockSubAgentFixtures.readOfficialPricing())).isEqualTo("pricing_page");
    }

    @Test
    void verifyFixturesCoverContradictAndInsufficient() {
        assertThat(MockSubAgentFixtures.verifyContradict().get("verdict")).isEqualTo("contradict");
        assertThat(MockSubAgentFixtures.verifyInsufficient().get("verdict")).isEqualTo("insufficient");
        assertThat(MockSubAgentFixtures.verifySupport().get("verdict")).isEqualTo("support");
    }

    @SuppressWarnings("unchecked")
    private static String docType(Map<String, Object> readResponse) {
        Object metadata = readResponse.get("metadata");
        assertThat(metadata).isInstanceOf(Map.class);
        return String.valueOf(((Map<String, Object>) metadata).get("doc_type"));
    }
}

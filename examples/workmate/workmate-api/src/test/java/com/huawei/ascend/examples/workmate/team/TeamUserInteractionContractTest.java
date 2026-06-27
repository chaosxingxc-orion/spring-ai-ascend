package com.huawei.ascend.examples.workmate.team;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TeamUserInteractionContractTest {

    @Test
    void leaderAgentIdIsStable() {
        assertThat(TeamUserInteractionContract.isLeaderAgent("leader")).isTrue();
        assertThat(TeamUserInteractionContract.isLeaderAgent("topic-researcher")).isFalse();
    }

    @Test
    void memberRulesBanAskTool() {
        String rules = TeamUserInteractionContract.memberHitlRules("workmate_ask_user_question__demo");
        assertThat(rules).contains("NEVER call workmate_ask_user_question__demo");
        assertThat(rules).contains("【需主编决策】");
    }

    @Test
    void memberSendMessageRulesRequireExplicitHandback() {
        String rules = TeamUserInteractionContract.memberSendMessageRules();
        assertThat(rules).contains("send_message");
        assertThat(rules).contains("team-lead");
        assertThat(rules).contains("runtime-injected");
    }

    @Test
    void leaderSendMessageRulesCoverDelegation() {
        String rules = TeamUserInteractionContract.leaderSendMessageRules();
        assertThat(rules).contains("send_message");
        assertThat(rules).contains("spawn_member");
        assertThat(rules).contains("wait for their send_message handbacks");
    }

    @Test
    void leaderHitlRulesRequireAskToolForConfirmation() {
        String rules = TeamUserInteractionContract.leaderHitlRules("workmate_ask_user_question__demo");
        assertThat(rules).contains("workmate_ask_user_question__demo");
        assertThat(rules).contains("NEVER write");
        assertThat(rules).contains("confirmation card");
    }

    @Test
    void detectsResearchPlannerOutlineHandback() {
        assertThat(TeamUserInteractionContract.isResearchPlannerOutlineHandback(
                "research-planner", "{\"title\":\"AI\",\"sections\":[\"a\",\"b\"]}")).isTrue();
        assertThat(TeamUserInteractionContract.isResearchPlannerOutlineHandback(
                "topic-researcher", "{\"sections\":[\"a\"]}")).isFalse();
    }
}

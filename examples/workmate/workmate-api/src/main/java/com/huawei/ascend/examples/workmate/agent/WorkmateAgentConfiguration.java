package com.huawei.ascend.examples.workmate.agent;

import com.huawei.ascend.runtime.engine.a2a.AgentCards;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenCheckpointerConfigurer;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import org.a2aproject.sdk.spec.AgentCard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class WorkmateAgentConfiguration {

    @Bean
    Checkpointer workmateCheckpointer() {
        return OpenJiuwenCheckpointerConfigurer.setInMemoryDefault();
    }

    @Bean
    AgentCard workmateDefaultAgentCard() {
        return AgentCards.create(
                WorkmateAgentHandler.AGENT_ID,
                "WorkMate workspace ReAct agent (Read / Write / Bash).");
    }
}

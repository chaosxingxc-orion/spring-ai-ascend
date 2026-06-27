package com.huawei.ascend.examples.financial.planagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the plan-agent (port 18090).
 *
 * <p>A standalone single-agent runtime running an OpenJiuwen ReAct LLM that
 * decomposes a one-sentence banking request into ordered atomic tasks and
 * calls the remote versatile-call agent per task.
 */
@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.examples.financial.planagent",
        "com.huawei.ascend.runtime.boot"})
public class PlanAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlanAgentApplication.class, args);
    }
}

package com.huawei.ascend.examples.a2a.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Questioner Workflow Agent.
 *
 * <p>Auto-scans {@code com.huawei.ascend.runtime.boot} to activate the
 * agent-runtime A2A endpoint and handler discovery.
 */
@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.examples.a2a.workflow",
        "com.huawei.ascend.runtime.boot"})
public class QuestionerWorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuestionerWorkflowApplication.class, args);
    }
}

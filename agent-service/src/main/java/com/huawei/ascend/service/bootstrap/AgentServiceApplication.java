package com.huawei.ascend.service.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the agent-service runtime.
 *
 * <p>Boots the full five-layer stack — access, session, internal-event-queue,
 * task-centric-control and engine — into one Spring context. Component scanning
 * is rooted at the access and bootstrap packages so the access layer's
 * {@code @Configuration} and A2A {@code @RestController}s and the cross-module
 * glue are picked up; the task-control and engine modules contribute through
 * their {@code AutoConfiguration} imports (and so are deliberately excluded from
 * the scan to avoid double registration).
 */
@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.service.access",
        "com.huawei.ascend.service.bootstrap"
})
public class AgentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentServiceApplication.class, args);
    }
}

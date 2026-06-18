package com.bank.financial;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bank financial-agents application — entry point.
 *
 * <p>We host our own agents inside the spring-ai-ascend A2A runtime. The only
 * platform coupling is the component scan: alongside our own package we scan
 * {@code com.huawei.ascend.runtime.boot}, which contributes the runtime's
 * auto-configuration (A2A JSON-RPC endpoint, handler discovery, agent-card
 * generation, tenant access control, lifecycle). We never modify the platform;
 * we only consume it.
 */
@SpringBootApplication(scanBasePackages = {
        "com.bank.financial",
        "com.huawei.ascend.runtime.boot"})
public class FinancialAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinancialAgentApplication.class, args);
    }
}

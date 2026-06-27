package com.huawei.ascend.examples.financial.expensereview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Expense Review Workflow Agent.
 *
 * <p>Auto-scans {@code com.huawei.ascend.runtime.boot} to activate the
 * agent-runtime A2A endpoint and handler discovery.
 */
@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.examples.financial.expensereview",
        "com.huawei.ascend.runtime.boot"})
public class ExpenseReviewWorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseReviewWorkflowApplication.class, args);
    }
}

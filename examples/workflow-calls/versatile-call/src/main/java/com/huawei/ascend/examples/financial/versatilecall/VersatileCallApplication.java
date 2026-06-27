package com.huawei.ascend.examples.financial.versatilecall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the versatile-call agent (port 18092).
 *
 * <p>A standalone single-agent runtime that proxies A2A requests onto the
 * external versatile MOCK at {@code http://127.0.0.1:31113} and streams SSE
 * back, extracting {@code node_type=QA} results.
 */
@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.examples.financial.versatilecall",
        "com.huawei.ascend.runtime.boot"})
public class VersatileCallApplication {

    public static void main(String[] args) {
        SpringApplication.run(VersatileCallApplication.class, args);
    }
}

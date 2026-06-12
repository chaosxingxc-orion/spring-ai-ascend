package com.huawei.ascend.examples.a2a.versatile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootable runtime application that hosts a single versatile workflow proxy
 * agent behind the standard A2A JSON-RPC endpoint.
 *
 * <h3>Quick start</h3>
 * <pre>
 * mvn spring-boot:run -pl examples/agent-runtime-a2a-versatile-e2e
 * </pre>
 *
 * <h3>Run the manual E2E test</h3>
 * With the application running:
 * <pre>
 * mvn test -pl examples/agent-runtime-a2a-versatile-e2e \
 *     -Dtest=VersatileA2aE2eTest
 * </pre>
 */
@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.examples.a2a.versatile",
        "com.huawei.ascend.runtime.boot"})
public class VersatileA2aRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(VersatileA2aRuntimeApplication.class, args);
    }
}

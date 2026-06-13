package com.huawei.ascend.examples.a2a.openjiuwensimple;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the openJiuwen simple example.
 *
 * <p>{@code scanBasePackages} includes two packages:
 * <ul>
 *   <li>{@code com.huawei.ascend.examples.a2a.openjiuwensimple} — this example's configuration</li>
 *   <li>{@code com.huawei.ascend.runtime.boot} — agent-runtime auto-configuration (A2A endpoints,
 *       handler discovery, access control, etc.)</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.examples.a2a.openjiuwensimple",
        "com.huawei.ascend.runtime.boot"})
public class OpenJiuwenSimpleApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenJiuwenSimpleApplication.class, args);
    }
}

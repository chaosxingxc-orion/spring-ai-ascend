package com.huawei.ascend.edp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * EDPAgent spike Spring Boot 启动入口。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>启动 edp-agent-trae spike 后端服务。</li>
 *     <li>扫描 EDPAgent spike 自身 Bean。</li>
 *     <li>扫描 agent-runtime boot 包，使 /a2a JSON-RPC Controller 等运行时组件生效。</li>
 * </ul>
 *
 * <p>对外提供的接口：</p>
 * <ul>
 *     <li>{@link #main(String[])}：命令行启动入口。</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.edp",
        "com.huawei.ascend.runtime.boot"})
public class EdpaSpikeApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 命令行参数，透传给 SpringApplication
     */
    public static void main(String[] args) {
        // 关键跳转：交给 Spring Boot 创建容器、加载配置、注册 A2A Controller 和 RuntimeHandler Bean。
        SpringApplication.run(EdpaSpikeApplication.class, args);
    }
}

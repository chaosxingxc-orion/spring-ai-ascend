package com.huawei.ascend.versatile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone A2A process hosting a single Versatile workflow proxy agent.
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>作为独立进程承担 EDPAgent 与 Versatile REST API 之间的通信职责，
 *         把上游 a2a 请求转换为 Versatile REST 调用。</li>
 *     <li>通过扫描 {@code com.huawei.ascend.runtime.boot} 激活 agent-runtime 的
 *         A2A JSON-RPC 端点与 AgentCard 发现端点。</li>
 * </ul>
 *
 * <p>对外提供：</p>
 * <ul>
 *     <li>{@code POST /a2a}：A2A JSON-RPC（含 SSE 流式）。</li>
 *     <li>{@code GET /.well-known/agent-card.json}：AgentCard 发现。</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.versatile",
        "com.huawei.ascend.runtime.boot"})
public class VersatileAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(VersatileAgentApplication.class, args);
    }
}

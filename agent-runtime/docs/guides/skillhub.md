# SkillHub 中间件

SkillHub 中间件用于渐进式加载 Agent 技能：先列出轻量摘要，再按需加载完整技能定义或技能包。它和 MCP 解耦：SkillHub 管理技能说明，MCP 管理工具调用。

## 1. 概述

```java
public interface SkillHubProvider {
    List<SkillSummary> listSkills(AgentExecutionContext context);

    SkillDefinition loadSkill(AgentExecutionContext context, String skillId);

    default SkillPackage loadSkillPackage(AgentExecutionContext context, String skillId);
}
```

OpenJiuwen 当前通过 `OpenJiuwenSkillHubInstaller` 把 SkillHub 返回的本地 skill 路径注册到 `BaseAgent`。

## 2. 快速开始

### Step 1 — 提供 SkillHubProvider

```java
@Bean
SkillHubProvider skillHubProvider() {
    return new LocalDirectorySkillHubProvider(Path.of("skills"));
}
```

Provider 至少需要实现：

- `listSkills(context)`：返回 `SkillSummary` 列表。
- `loadSkill(context, skillId)`：返回完整 `SkillDefinition`。

### Step 2 — 设置 OpenJiuwen metadata

OpenJiuwen Wave 1 从 `SkillDefinition.metadata` 中读取本地路径：

```java
new SkillDefinition(
        "date-helper",
        "date-helper",
        "帮助用户处理日期表达",
        instructions,
        List.of(),
        List.of(),
        Map.of("openjiuwen.skill.path", "skills/date-helper"));
```

多个路径可以使用：

```java
Map.of("openjiuwen.skill.paths", List.of("skills/a", "skills/b"))
```

### Step 3 — 自动或手工安装

Spring Boot 自动装配条件：

- classpath 中存在 OpenJiuwen
- Spring 容器中存在 `SkillHubProvider`
- Spring 容器中存在 `OpenJiuwenAgentRuntimeHandler`

满足条件时，runtime 创建 `OpenJiuwenSkillHubInstaller` 并注入 handler。

手工 wiring：

```java
handler.setSkillHubInstaller(new OpenJiuwenSkillHubInstaller(skillHubProvider));
```

## 3. 工作原理

```text
OpenJiuwenAgentRuntimeHandler.doExecute(...)
  │
  ├─ createOpenJiuwenAgent(context)
  ├─ installRuntimeTools(agent, context)
  │     └─ OpenJiuwenSkillHubInstaller.install(...)
  │          ├─ skillHubProvider.listSkills(context)
  │          ├─ skillHubProvider.loadSkill(context, skillId)
  │          ├─ 读取 openjiuwen.skill.path(s)
  │          └─ agent.registerSkill(path)
  └─ Runner.runAgentStreaming(...)
```

SkillHub installer 每次执行前安装技能。Provider 可以自己缓存 summary/definition，也可以按 tenant、user、agentId 返回不同技能集合。

## 4. 数据模型

| 类型 | 用途 |
|---|---|
| `SkillSummary` | 轻量摘要，包含 `skillId`、`name`、`description`、`tags`、`metadata` |
| `SkillDefinition` | 完整技能定义，包含 `instructions`、`referenceUris`、`toolDependencies`、`metadata` |
| `SkillToolDependency` | 描述推荐工具依赖，例如某个 MCP tool |
| `SkillPackage` | 可选打包 payload，通常是包含 `SKILL.md` 和参考文件的 zip |

## 5. 示例

| Example | 说明 |
|---|---|
| `examples/agent-runtime-middleware-skillhub-local/` | 本地 `skills/` 目录作为 SkillHub |
| `examples/agent-runtime-middleware-skillhub-remote-json/` | 远端 HTTP JSON catalog 作为 SkillHub |

运行示例：

```bash
./mvnw -f examples/agent-runtime-middleware-skillhub-local/pom.xml verify
./mvnw -f examples/agent-runtime-middleware-skillhub-remote-json/pom.xml verify
```

手工 curl 流程见对应 example 的 `README.md` 和 `TUTORIAL.cn.md`。

## 6. 限制

- 当前不实现 Nacos Skill Registry。
- 当前不实现动态技能热更新。
- SkillHub 不负责执行工具调用；需要工具调用时通过 MCP 或其他 tool installer 接入。
- 多租户权限过滤由业务自定义 `SkillHubProvider` 实现。

## 7. 相关资源

- 设计文档：`architecture/docs/L2/agent-runtime/skillhub-runtime-middleware-design.md`
- Proposal：`docs/logs/reviews/2026-06-17-agent-runtime-skill-hub-middleware-proposal.cn.md`
- 测试设计：`architecture/docs/L1/agent-runtime/features/skillhub-middleware-test-design.cn.md`

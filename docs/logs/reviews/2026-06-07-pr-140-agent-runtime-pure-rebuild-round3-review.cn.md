---
title: "PR 140 第三轮复审报告：authority drift 收敛后的剩余阻断"
review_target: "PR-140 round-3"
review_url: "https://github.com/chaosxingxc-orion/spring-ai-ascend/pull/140"
reviewed_at: "2026-06-07"
reviewed_base: "4b1673df6d15ace4db993d2508f16eb139fff8d2"
reviewed_head: "89ed7c7512b1befbe6507f12b85afdbc9a32fd7f"
previous_reviews:
  - "docs/logs/reviews/2026-06-07-pr-140-agent-runtime-pure-rebuild-review.cn.md"
  - "docs/logs/reviews/2026-06-07-pr-140-agent-runtime-pure-rebuild-follow-up-review.cn.md"
status: "blocking_findings"
---

# PR 140 第三轮复审报告：authority drift 收敛后的剩余阻断

## 结论

本轮复审基于 PR 140 最新 head `89ed7c7512b1befbe6507f12b85afdbc9a32fd7f`。工程团队已经接受并处理了上一轮 review 意见：`agent-service/module-metadata.yaml` 不再允许 `agent-middleware`，`contract-catalog.md` 的 active SPI 总数已收敛到 10，`architecture-status.yaml` 的 4 模块主叙述也已更新。

但当前 PR 仍不建议合并。主要原因有两个：第一，仓库指定的 canonical architecture sync gate 在本地失败，Structurizr workspace 已无法解析；第二，`agent-runtime` 仍声明会生成 boot classifier executable jar，但 Maven plugin 的 `mainClass` 指向已删除的 `AgentRuntimeApplication`，而当前模块内没有可用于 boot jar 的 `public static main` 入口。除此之外，architecture closure 仍有若干 `agent-middleware` / 8 模块视图残留，会在修掉 P0 后继续造成架构读者误判。

## 已确认收敛项

- `agent-service/module-metadata.yaml:17-20` 已只允许 `agent-runtime` 与 `agent-bus`，不再包含 `agent-middleware`。
- `architecture/facts/generated/module-build.json:56-77` 中 fact ID `build-module/agent-service` 的 `allowed_dependencies` 已同步为 `[agent-runtime, agent-bus]`。
- `docs/contracts/contract-catalog.md:33-54` 已把 active SPI surface 收敛到 10 total，且 `agent-runtime` shipped SPI 明确为 `AgentRuntimeHandler` + `StreamAdapter`。
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:42-55` 已从旧 `ExecutorAdapter` / `AgentHandler` 叙述改为当前 `AgentRuntimeHandler` / `StreamAdapter` / `RuntimeApp` / `LocalA2aRuntimeHost`。

## 阻断问题

### P0-1 Canonical architecture sync gate 失败：workspace.dsl 仍引用已删除的 `agentMiddleware` 元素

**位置：**

- `architecture/workspace.dsl:110-113`
- `architecture/features/function-points.dsl:237-245`
- `architecture/features/function-points.dsl:353-359`

**证据：**

本地执行仓库指定的 canonical gate：

```bash
bash gate/check_architecture_sync.sh
```

结果为失败：

```text
ARCHITECTURE WORKSPACE: profile violations (exit 1)
GATE: FAIL (workspace gate)
DSL PARSE FAILED: The source element "agentMiddleware" does not exist at line 353 of .../architecture/features/function-points.dsl:
agentMiddleware -> fpHookDispatch "implements RuntimeMiddleware hook dispatch" "SAA Relationship" {
```

`architecture/workspace.dsl:112` 会 include `features/function-points.dsl`。该文件中 `fpHookDispatch` 仍是 `saa.owner = agent-middleware`，且 `architecture/features/function-points.dsl:353` 仍有：

```dsl
agentMiddleware -> fpHookDispatch "implements RuntimeMiddleware hook dispatch" "SAA Relationship" {
```

但 PR 140 已删除 `agentMiddleware` container，因此 Structurizr DSL 解析直接失败。

**影响：**

这是合并阻断。PR body 声称 `bash gate/check_parallel.sh` 与 architecture gate 已通过，但当前本地 canonical `bash gate/check_architecture_sync.sh` 不能解析 workspace。根据 AGENTS.md，`architecture/workspace.dsl` 是 architecture authoring root；该 root 无法解析时，任何“architecture facts/gate 已闭环”的结论都不成立。

**建议修复：**

如果 hook dispatch 已随 `agent-middleware` 删除而退役，应删除或改为 historical/deferred，不应继续作为 shipped function point。若团队决定 hook dispatch 转由 `agent-runtime` 承载，则需要同时更新：

- `fpHookDispatch` 的 owner/status/description；
- `agentMiddleware -> fpHookDispatch` 关系，改为真实 owner；
- 相关 feature/frame/capability DSL；
- 重新运行 `bash gate/check_architecture_sync.sh`。

### P1-1 `agent-runtime` boot classifier jar 仍指向已删除主类，当前模块没有可执行 main

**位置：**

- `agent-runtime/pom.xml:266-275`
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/app/RuntimeApp.java:22-39`
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/app/LocalA2aRuntimeHost.java:37-57`

**问题：**

`agent-runtime/pom.xml` 仍配置 Spring Boot Maven plugin：

```xml
<classifier>boot</classifier>
<mainClass>com.huawei.ascend.runtime.bootstrap.AgentRuntimeApplication</mainClass>
```

但 production source 中已经没有 `com.huawei.ascend.runtime.bootstrap.AgentRuntimeApplication`。当前真实 entry shape 是 library-style API：`RuntimeApp.create(handler).run(host)`，其中 `LocalA2aRuntimeHost` 负责启动 Spring context，但它本身没有 `public static void main(String[] args)`。本轮静态搜索只在 example app 下找到 `main`，没有在 `agent-runtime/src/main/java` 找到可作为 boot jar `Start-Class` 的 main 方法。

**影响：**

这会让 `agent-runtime` 的 `boot` classifier artifact 失真：构建产物即使生成，也会把启动入口指向不存在/不可启动的类。PR 描述中强调 “Pure-Java bootable entry: `app.RuntimeApp` / `RuntimeHost` ... `bootstrap` retired”，但 POM 仍在发布旧 bootstrap 入口，属于发布面与代码面不一致。

**建议修复：**

二选一：

1. 如果 `agent-runtime` 只应该作为 library runtime 发布，删除 `spring-boot-maven-plugin` 的 boot classifier repackage 配置，并把 README 改成 library embedding 模式。
2. 如果仍要发布可执行 boot classifier jar，则新增真实 `public static main` 启动类，并把 `mainClass` 指向它；同时补一个 packaging/manifest 或 launch smoke test，防止再次指向已删除类。

### P1-2 Architecture closure 仍有 8 模块 / `agent-middleware` shipped surface 残留

**位置：**

- `architecture/views/L1-development.dsl:1-5`
- `architecture/views/L1-physical.dsl:9-13`
- `architecture/generated/modules.dsl:47-52`
- `architecture/features/function-points.dsl:237-245`
- `architecture/features/engineering-frames.dsl:123-135`
- `architecture/README.md:82-89`

**问题：**

PR 140 最新叙述已经是 4 reactor modules，但 architecture closure 中仍存在 active-sounding 旧结构：

- L1 development view 仍写 “Eight Maven modules: agent-client, agent-bus, agent-service, agent-runtime, agent-middleware, agent-evolve, spring-ai-ascend-graphmemory-starter, spring-ai-ascend-dependencies”。
- L1 physical view 仍把 Compute & Control 描述为 `agent-service / agent-runtime / agent-middleware`。
- generated modules 中 BoM 描述仍说 pins reactor modules `(agent-service, agent-runtime, agent-middleware, agent-bus)`。
- function point / engineering frame 仍把 hook dispatch owner 写成 `agent-middleware`，并标为 `shipped`。
- `architecture/README.md` 的 closure navigation 仍列出 `agent-middleware/`、`graphmemory-starter/` 等 L1 目录。

**影响：**

这与 P0-1 是同一类残留的更大范围表现。即使只修掉 `agentMiddleware -> fpHookDispatch` 这一条解析错误，架构读者仍会在 workspace/view/generated/features 多个入口看到“8 模块 / agent-middleware shipped”的旧叙述。对于 PR 140 这种“删除旧模块、回到 4 reactor modules”的重构，这会继续削弱架构权威的可信度。

**建议修复：**

围绕 4 模块 reality 做一次 architecture closure sweep：

- views 中的 development / physical 描述改为 4 reactor modules，并明确非 reactor 目录若仅保留为历史材料必须标 historical/archive；
- feature/function-point/frame 中与 `agent-middleware` 绑定的 shipped hook surface 退役、迁移 owner 或降级为 deferred/design-only；
- generated modules 通过正确生成流程刷新，避免手改 generated file；
- `architecture/docs/L1/agent-middleware/**` 若不再属于 active closure，应移入 archive 或从 active navigation 中摘除。

## 非阻断但建议同轮处理

### P2-1 `agent-runtime/README.md` 仍指导用户使用旧 bootstrap 与旧 handler 名称

**位置：**

- `agent-runtime/README.md:40-46`
- `agent-runtime/README.md:131-145`

**问题：**

README 仍把 bootable runtime entry point 写成：

```text
com.huawei.ascend.runtime.bootstrap.AgentRuntimeApplication
```

并在 extension points 中列出已不存在的 `AgentHandler` / `OpenJiuwenAgentHandler`。当前代码和 L1 文档已经改成 `AgentRuntimeHandler` / `OpenJiuwenAgentRuntimeHandler` / `RuntimeApp` / `LocalA2aRuntimeHost`。

**影响：**

这是开发者入口文档漂移。它本身不一定阻断合并，但会让 example / embedder 按旧类名接入，尤其会与 P1-1 的 boot jar 主类错误互相放大。

**建议修复：**

把 README 的 boot section 改成当前真实用法，例如 `RuntimeApp.create(handler).run(LocalA2aRuntimeHost.port(port))`，并把 extension points 更新为 `AgentRuntimeHandler`、`StreamAdapter`、`OpenJiuwenAgentRuntimeHandler` 等实际类型。

### P2-2 Generated facts provenance 与 PR body “at head” 表述仍需澄清

**位置：**

- `architecture/facts/generated/module-build.json:2-8`
- PR 140 body

**问题：**

generated facts 内容已经反映了上一轮修复，例如 fact ID `build-module/agent-service` 不再包含 `agent-middleware`。但 `_provenance.repo_commit` 仍为 `ecb6f24cd4dfb668b17e64685011764128d5ca02`，而当前 PR head 是 `89ed7c7512b1befbe6507f12b85afdbc9a32fd7f`。

考虑到 extractor 可能是在提交前对 dirty working tree 运行，这不一定是事实内容错误；本轮 `fact_layer_integrity` 也通过了。但 PR body 写 “facts re-extracted at head” 容易被理解为 provenance 已指向当前 head。建议明确这是生成流程约定，或在 PR body 中避免 “at head” 这种会与 `_provenance.repo_commit` 直接冲突的表述。

### P1-3 E2E example 缺少可复用的本地/云端模型配置交付物

**位置：**

- `examples/agent-runtime-a2a-llm-e2e/README.md:60-83`
- `examples/agent-runtime-a2a-llm-e2e/src/main/resources/application.yaml:9-15`
- `examples/agent-runtime-a2a-llm-e2e/src/test/java/com/huawei/ascend/examples/a2a/OpenJiuwenReactAgentA2aE2eTest.java:46-62`

**复核结论：**

从 outsider 视角看，这个 example 的工程实现可以跑通，但交付形态还不够。它现在依赖读者手工理解并拼装环境变量；仓库没有提供一套可复制的配置文件或脚本，让每个人填入自己本地部署的模型服务或云端 API key 后直接运行。

当前 README/default 仍默认 `http://localhost:4000/v1`。如果按团队本地 Ollama 服务做 OpenAI-compatible 映射，它可以跑通真实 A2A + openJiuwen + Ollama E2E。

本地 Ollama 状态：

```bash
curl.exe -sS --max-time 5 http://localhost:11434/v1/models -H "Authorization: Bearer ollama"
```

返回模型 `gemma4:latest`。随后执行：

```powershell
$env:SAA_SAMPLE_LLM_API_KEY='ollama'
$env:SAA_SAMPLE_OPENJIUWEN_API_BASE='http://localhost:11434/v1'
$env:SAA_SAMPLE_LLM_MODEL='gemma4:latest'
$env:SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER='openai'
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml test
```

结果：

```text
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

其中 `OpenJiuwenReactAgentA2aE2eTest` 未 skip，日志显示实际使用 `provider=openai apiBase=http://localhost:11434/v1 model=gemma4:latest`，并完成 `COMPLETED` 输出。

**影响：**

工程实现本身可以接 Ollama，也可以接云端 OpenAI-compatible API；问题是 example 没有把这件事封装成可交付、可复用、可审计的开发者入口。README 仍把 `localhost:4000/v1` 写成默认本地 gateway，且 test 只有在显式设置 `SAA_SAMPLE_LLM_API_KEY` 时才运行真实 LLM 流程；不设置时会在 agent card 校验后 skip real LLM。外部开发者按文档默认值执行，很可能会误以为 example 不可用或只跑了半个 E2E。

**建议修复：**

提供一套配置模板和脚本，而不是只在 README 里描述环境变量。建议至少包含：

- `examples/agent-runtime-a2a-llm-e2e/.env.example`：列出 `SAA_SAMPLE_LLM_API_KEY`、`SAA_SAMPLE_OPENJIUWEN_API_BASE`、`SAA_SAMPLE_LLM_MODEL`、`SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER`、`SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY`。
- `examples/agent-runtime-a2a-llm-e2e/.env.ollama.example`：团队本地 Ollama 推荐映射。
- `examples/agent-runtime-a2a-llm-e2e/.env.openai-compatible.example`：云端 OpenAI-compatible API 模板，不提交真实 key。
- `examples/agent-runtime-a2a-llm-e2e/scripts/test-e2e.ps1` 与 `scripts/test-e2e.sh`：加载指定 env 文件，执行 runtime install，再执行 example test。
- `examples/agent-runtime-a2a-llm-e2e/scripts/run-server.ps1` / `.sh` 与 `scripts/run-client.ps1` / `.sh`：用于手工验证 server/client。

Ollama 模板应至少包含：

```bash
export SAA_SAMPLE_LLM_API_KEY=ollama
export SAA_SAMPLE_OPENJIUWEN_API_BASE=http://localhost:11434/v1
export SAA_SAMPLE_LLM_MODEL=gemma4:latest
export SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER=openai
```

同时建议在 README 里明确两件事：第一，`SAA_SAMPLE_LLM_API_KEY` 必须显式设置，否则 `OpenJiuwenReactAgentA2aE2eTest` 的真实 LLM 分支会被 JUnit assumption 跳过；第二，Ollama 和云端 API 的差异只应体现在 env 文件中，运行命令保持一致。

## 验证记录

本轮实际执行：

```bash
git rev-parse HEAD
git status --short --branch
python gate/lib/sync_baseline.py --check
bash gate/check_architecture_sync.sh
git ls-files | rg "(^|/)logs/run/|\.log\.gz$|agent-runtime-spring-boot-starter-a2a|agent-middleware/|agent-execution-engine/"
./mvnw -pl agent-runtime -DskipTests package
./mvnw -pl agent-runtime -am -DskipTests install
curl.exe -sS --max-time 5 http://localhost:11434/v1/models -H "Authorization: Bearer ollama"
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml test
```

结果：

- `git rev-parse HEAD`：`89ed7c7512b1befbe6507f12b85afdbc9a32fd7f`
- `python gate/lib/sync_baseline.py --check`：通过，输出 `baseline_metrics: all derivable fields match canonical counts.`
- `bash gate/check_architecture_sync.sh`：失败，workspace gate DSL parse failed，`agentMiddleware` 元素不存在。
- `git ls-files ...`：仍发现 active L1 `architecture/docs/L1/agent-middleware/**` 文件；未发现旧 `agent-runtime-spring-boot-starter-a2a`、运行日志、`agent-execution-engine/` 生产模块路径。
- `./mvnw -pl agent-runtime -DskipTests package`：本地 124 秒超时，未得到成功/失败结论；因此本报告不把该命令作为构建失败证据，只使用静态主类缺失作为 P1-1 依据。
- `./mvnw -pl agent-runtime -am -DskipTests install`：通过，安装 parent / `agent-bus` / `agent-runtime` 到本地 Maven 仓库。
- Ollama `/v1/models`：通过，返回 `gemma4:latest`。
- `./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml test` 在 Ollama 映射环境变量下通过，16 tests / 0 failures / 0 errors / 0 skipped。

## 合并前建议清单

1. 先修 P0：让 `bash gate/check_architecture_sync.sh` 重新通过。
2. 再处理 boot artifact：删除 boot classifier 或提供真实可执行 main，并补 launch/manifest 验证。
3. 做一次 architecture closure sweep，清理 views/generated/features/frames 中的 8 模块和 `agent-middleware` active claims。
4. 更新 `agent-runtime/README.md`，避免用户继续复制旧 bootstrap / handler 类名。
5. 更新 PR body 中的 verification 结果，删除或更正当前无法本地复现的 `GATE: PASS` 声明。

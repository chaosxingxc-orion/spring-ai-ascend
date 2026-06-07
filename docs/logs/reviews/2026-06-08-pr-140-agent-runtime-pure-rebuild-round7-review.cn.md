# PR 140 第七轮复审报告：round6 修复验收与合并前残余风险

- PR: https://github.com/chaosxingxc-orion/spring-ai-ascend/pull/140
- 复审时间: 2026-06-08
- 复审对象: `redo/agent-runtime-pure-rebuild`
- 复审 head: `54919017f31cac0666456c02c1c339705edee74e`
- 基线: `origin/main@4b1673df6d15ace4db993d2508f16eb139fff8d2`
- 结论: **未发现新的 P0/P1 合并阻断项。round6 的 Rule R-M / L0 / L1 / example 配置意见已基本被接受并修复；当前 PR 可进入合并候选。建议合并前或紧随合并后处理 2 个 P2 文档/治理残余，以及 1 个 P3 sample client 鲁棒性改进。**

## 1. Findings

### P2-1. recurring-defect family 的 freshness surfaces 仍使用旧路径，gate 对真实 L1 目录仍然 fail-open

证据:

- 本轮 `bash gate/check_architecture_sync.sh` 通过，但输出了多条 WARN：`docs/L1/**/*.md` 和 `agent-*/ARCHITECTURE.md` glob matched no files，真实 L1 目录 `architecture/docs/L1/...` 没有被这些 freshness surface 追踪。
- `docs/governance/recurring-defect-families.yaml:580` 的候选 gate 仍写 `docs/L1/**/*.md` + `agent-*/ARCHITECTURE.md`。
- `docs/governance/recurring-defect-families.yaml:775-782` 的 `F-l1-architecture-grounding-gap` surfaces 仍以 `agent-*/ARCHITECTURE.md` 为核心，而当前 authority reading path 已迁移到 `architecture/docs/L1/<module>/...`。
- `docs/governance/recurring-defect-families.yaml:1390-1394`、`:1469-1477` 仍有 `docs/L1/**/*.md` / `agent-*/ARCHITECTURE.md` 的旧路径组合。

影响:

这不是 runtime blocker，但它正好解释了为什么 round5/round6 需要人工反复指出 L1 stale prose：gate 的 recurring-family freshness 信号并没有稳定覆盖当前 L1 真实目录。PR 140 这次靠人工 sweep 修掉了症状；如果不修 surface roster，同类 authority 漂移还会复发。

建议:

1. 将这些 family 的 surfaces 从旧的 `docs/L1/**/*.md` / `agent-*/ARCHITECTURE.md` 迁移到 `architecture/docs/L1/**/*.md`，必要时保留旧路径作为历史说明而不是 active surface。
2. 对 `F-design-only-mechanism-shown-as-shipped`、`F-frontmatter-claim-body-mismatch`、`F-l1-architecture-grounding-gap` 增加至少一个负例 fixture，证明 stale path 会 fail closed。
3. 将 gate 输出里的这些 WARN 作为下一轮治理债务清单，不建议作为 PR 140 runtime 合并阻断。

### P2-2. `agent-runtime` 的 canonical architecture_doc 指向 README，但 README 仍是模板残留入口

证据:

- generated facts 声明 `agent-runtime` 的 architecture doc 是 `architecture/docs/L1/agent-runtime/README.md`：`architecture/facts/generated/module-build.json:51`。
- 该 README 当前渲染残缺：
  - `architecture/docs/L1/agent-runtime/README.md:8` 标题是空模块名：`#  — L1 Design Index`。
  - `architecture/docs/L1/agent-runtime/README.md:12-20` 多个 Markdown link label 为空，例如 `[](ARCHITECTURE.md)`、`[](logical.md)`。
  - `architecture/docs/L1/agent-runtime/README.md:20` 的 feature catalog 说明缺少 source 名称：`rendered from  at W3`。
- round6 修复在 12 个 `agent-service` L1 文件里新增 banner，统一引导读者 “See Rule R-M and `architecture/docs/L1/agent-runtime/`”；因此这个 README 是新读者最自然会打开的入口。

影响:

`architecture/docs/L1/agent-runtime/ARCHITECTURE.md` 本体内容已经与当前 facts 基本一致；问题在入口索引质量。由于 `module-build.json` 将 README 标为 canonical architecture doc，空标题/空 link label 会削弱 agent-runtime 作为当前 runtime authority 的可读性，也容易让后续 agent 误以为 L1 渲染未完成。

建议:

1. 重新渲染或手工修正 `architecture/docs/L1/agent-runtime/README.md`，至少补齐标题 `agent-runtime — L1 Design Index` 和每个链接 label。
2. 如果团队认为 `ARCHITECTURE.md` 才是当前 canonical entry，则同步更新 `agent-runtime/module-metadata.yaml#architecture_doc` 并重新提取 facts，而不是只让 prose 指向目录。

### P3-1. sample client 对 CancellationException 的容错应以“已观察到 terminal event”为前置条件

证据:

- `examples/agent-runtime-a2a-llm-e2e/src/main/java/com/huawei/ascend/examples/a2a/SampleA2aClient.java:56-64` 在 error callback 中只要 `causedByCancellation(error)` 为 true 就不记录 failure，并直接 `completed.countDown()`。
- terminal 的正常完成条件在 `:50-54` / `:142-146`，只识别带 `runStatus` terminal metadata 的 `Message`。

影响:

当前 real-LLM e2e 仍会断言 completed message 与 `pong`，所以这不是假绿 blocker；本轮 `.env.ollama.example` 也已跑通。只是作为 sample/client 工具，若 SDK 在尚未收到 terminal message 时因上游中断抛出 `CancellationException`，`streamMessage` 可能返回 partial events 而不是暴露 transport failure。

建议:

增加一个 `AtomicBoolean sawTerminal`：只有在 `isTerminal(event)` 已经置位后，CancellationException 才被视为正常 stream completion；否则记录 failure。对应补一个单测覆盖 “pre-terminal cancellation is failure / post-terminal cancellation is ignored”。

## 2. round6 意见验收情况

本轮确认上一份报告中的主要问题已经被工程团队接受并基本闭合：

1. Rule R-M frontmatter 已移除 retired enforcer：`docs/governance/rules/rule-R-M.md` 从 `[E73, E74, E75, E76, E78, ...]` 改为 `[E73, E81, E82, E83, E84, E89, E90, E92, E113]`，并注明 `E74/E75/E76/E78` retired。
2. Rule R-M `.a/.b/.c` cross-reference 行已从 present-tense “Enforced by...” 改为 `RETIRED / historical`，并指向当前 `EngineDispatcherTest` / `EngineClosedLoopIntegrationTest`。
3. L0 §4 #7 已从 `com.huawei.ascend.middleware..spi..` 默认约束改为当前 SPI home：`com.huawei.ascend.bus.spi..` 与 `com.huawei.ascend.runtime.engine.spi..`。
4. `agent-service` L1 目录新增统一 status banner，明确旧 `EngineRegistry` / `ExecutorAdapter` / `HookPoint` / `RuntimeMiddleware` / `resolve(envelope)` 均为 `RETIRED / design_only / historical`。
5. `engine-dispatch-execution.md` 已有独立 status note：`architecture/docs/L1/agent-service/features/engine-dispatch-execution.md:15` 明确该 inventory 是 pre-ADR-0159 design，旧 engine/hook 设计无当前 Java type。
6. example 的默认 key 已从看起来像真实 key 的 `sk-x00550472` 改成 `sk-local-placeholder`，README 也说明本地 Ollama 忽略 Authorization header，云端 API 必须填自己的 key。

## 3. Facts-layer 对照

按 AGENTS.md / Rule G-15，本轮先对照 generated facts，再判断 prose 是否可信：

- `architecture/facts/generated/code-symbols.json` 当前事实层包含：
  - `code-symbol/com-huawei-ascend-runtime-engine-agentruntimehandlerregistry`
  - `code-symbol/com-huawei-ascend-runtime-engine-enginedispatcher`
  - `code-symbol/com-huawei-ascend-runtime-engine-spi-agentruntimehandler`
- `architecture/facts/generated/tests.json` 当前事实层包含：
  - `test/com-huawei-ascend-runtime-engine-enginedispatchertest`
  - `com.huawei.ascend.runtime.engine.EngineClosedLoopIntegrationTest`
- `architecture/facts/generated/contract-surfaces.json` 当前事实层显示：
  - `contract-yaml/engine-envelope` status = `design_only`
  - `contract-yaml/engine-hooks` status = `design_only`
- `architecture/facts/generated/module-build.json:51` 当前事实层显示 `agent-runtime` 的 architecture doc 是 `architecture/docs/L1/agent-runtime/README.md`。

## 4. Outsider e2e example 复核

本轮从 outsider 视角重新执行了本地 Ollama 路径：

```text
cd examples/agent-runtime-a2a-llm-e2e
bash scripts/test-e2e.sh .env.ollama.example
```

观察到脚本加载的配置：

```text
provider=openai
apiBase=http://localhost:11434/v1
model=gemma4:latest
```

结果：

```text
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

结论：本地 Ollama 映射路径可跑通；`.env.example` / `.env.ollama.example` / `.env.openai-compatible.example` + Bash/PowerShell 脚本已经满足“团队成员可填本地模型或云端 API key”的基本要求。

## 5. Verification record

```text
GitHub PR metadata
PR #140 head = 54919017f31cac0666456c02c1c339705edee74e
state = open, mergeable = true, draft = false
```

```text
git status --short --branch --untracked-files=all
## redo/agent-runtime-pure-rebuild...origin/redo/agent-runtime-pure-rebuild
```

```text
bash gate/check_architecture_sync.sh
GATE: PASS
ARCHITECTURE WORKSPACE: PASS
workspace projection: 370 nodes / 201 edges
```

```text
python gate/lib/sync_baseline.py --check
baseline_metrics: all derivable fields match canonical counts.
```

```text
./mvnw -Pquality verify
BUILD SUCCESS
Reactor: parent, BoM, agent-bus, agent-runtime, agent-service all SUCCESS
```

```text
Select-String agent-runtime/target/checkstyle-result.xml '<error'
no matches
```

```text
examples/agent-runtime-a2a-llm-e2e
bash scripts/test-e2e.sh .env.ollama.example
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 6. Merge recommendation

建议：

- **可以合并候选**：没有新的 P0/P1；runtime verification 与 example e2e 均通过。
- **合并前可选修**：P3-1 的 sample client cancellation guard，改动小且能让示例更严谨。
- **合并后治理跟进**：P2-1 / P2-2 更偏 authority hygiene，建议作为紧随 PR 140 后的一次小 PR 或同分支补丁处理。


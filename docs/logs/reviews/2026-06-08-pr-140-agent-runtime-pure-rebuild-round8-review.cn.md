# PR 140 第八轮复审报告：round7 修复验收、e2e example 复核与合并建议

- PR: https://github.com/chaosxingxc-orion/spring-ai-ascend/pull/140
- 复审时间: 2026-06-08
- 复审对象: `redo/agent-runtime-pure-rebuild`
- 当前 head: `89d931a78ad0a5d91a85197db6fbcaf8ef5880f7`
- 基线: `origin/main@4b1673df6d15ace4db993d2508f16eb139fff8d2`
- 结论: **未发现新的 P0/P1/P2 合并阻断项。上一轮 round7 的 P2/P3 意见已经被工程团队接受并基本闭环；PR 140 当前可以作为合并候选。** 本轮仅保留 2 个 P3 级文档卫生/发布说明项，建议合并前顺手修正，若赶合并窗口也可以作为合并后治理尾项处理。

## 1. Findings

### P3-1. recurring-defect 的候选 gate 文案仍残留 `docs/L1/**/*.md` 旧路径

证据:

- `docs/governance/recurring-defect-families.yaml:585` 的 candidate gate-rule 仍写着 grep `docs/L1/**/*.md` + `architecture/docs/L1/*/ARCHITECTURE.md`。
- `docs/governance/recurring-defect-families.yaml:1319` 的 candidate gate-rule 仍写着 grep `architecture/docs/L1/*/ARCHITECTURE.md` + `docs/L1/**/*.md`。
- `docs/governance/recurring-defect-families.yaml:1361` 的 candidate gate-rule 仍写着 pattern-match `architecture/docs/L1/*/ARCHITECTURE.md` + `docs/L1/**/*.md`。

影响:

这不是当前 gate 的失败点，也不再是 P2 blocker。round7 已经把 active freshness surfaces 迁移到了 `architecture/docs/L1/**/*.md` / `architecture/docs/L1/*/ARCHITECTURE.md`，本轮 `bash gate/check_architecture_sync.sh` 中上一轮的 L1 旧路径 WARN 已经消失。剩余问题是候选规则文本仍带旧路径，未来如果团队直接复制这些 candidate gate-rule 进入真实 gate，可能重新引入 `docs/L1` 这个已经不存在的盲区。

建议:

将上述 candidate gate-rule 的 prose 也同步到 `architecture/docs/L1/**/*.md`。历史叙述中提到 `docs/L1/agent-service` 的地方可以保留为历史背景，但候选 gate / 待实现防线的路径应只指向当前 authority surface。

### P3-2. PR body 的 example 验证计数仍是旧的 `16/16`

证据:

- GitHub PR body 的 Verification 段仍写着 example `16/16`。
- 当前 head 新增了 `SampleA2aClientTest.cancellationIsNormalCompletionOnlyAfterTerminalEvent`，本轮实际执行 `bash scripts/test-e2e.sh .env.ollama.example` 后，Surefire 报告显示 example suite 为 17 个测试且没有 skip：
  - `SampleA2aClientTest`: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`
  - `OpenJiuwenReactAgentA2aE2eTest`: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`
  - example 模块总计: `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`

影响:

这不是代码缺陷，但 PR body 现在低报了测试数量，也没有反映 round7 之后的 sample-client 新单测。对于评审者和发布记录来说，最终 PR body 最好与实际验收口径一致。

建议:

合并前将 PR body 的 example 验证从 `16/16` 更新为 `17/17`，并补一句说明 round7 新增了 terminal-event 后 cancellation 容错测试。

## 2. 上一轮意见闭环验收

### P2-1 recurring-defect L1 surfaces: 已闭环

round7 把 active surface roster 从旧的 `docs/L1/**/*.md` / `agent-*/ARCHITECTURE.md` 迁移到当前 authority path:

- `docs/governance/recurring-defect-families.yaml:202`: `architecture/docs/L1/*/ARCHITECTURE.md`
- `docs/governance/recurring-defect-families.yaml:577`: `architecture/docs/L1/*/ARCHITECTURE.md body text`
- `docs/governance/recurring-defect-families.yaml:579`: `architecture/docs/L1/**/*.md`
- `docs/governance/recurring-defect-families.yaml:781`: `architecture/docs/L1/*/ARCHITECTURE.md`

验收结果:

- `bash gate/check_architecture_sync.sh` 通过。
- 上一轮与 L1 旧路径相关的 `glob matched no files` WARN 已经消失。
- gate 仍有一些非 L1 的历史 WARN，例如已删除 Java path、`agent-middleware`、`docs/L2/**/*.md` 等。这些属于更宽的治理债，不构成 PR 140 本轮 runtime 合并 blocker。

### P2-2 `agent-runtime` / `agent-bus` L1 README 模板残留: 已闭环

当前两个 canonical README 已不再是空标题/空 label:

- `architecture/docs/L1/agent-runtime/README.md:8-20` 已渲染为 `agent-runtime` 的 L1 Design Index，并补齐 `ARCHITECTURE.md`、4+1 views、`spi-appendix.md`、`features/README.md` 链接。
- `architecture/docs/L1/agent-bus/README.md:8-20` 已渲染为 `agent-bus` 的 L1 Design Index，并补齐同样的导航入口。

facts-layer 对照:

- `architecture/facts/generated/module-build.json` 的 `build-module/agent-runtime` 仍声明 `architecture_doc = architecture/docs/L1/agent-runtime/README.md`。
- `architecture/facts/generated/module-build.json` 的 `build-module/agent-bus` 仍声明 `architecture_doc = architecture/docs/L1/agent-bus/README.md`。

因此 README 作为 canonical entry 的可读性问题已经闭合。

### P3-1 sample client cancellation 容错: 已闭环

当前实现已经把 `CancellationException` 的容错限定在“已收到 terminal event 之后”:

- `examples/agent-runtime-a2a-llm-e2e/src/main/java/com/huawei/ascend/examples/a2a/SampleA2aClient.java:47` 新增 `AtomicBoolean sawTerminal`。
- `examples/agent-runtime-a2a-llm-e2e/src/main/java/com/huawei/ascend/examples/a2a/SampleA2aClient.java:54-56` 在 `isTerminal(event)` 后设置 `sawTerminal = true` 并完成 latch。
- `examples/agent-runtime-a2a-llm-e2e/src/main/java/com/huawei/ascend/examples/a2a/SampleA2aClient.java:59-68` 的 error callback 只在 `isFailureError(error, sawTerminal.get())` 为 true 时记录 failure。
- `examples/agent-runtime-a2a-llm-e2e/src/test/java/com/huawei/ascend/examples/a2a/SampleA2aClientTest.java:103-115` 新增单测覆盖 post-terminal cancellation、pre-terminal cancellation、nested cancellation、non-cancellation error 四种情况。

该修复把 sample client 从“任何 cancellation 都吞掉”收窄为“只有 terminal 后 SDK 正常取消 SSE subscription 才吞掉”，行为更符合 outsider 使用时对 partial stream / transport break 的直觉。

## 3. Outsider e2e example 复核

本轮按“外部新同学拿到 example 是否能跑通”的角度重新检查。结论是: **现在 example 已经提供了一套足够清晰的可配置文件 + 脚本，可以映射到本地 Ollama，也可以切换到云端 OpenAI-compatible API。**

关键依据:

- `examples/agent-runtime-a2a-llm-e2e/.env.example` 列出所有可配置变量，并说明 `.env` 是 gitignored，用户需要自己填模型服务/API Key。
- `examples/agent-runtime-a2a-llm-e2e/.env.ollama.example` 明确映射本地 Ollama 的 OpenAI-compatible `/v1` surface:
  - `SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER=openai`
  - `SAA_SAMPLE_OPENJIUWEN_API_BASE=http://localhost:11434/v1`
  - `SAA_SAMPLE_LLM_MODEL=gemma4:latest`
  - `SAA_SAMPLE_LLM_API_KEY=ollama`，作为非空占位以启用 real-LLM 分支。
- `examples/agent-runtime-a2a-llm-e2e/.env.openai-compatible.example` 提供云端 OpenAI-compatible API 模板，不提交真实 key。
- `examples/agent-runtime-a2a-llm-e2e/scripts/test-e2e.sh` 支持 `bash scripts/test-e2e.sh [env-file]`，会加载 env 文件、安装当前 `agent-runtime` 到本地 Maven repo，然后运行 example E2E suite。
- `examples/agent-runtime-a2a-llm-e2e/README.md` 的 Quick start 已经采用“同一脚本 + 不同 env 文件”的使用方式，符合“让大家填充自己本地部署模型或云端 API Key”的预期。

本轮实跑命令:

```bash
cd examples/agent-runtime-a2a-llm-e2e
bash scripts/test-e2e.sh .env.ollama.example
```

脚本加载到的关键配置:

```text
provider=openai
apiBase=http://localhost:11434/v1
model=gemma4:latest
```

结果:

```text
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

其中 `OpenJiuwenReactAgentA2aE2eTest` 本轮为 `Skipped: 0`，说明不是只跑了 agent-card / 非 LLM 分支，而是真正通过本地 Ollama/OpenAI-compatible endpoint 跑通了 real-LLM e2e。

注意事项:

- outsider 仍需要本机先具备可访问的模型服务，例如执行过 `ollama serve` 和 `ollama pull gemma4:latest`。这属于 example 的合理前置条件，README / `.env.ollama.example` 已经说明。
- 如果团队希望进一步降低失败率，可以在脚本开头增加一个可选 preflight，例如 curl `${SAA_SAMPLE_OPENJIUWEN_API_BASE}/models`，提前输出“模型服务不可达 / model 未拉取”的明确提示。但这属于体验增强，不是当前 PR blocker。

## 4. Facts-layer 对照

按 AGENTS.md / Rule G-15，本轮先对照 generated facts，再判断 prose 和代码是否可信:

- `architecture/facts/generated/code-symbols.json` 当前包含:
  - `code-symbol/com-huawei-ascend-runtime-engine-enginedispatcher`
  - `code-symbol/com-huawei-ascend-runtime-engine-agentruntimehandlerregistry`
  - `code-symbol/com-huawei-ascend-runtime-engine-spi-agentruntimehandler`
- `architecture/facts/generated/tests.json` 当前包含:
  - `test/com-huawei-ascend-runtime-engine-enginedispatchertest`
  - `com.huawei.ascend.runtime.engine.EngineClosedLoopIntegrationTest`
- `architecture/facts/generated/module-build.json` 当前包含:
  - `build-module/agent-runtime`
  - `build-module/agent-bus`

这些 facts 与 PR 当前“agent-runtime 作为 framework-neutral runtime library、agent-bus 作为事件/命令边界、Spring Boot confined to host/example”的主张没有发现新的矛盾。

## 5. 本轮验证记录

已执行:

```bash
bash gate/check_architecture_sync.sh
python gate/lib/sync_baseline.py --check
./mvnw -Pquality verify
cd examples/agent-runtime-a2a-llm-e2e && bash scripts/test-e2e.sh .env.ollama.example
```

结果摘要:

- canonical architecture gate: `GATE: PASS`，`ARCHITECTURE WORKSPACE: PASS`，workspace baseline 为 370 elements / 201 relationships。
- baseline check: `baseline_metrics: all derivable fields match canonical counts.`
- Maven quality verify: reactor `BUILD SUCCESS`；`agent-runtime` 的 IT 也随 Failsafe 跑过。
- checkstyle: `agent-runtime/target/checkstyle-result.xml` 未发现 `<error`。
- example e2e: 17 tests，0 failures，0 errors，0 skipped；real-LLM branch 通过本地 Ollama 映射跑通。
- 工作区状态: `git status --short --branch --untracked-files=all` 在写入本报告前为干净，当前仅新增本报告文件。

## 6. 合并建议

建议将 PR 140 标记为 **approve / merge candidate**。

合并前建议顺手处理:

1. 把 PR body 的 example 验证计数从 `16/16` 更新为 `17/17`。
2. 把 `docs/governance/recurring-defect-families.yaml` 中 candidate gate-rule 的 `docs/L1/**/*.md` prose 残留改成 `architecture/docs/L1/**/*.md`。

如果当前合并窗口紧，这两个 P3 项也可以进入合并后治理清单；它们不影响 runtime 行为、architecture gate、quality verify 或 outsider e2e 可运行性。

# PR 147 post-response 复审报告：agent-runtime five-module flatten

- PR: https://github.com/chaosxingxc-orion/spring-ai-ascend/pull/147
- 复审日期: 2026-06-08
- 本轮复审类型: 接受上一轮 review 后的 post-response 复审
- Base: `origin/main@4bf47278bc9a0803824d47ea642e3951a1683b8d`
- Head reviewed: `bf5b18696822e62cc6fff8c0a22eef2d06c18cf1`
- GitHub 状态: open, mergeable, non-draft
- 结论: **Approve / merge-ready。** 本轮没有发现新的 P0/P1/P2/P3 actionable findings。上一轮剩余的 PR body P3 也已由团队更新关闭。

## 1. Findings

### 未发现新的 actionable finding

本轮没有发现需要阻塞合并或要求再次修改的 P0/P1/P2/P3 问题。

重点复核结论:

- 代码 head 未变化，仍为 `bf5b18696822e62cc6fff8c0a22eef2d06c18cf1`。
- PR body 已补充 "Scope — runtime code AND its authority/fact surfaces"，明确列出 `architecture/facts/generated/code-symbols.json`、`tests.json`、`architecture/docs/L1/agent-runtime/ARCHITECTURE.md`、`agent-runtime/README.md`、package-info 和 review logs。
- PR body 已将 Mockito 描述修正为作者本地 WSL 的 ByteBuddy self-attach 限制，同时说明 CI 和 reviewer 环境已验证相关测试。
- 本轮重新跑了 build、quality、fact-layer、architecture gate、baseline、checkstyle error scan 和 Ollama e2e，均为通过状态。

## 2. 上一轮剩余 P3 闭环

### P3-1. PR body 低估实际变更范围 - 已关闭

上一份 follow-up review 指出 PR body 仍把范围描述为几乎只改 `agent-runtime`，没有反映后来接受 review 后新增的 facts / L1 / README / package-info / review log 更新。

本轮 GitHub PR body 已更新，现包含:

- "Scope — runtime code AND its authority/fact surfaces"
- fact-layer 说明: `architecture/facts/generated/code-symbols.json` + `tests.json` 已由 `ExtractFactsCli` 重新生成
- L1 说明: `architecture/docs/L1/agent-runtime/ARCHITECTURE.md` package table 已更新到 flat layout，`runtime.schema` 已迁移为 `runtime.common`，并补充 G12 说明
- README/package-info 说明: extension-point FQN 和 dependency rules 已更新到 flat paths
- review log 说明: `docs/logs/reviews/2026-06-08-pr-147-*.md`
- example 说明: `examples/agent-runtime-a2a-llm-e2e` 仅为 relocated engine types 的机械 import-path fixup

同时，PR body 的 verification 区域也已补充:

- `FactLayerByteIdentityIT` green
- canonical architecture gate `PASS`
- baseline check `PASS`
- A2A ping/pong e2e green
- reviewer 环境的 `./mvnw clean verify` + `-Pquality verify` 已通过
- local-Ollama example e2e 已通过，真实 LLM branch 未 skip
- Mockito warning 被描述为作者本地 WSL 限制，而不是全局不能本地验证

判断: 上一轮 P3 已关闭，无需再次修改。

## 3. 权威事实层与文档一致性复查

根据 AGENTS.md / Rule G-15，本轮仍先检查 generated facts，再对照 L1/README/package-info。

### 3.1 Generated facts

旧包名扫描结果:

```text
rg "access\.protocol|access\.config|access\.core|access\.model|engine\.command|engine\.event|engine\.model|engine\.port|runtime\.schema|session\.core|session\.model|session\.store|queue\.config" \
  architecture/facts/generated/code-symbols.json architecture/facts/generated/tests.json
```

结果: `architecture/facts/generated/code-symbols.json` 和 `architecture/facts/generated/tests.json` 没有旧包事实残留。

代表性 current facts:

- `fact_id=code-symbol/com-huawei-ascend-runtime-access-a2a-a2ajsonrpchandler`
- `fact_id=code-symbol/com-huawei-ascend-runtime-engine-engineevent`
- `fact_id=code-symbol/com-huawei-ascend-runtime-engine-taskcontrolclient`
- `fact_id=code-symbol/com-huawei-ascend-runtime-engine-accesslayerclient`
- `fact_id=test/com-huawei-ascend-runtime-architecture-runtimepackageboundarytest`
- `fact_id=test/com-huawei-ascend-runtime-engine-engineclosedloopintegrationtest`

### 3.2 L1 / README / package-info

旧包名扫描范围:

- `agent-runtime/README.md`
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md`
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/package-info.java`
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/api/package-info.java`
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/package-info.java`
- generated facts

结果只剩一处 `engine.port` 文本:

```text
architecture/docs/L1/agent-runtime/ARCHITECTURE.md:194:
`engine.TaskControlClient` + `engine.AccessLayerClient` (engine root, not `engine.port`) — intra-service, not SPI.
```

这是负向澄清，不是 stale package claim。

判断: generated facts、人写 L1、README 和 package-info 之间已经一致。

## 4. E2E example outsider 视角复查

本轮继续按新使用者视角检查 `examples/agent-runtime-a2a-llm-e2e` 是否能通过可配置文件或脚本映射到本地模型服务。

当前 example 具备:

- `.env.ollama.example`: 本地 Ollama OpenAI-compatible `/v1` 配置
  - `SAA_SAMPLE_OPENJIUWEN_API_BASE=http://localhost:11434/v1`
  - `SAA_SAMPLE_LLM_MODEL=gemma4:latest`
  - `SAA_SAMPLE_LLM_API_KEY=ollama`
- `.env.openai-compatible.example`: 云端或其他 OpenAI-compatible API 模板
- `scripts/test-e2e.sh`: 加载 env 文件、安装当前 `agent-runtime` snapshot、运行 example tests
- `README.md` / `README.cn.md`: 说明 `.env` 不会被 Maven/Spring 自动加载，必须通过 helper script 或手工 export；真实 LLM test 仅在 API key 非空时运行

本轮实跑:

```text
cd examples/agent-runtime-a2a-llm-e2e
bash scripts/test-e2e.sh .env.ollama.example

loaded env: .env.ollama.example  (provider=openai apiBase=http://localhost:11434/v1 model=gemma4:latest)
BUILD SUCCESS
Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
OpenJiuwenReactAgentA2aE2eTest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

判断: example 当前可通过配置模板和脚本对接本地 Ollama，也可替换为云端 OpenAI-compatible API；本轮真实 LLM 分支实际执行且未 skip。

## 5. 本轮验证证据

所有命令均在 head `bf5b18696822e62cc6fff8c0a22eef2d06c18cf1` 上重新执行。

```text
git fetch origin main simplify/agent-runtime-five-module-flatten
git rev-parse HEAD
git rev-parse origin/simplify/agent-runtime-five-module-flatten

HEAD == origin/simplify/agent-runtime-five-module-flatten == bf5b18696822e62cc6fff8c0a22eef2d06c18cf1
```

```text
./mvnw clean verify
BUILD SUCCESS
agent-runtime surefire: 98 tests, 0 failures, 0 errors, 0 skipped
agent-runtime failsafe: 7 tests, 0 failures, 0 errors, 0 skipped
```

```text
./mvnw -Pquality verify
BUILD SUCCESS
```

```text
./mvnw -f tools/architecture-workspace/pom.xml -Dtest=FactLayerByteIdentityIT#committedFactsAreByteIdenticalToFreshExtractorOutput test
BUILD SUCCESS
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
ExtractFactsCli: ok (commit=bf5b18696822e62cc6fff8c0a22eef2d06c18cf1)
```

```text
bash gate/check_architecture_sync.sh
GATE: PASS
ARCHITECTURE WORKSPACE: PASS
```

说明: gate 输出了既有 advisory/warn，包括 freshness advisory 和若干 stale freshness surface warn；没有 blocking failure。

```text
python gate/lib/sync_baseline.py --check
baseline_metrics: all derivable fields match canonical counts.
```

```text
Select-String -Path agent-runtime/target/checkstyle-result.xml -Pattern '<error'
no matches
```

```text
cd examples/agent-runtime-a2a-llm-e2e
bash scripts/test-e2e.sh .env.ollama.example
BUILD SUCCESS
Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
```

## 6. 非阻塞观察

- `A2aResponseMapper` 仍有 A2A SDK deprecation warning；当前不影响 build / quality / checkstyle，可作为后续 SDK migration 跟踪。
- Mockito inline mocking 仍输出 dynamic Java-agent warning；当前测试通过，未来 JDK 默认禁用 dynamic attach 时可能需要显式 Java agent 配置。
- `RuntimePackageBoundaryTest` 继续提供五层扁平结构保护，并使用 `allowEmptyShould(false)` 避免包迁移后规则空集误绿。

## 7. 最终建议

建议 approve 并合并。当前 PR 的代码、generated facts、L1/README/package-info、PR body 说明、架构 gate、quality profile、baseline 和本地 Ollama e2e 都已经对齐；本轮没有剩余 actionable review item。

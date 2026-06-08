# PR 147 复审报告：agent-runtime five-module flatten follow-up

- PR: https://github.com/chaosxingxc-orion/spring-ai-ascend/pull/147
- 复审日期: 2026-06-08
- Base: `origin/main@4bf47278bc9a0803824d47ea642e3951a1683b8d`
- 本次复审 head: `bf5b18696822e62cc6fff8c0a22eef2d06c18cf1`
- 复审范围: 134 个变更文件，覆盖 `agent-runtime`、`architecture/docs/L1/agent-runtime`、`architecture/facts/generated`、上一轮 review log，以及 `examples/agent-runtime-a2a-llm-e2e`。
- 结论: **建议 Approve / merge-ready。** 本轮没有发现新的 P0/P1/P2 阻塞问题。上一轮 review 的事实层、L1/README、package-info 漂移问题已经在最新 head 闭环。本轮仅保留 1 个 P3 级别的 PR body 更新建议。

## 1. Findings

### 未发现 P0/P1/P2 阻塞问题

本轮复审没有发现会阻断合并的代码正确性、契约、架构事实层或测试覆盖回归。

### P3-1. PR body 仍然低估了已接受 review 后新增的修复范围

证据:

- PR body 仍写着 scope strictly `agent-runtime`，并称 "no other module changed (`examples` got 3 mechanical import-path fixups only)"。
- 但当前 PR 文件列表实际还包含:
  - `architecture/docs/L1/agent-runtime/ARCHITECTURE.md`
  - `architecture/facts/generated/code-symbols.json`
  - `architecture/facts/generated/tests.json`
  - `docs/logs/reviews/2026-06-08-pr-147-agent-runtime-five-module-flatten-review.cn.md`
  - `agent-runtime/README.md`
- PR body 仍写着 Mockito-based edge tests 无法在本地跑、依赖 CI；但本轮在当前环境下执行 `./mvnw clean verify` 时，相关 Mockito 单测已经实际跑通，只剩未来 JDK dynamic-agent warning。

影响:

这不是合并阻塞项，但会让 reviewer 误以为本 PR 只动了 runtime 代码和少量 example import，从而跳过本轮实际很关键的 authority surface: generated facts、L1 architecture、README/package-info 和 review log。

建议:

合并前更新 PR body，说明接受上一轮 review 后新增了 fact-layer 输出刷新、L1/README/package-info 权威文档同步，以及 review log。若作者自己的 WSL 环境仍无法跑 Mockito self-attach，建议改成“作者本地 WSL 环境限制”，不要写成所有本地验证都不可用。

## 2. 上一轮问题闭环检查

### P1-1. `architecture/facts/generated` 未随 flatten 刷新 - 已关闭

上一轮发现 generated facts 仍描述 pre-flatten 包结构。本轮在 `bf5b186...` 上复查，针对 `access.protocol`、`engine.command`、`engine.event`、`engine.port`、`engine.model`、`session.store` 等旧包名的 grep，在 `code-symbols.json` / `tests.json` 中没有发现旧事实条目。

当前事实层已经指向 flatten 后的布局，代表性 fact id 包括:

- `fact_id=code-symbol/com-huawei-ascend-runtime-access-a2a-a2ajsonrpchandler`
- `fact_id=code-symbol/com-huawei-ascend-runtime-engine-engineevent`
- `fact_id=code-symbol/com-huawei-ascend-runtime-engine-taskcontrolclient`
- `fact_id=code-symbol/com-huawei-ascend-runtime-engine-accesslayerclient`
- `fact_id=test/com-huawei-ascend-runtime-architecture-runtimepackageboundarytest`
- `fact_id=test/com-huawei-ascend-runtime-engine-engineclosedloopintegrationtest`

验证结果:

```text
./mvnw -f tools/architecture-workspace/pom.xml -Dtest=FactLayerByteIdentityIT#committedFactsAreByteIdenticalToFreshExtractorOutput test
BUILD SUCCESS
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
ExtractFactsCli: ok (commit=bf5b18696822e62cc6fff8c0a22eef2d06c18cf1)
```

补充说明: `code-symbols.json` 和 `tests.json` 的 provenance 字段仍嵌入上一提交 SHA，但 `ExtractFactsCli --check` 会按设计规范化 40 位 commit SHA 后再比较内容。因此这里不是事实内容漂移，byte-identity 内容检查已经通过。

### P1-2. L1 architecture 仍描述旧 `engine.command` / `engine.port` / `runtime.schema` - 已关闭

`architecture/docs/L1/agent-runtime/ARCHITECTURE.md` 已更新为当前 flatten 后结构:

- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:42-53` 的 package table 现在描述 `runtime.engine` root 承载 dispatch / internals / events / ports，并保留 `runtime.engine.api`、`runtime.engine.spi`、`runtime.engine.openjiuwen` 等边界子包。
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:188-191` 明确说明 engine dispatch internals 位于 `runtime.engine` root，outbound ports 是 `engine.TaskControlClient` + `engine.AccessLayerClient`，不是 `engine.port`。

当前唯一剩余的 `engine.port` 文本是负向说明 "not `engine.port`"，不是旧包路径声明。

### P2-1. `agent-runtime/README.md` extension-point FQN 过期 - 已关闭

`agent-runtime/README.md:144-151` 已列出当前 FQN:

- `com.huawei.ascend.runtime.app.RuntimeApp` / `LocalA2aRuntimeHost`
- `com.huawei.ascend.runtime.access.a2a.A2aAccessProperties`
- `com.huawei.ascend.runtime.access.AccessLayerConfiguration`
- `com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler`
- `com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler`

这些路径与当前代码和 generated facts 一致。

### P2-2. `package-info.java` 仍引用旧边界 - 已关闭

`agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/package-info.java:4-9` 现在说明外部 provider 只实现 `AgentRuntimeHandler`，engine inbound API 在 `engine.api`，内部 command runtime 和 outbound clients 都在 `engine` root。

`agent-runtime/src/main/java/com/huawei/ascend/runtime/access/package-info.java:21` 已引用当前的 `com.huawei.ascend.runtime.engine.AccessLayerClient`，不再指向已删除的 `engine.port`。

## 3. 从 outsider 视角检查 e2e example

我按“新接手的人需要填自己本地模型或云端 API Key”的视角检查并实跑了 `examples/agent-runtime-a2a-llm-e2e`。

当前 example 提供的配置形态:

- `.env.ollama.example` 映射到本地 Ollama 的 OpenAI-compatible `/v1`:
  - `SAA_SAMPLE_LLM_API_KEY=ollama`
  - `SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER=openai`
  - `SAA_SAMPLE_OPENJIUWEN_API_BASE=http://localhost:11434/v1`
  - `SAA_SAMPLE_LLM_MODEL=gemma4:latest`
  - `SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY=false`
- `.env.openai-compatible.example` 可用于云端或其他 OpenAI-compatible gateway。
- `scripts/test-e2e.sh` 会加载传入 env 文件，安装当前 `agent-runtime` snapshot，再运行 example 测试模块。
- `README.md:103-125` 说明了“复制模板、填值、同一条命令运行”的流程。
- `README.md:127-134` 说明 `.env` 不会被 Maven/Spring Boot 自动加载，并解释真实 LLM 分支只有在 `SAA_SAMPLE_LLM_API_KEY` 非空时才运行。
- `README.md:146-154` 说明 env 文件、显式 shell 环境变量、Spring Boot 默认值之间的生效顺序。

实跑结果:

```text
cd examples/agent-runtime-a2a-llm-e2e
bash scripts/test-e2e.sh .env.ollama.example

loaded env: .env.ollama.example (provider=openai apiBase=http://localhost:11434/v1 model=gemma4:latest)
BUILD SUCCESS
Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
OpenJiuwenReactAgentA2aE2eTest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

结论:

example 现在不是“写死某个服务”的形态，而是一套可配置模板 + 脚本。它能通过 `http://localhost:11434/v1` 对接本地 Ollama，也允许用户替换 env 文件值，接入自己的本地部署模型或云端 OpenAI-compatible API Key。本轮本地 Ollama 路径真实跑通，真实 LLM 分支没有被 skip。

## 4. 本轮验证命令

以下命令均在 head `bf5b18696822e62cc6fff8c0a22eef2d06c18cf1` 上执行。

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
```

```text
bash gate/check_architecture_sync.sh
GATE: PASS
ARCHITECTURE WORKSPACE: PASS
```

说明: architecture gate 输出了既有 advisory / warn，例如 freshness advisory 和 stale freshness surface warn；这些不是 blocking failure。

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

## 5. 剩余风险和非阻塞观察

- `A2aResponseMapper` 仍触发 Java 编译器 deprecation warning。当前 quality / checkstyle 不失败，但后续可以跟踪 A2A SDK/API 的替代写法。
- Mockito inline mocking 在当前 JDK 下会输出 dynamic Java-agent warning。本轮测试实际通过，但未来 JDK 默认行为变化时，可能需要把 Mockito 配成显式 Java agent。
- PR 是大范围 flatten refactor。当前新增的 `RuntimePackageBoundaryTest` 有价值，因为它使用 `allowEmptyShould(false)`，能避免包名漂移后结构规则 vacuously green。

## 6. 最终建议

建议 approve。合并前最好顺手更新 PR body，反映事实层、L1/README/package-info 和 example 配置模板的实际变更范围；但从代码、事实层、架构 gate、quality profile 和本地 Ollama e2e 验证看，本 PR 已经达到 merge-ready 状态。

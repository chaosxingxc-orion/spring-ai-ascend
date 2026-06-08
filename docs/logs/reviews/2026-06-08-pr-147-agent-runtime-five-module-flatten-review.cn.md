# PR 147 复审报告：agent-runtime five-module flatten

- PR: https://github.com/chaosxingxc-orion/spring-ai-ascend/pull/147
- 复审时间: 2026-06-08
- 复审对象: `simplify/agent-runtime-five-module-flatten`
- 当前 head: `3a3a6f133d5b1d989e34cd66ce58717217c99480`
- 基线: `origin/main@4bf47278bc9a0803824d47ea642e3951a1683b8d`
- 结论: **建议 request changes。** 运行时代码、clean build、quality profile、A2A/Ollama example e2e 都是绿的；但 PR 的核心目标是包结构 flatten，当前提交没有同步 generated facts 与 L1/README/package-info 权威材料，导致事实层和架构层仍在描述旧包结构。这会直接误导后续 AI agent、开发者 onboarding 和架构 gate 的事实判断。

## 1. Findings

### P1-1. `architecture/facts/generated` 未随包结构 flatten 刷新，Rule G-15 事实层已经漂移

证据:

- 本轮 clean build 后执行事实层 byte-identity 检查失败:

```text
./mvnw -f tools/architecture-workspace/pom.xml -Dtest=FactLayerByteIdentityIT#committedFactsAreByteIdenticalToFreshExtractorOutput test

ERROR: --check: content drift detected on
D:\chao_workspace\spring-ai-ascend\architecture\facts\generated\code-symbols.json
```

- checked-in facts 仍含旧包名:
  - `architecture/facts/generated/code-symbols.json:931`: `com.huawei.ascend.runtime.access.protocol.a2a.jsonrpc.A2aJsonRpcHandler`
  - `architecture/facts/generated/code-symbols.json:1689`: `com.huawei.ascend.runtime.engine.command.EngineCommandEventFactory`
  - `architecture/facts/generated/code-symbols.json:2231`: `com.huawei.ascend.runtime.engine.port.AccessLayerClient`
  - `architecture/facts/generated/code-symbols.json:2251`: `com.huawei.ascend.runtime.engine.port.TaskControlClient`
  - `architecture/facts/generated/tests.json:110`: `com.huawei.ascend.runtime.access.protocol.a2a.jsonrpc.A2aJsonRpcHandlerTest`
  - `architecture/facts/generated/tests.json:270`: `com.huawei.ascend.runtime.engine.command.EngineCommandEventFactoryTest`
- 同一 head 的源码已不存在这些旧目录:
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/command`: absent
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/port`: absent
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/model`: absent
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol`: absent

影响:

AGENTS.md / Rule G-15 明确要求任何代码、契约、测试、依赖、运行时行为的事实判断都先读 `architecture/facts/generated/*.json`，并且 generated facts 是代码事实权威。PR 147 合并后，后续 agent 会从 facts 读到已经不存在的包和类，再去判断 L1、契约和测试覆盖；这会把当前 flatten 的主要收益反向抵消掉。

特别需要注意: `bash gate/check_architecture_sync.sh` 本轮仍然 PASS，说明 canonical gate 目前没有覆盖这个 byte-identity 断言。不能用主 gate 绿灯替代 `FactLayerByteIdentityIT` 对 Rule G-15.c 的检查。

建议:

1. 在 clean compile 后用 extractor 重新生成 `architecture/facts/generated/code-symbols.json` 与 `architecture/facts/generated/tests.json`，必要时同步其他 generated fact 文件。
2. 将刷新后的 generated facts 纳入 PR。
3. 修复后必须重新跑:

```bash
./mvnw clean verify
./mvnw -f tools/architecture-workspace/pom.xml -Dtest=FactLayerByteIdentityIT#committedFactsAreByteIdenticalToFreshExtractorOutput test
bash gate/check_architecture_sync.sh
```

### P1-2. L1 architecture authority 仍描述旧 `engine.command` / `engine.port` / `runtime.schema` 结构

证据:

- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:46` 仍说 `EngineWorker` + `engine.command.*` 是当前 engine dispatch。
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:48` 仍说 outbound ports 在 `runtime.engine.port`。
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:54` 仍列 `runtime.schema`，并把 `AgentRequest` / `AgentResponse` / `RunStatus` 放在那里。
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:93-94` 再次描述 `EngineWorker` + `engine.command.*`。
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:182-184` 再次描述 engine dispatch internals under `engine.command.*`，outbound ports 为 `engine.port.{TaskControlClient, AccessLayerClient}`。
- 但当前源码里:
  - `EngineCommandEventFactory` / `EngineCommandGateway` / `EngineWorker` 已位于 `com.huawei.ascend.runtime.engine`。
  - `TaskControlClient` / `AccessLayerClient` 已位于 `com.huawei.ascend.runtime.engine`。
  - `AgentRequest` / `RunStatus` 位于 `com.huawei.ascend.runtime.common`；`runtime.schema` 目录不存在。

影响:

L1 `agent-runtime/ARCHITECTURE.md` 是 AGENTS.md reading path 中的 module design authority。当前 PR 的核心是“five flat modules”，但 L1 authority 仍描述 pre-flatten 包结构，后续开发者会从 architecture authority 读到与源码相反的结构约束。这个问题不影响编译，但会影响设计评审、后续重构边界和 AI agent 的事实判断。

建议:

同步更新 `architecture/docs/L1/agent-runtime/ARCHITECTURE.md`:

- 将 package table 改为当前 root-flat 结构: `runtime.engine` root contains dispatcher / worker / command events / execution scope / input / output / ports，保留 `api` / `spi` / `openjiuwen` 作为边界子包。
- 将 `runtime.schema` 改为 `runtime.common`，并反映 `AgentResponse` 已删除、live model 是 `AgentResponseEvent`。
- 明确 PR 中的 G12 决策: engine 只写 `TaskControlClient`，control 通过 accepted transition 才 fan out caller-facing egress。

### P2-1. `agent-runtime/README.md` 的 Java extension points 仍指向旧 FQN

证据:

- `agent-runtime/README.md:145`: `com.huawei.ascend.runtime.access.protocol.a2a.A2aAccessProperties`
- `agent-runtime/README.md:146`: `com.huawei.ascend.runtime.access.config.AccessLayerConfiguration`
- `agent-runtime/README.md:151`: `com.huawei.ascend.runtime.engine.adapters.openjiuwen.OpenJiuwenAgentRuntimeHandler`

当前源码实际路径:

- `com.huawei.ascend.runtime.access.a2a.A2aAccessProperties`
- `com.huawei.ascend.runtime.access.AccessLayerConfiguration`
- `com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler`

影响:

README 是外部读者最先接触的 module-level onboarding。PR body 说本次是为了降低定位成本，但 README 仍给出旧路径，会让新读者照着 README 找不到类，尤其是 openJiuwen adapter 的旧 `engine.adapters.openjiuwen` 与当前 `engine.openjiuwen` 完全不一致。

建议:

同步 README 的 extension point 列表，并考虑补一段 flatten 后的包导航，和 `RuntimePackageBoundaryTest` 的 five-layer 守卫口径保持一致。

### P2-2. 新增/保留的 `package-info.java` 仍含旧包引用或与源码依赖不一致

证据:

- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/api/package-info.java:22-23` 仍说 API package imports `com.huawei.ascend.runtime.engine.model`；该包已被 flatten 到 `runtime.engine` root。
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/package-info.java:6-8` 仍引用 `com.huawei.ascend.runtime.engine.command` 和 `com.huawei.ascend.runtime.engine.port`；这两个包已不存在。
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/package-info.java:18-20` 写着 access may depend on `common`, `session.api`, `control.api`, `queue`，但当前 access 源码仍依赖 engine:
  - `AccessLayerConfiguration.java:13` imports `com.huawei.ascend.runtime.engine.spi.AbstractAgentRuntimeHandler`
  - `EngineOutputSink.java:7-10` imports `EngineEvent`, `EngineExecutionScope`, `EngineOutput`, `AccessLayerClient`

影响:

PR 的 W4 目标是“document per-layer package-info”，这些 package-info 会变成后续维护者理解 layer dependency 的最近入口。现在它们一边宣称 flatten 后的边界，一边仍有旧包或不完整依赖描述，风险是后续 ArchUnit rule 和开发约束会按错误文案继续扩展。

建议:

1. 修正 `engine/api` 与 `engine/spi` 的 package-info，删除旧 `engine.model` / `engine.command` / `engine.port` 引用。
2. 对 `access/package-info.java` 做二选一:
   - 如果 access 允许依赖 engine SPI/root ports，则把 dependency rule 写完整，并说明这是为了 A2A AgentCard / output sink wiring。
   - 如果目标是 access 不依赖 engine，则需要把 `AccessLayerConfiguration` 和 `EngineOutputSink` 的 engine 类型依赖通过更中性的 boundary 移走，并增加 ArchUnit guard。

## 2. Positive Notes

本轮也确认了很多改动是扎实的:

- 源码层重命名没有留下旧包 import；`rg` main/test/example 后，旧 `access.protocol`、`engine.command`、`engine.port`、`session.model` 等主要旧包引用只集中在 generated facts、L1/README/package-info 和历史 review/plan 文档。
- `RuntimePackageBoundaryTest` 已经覆盖 flatten 后的主包形态，并用 `allowEmptyShould(false)` 避免 vacuous pass。
- `A2aJsonRpcHandler` 拆分为 handler + `A2aRequestMapper` + `A2aResponseMapper` 后，A2A handler tests 与 real e2e 均通过。
- `EngineEvent` record + `EngineEventKind` enum 的压缩没有在本轮 clean build / IT / example e2e 中暴露运行时回归。

## 3. Verification

已执行:

```bash
git fetch origin main simplify/agent-runtime-five-module-flatten
./mvnw clean verify
./mvnw -Pquality verify
bash gate/check_architecture_sync.sh
python gate/lib/sync_baseline.py --check
cd examples/agent-runtime-a2a-llm-e2e && bash scripts/test-e2e.sh .env.ollama.example
./mvnw -f tools/architecture-workspace/pom.xml -Dtest=FactLayerByteIdentityIT#committedFactsAreByteIdenticalToFreshExtractorOutput test
```

结果:

- Local head / remote head / PR head 均为 `3a3a6f133d5b1d989e34cd66ce58717217c99480`；merge-base 为 `origin/main@4bf47278bc9a0803824d47ea642e3951a1683b8d`。
- `./mvnw clean verify`: PASS，reactor `BUILD SUCCESS`。
- `./mvnw -Pquality verify`: PASS，reactor `BUILD SUCCESS`。
- `agent-runtime/target/checkstyle-result.xml`: 未发现 `<error>`。
- `bash gate/check_architecture_sync.sh`: PASS，`GATE: PASS` / `ARCHITECTURE WORKSPACE: PASS`；同时输出 `architecture_refresh_defect_family` advisory 以及若干历史 stale-surface WARN。
- `python gate/lib/sync_baseline.py --check`: PASS，baseline derivable fields match canonical counts。
- `bash scripts/test-e2e.sh .env.ollama.example`: PASS，loaded `provider=openai`, `apiBase=http://localhost:11434/v1`, `model=gemma4:latest`；example suite `Tests run: 28, Failures: 0, Errors: 0, Skipped: 0`，`OpenJiuwenReactAgentA2aE2eTest` 未跳过。
- `FactLayerByteIdentityIT#committedFactsAreByteIdenticalToFreshExtractorOutput`: FAIL，`architecture/facts/generated/code-symbols.json` content drift。

## 4. 合并建议

当前不建议 approve。建议工程团队先关闭 P1-1 和 P1-2，再回归 P2 文档卫生项。

最低合并门槛:

1. 刷新并提交 `architecture/facts/generated/*` 中受包结构重命名影响的事实文件。
2. 更新 `architecture/docs/L1/agent-runtime/ARCHITECTURE.md`，让 L1 authority 与 five-module flat source layout 一致。
3. 更新 `agent-runtime/README.md` 和相关 `package-info.java`，移除旧 FQN。
4. 重新跑 clean verify、quality verify、fact byte-identity、canonical architecture gate 和 example e2e。

完成后，本 PR 的运行时代码本身看起来有较好的合并基础；当前主要阻塞点是 authority / fact layer 没跟上源码重构。

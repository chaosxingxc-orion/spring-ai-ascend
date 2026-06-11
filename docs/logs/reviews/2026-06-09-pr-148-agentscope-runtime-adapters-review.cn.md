# PR 148 Review 报告：AgentScope runtime adapters

- PR: https://github.com/chaosxingxc-orion/spring-ai-ascend/pull/148
- Review 日期: 2026-06-09
- Base: `main@4bf47278bc9a0803824d47ea642e3951a1683b8d`
- Head reviewed: `fc4fa59e6d63245547a73385e16af8152e625dfd`
- PR 标题: `feat(runtime): add AgentScope runtime adapters`
- 结论: **Request changes / 暂不建议合并**
- 专家补审基线: `origin/main@66cd37b8`，包含 `00761e42 refactor(agent-runtime): simplify to five flat modules (#147)` 与后续 #149 文档更新。

> **复审更新（2026-06-09, head `4a206c6c`）** — 本报告原始基线是 `fc4fa59e`；committer 随后又推了 6 个提交、更新了 PR。复审结论：**P0-0 / P0-1 已修复**（包路径已落到 `engine.agentscope`，与 `engine.openjiuwen` 同级；`CapturingHttpClient` 已补同步 `send(...)`；新增可运行 examples；本地 `compile` = BUILD SUCCESS）。**仍未闭环**：P1-1（facts 漂移，已用全仓库 `ExtractFactsCli --check` 实测确认 `code-symbols.json` drift）、P2-1（L1/README/contract 仍 openJiuwen-only）、P3-1（`mapMap` 不识别 `failed`/`failure`，被 example 的 `error` 编码掩盖）。已把复审评论发到 PR：`#issuecomment-4656209325`。**下文为原始 `fc4fa59e` 报告，其中 P0-0 / P0-1 已过时。**

## 0. 总体判断

这次 PR 在自身旧基线上生产代码 main compile 可以通过，但放到最新 `origin/main`（已包含 PR 147 的五模块扁平化）后，自动三方合并虽然没有文本冲突，`agent-runtime` 生产代码编译会直接失败。PR 148 仍导入 `runtime.engine.handler` / `runtime.engine.model` 这些 PR 147 已经移除的旧包，属于“可自动合并、不可构建”的隐藏阻塞。

此外，PR 自身声明的测试命令在当前工作区也无法进入测试执行阶段：新增的 `AgentScopeRuntimeClientHandlerTest` test double 编译失败。新增的 `com.huawei.ascend.runtime.engine.adapters.agentscope.*` 运行时适配器没有同步刷新 Rule G-15 的 generated facts，`FactLayerByteIdentityIT` 明确失败；L1 / README / contract catalog 也仍然把 adapter surface 描述成 openJiuwen first/only 的状态。

根因一句话：PR 把 AgentScope adapter 代码提交进来了，但没有把同一生命周期里的测试替身、事实层 authority、L1/contract/README 说明一起闭环。

本轮先按 AGENTS.md / Rule G-15 读取 `architecture/facts/generated/*.json`，再对照 L1、contract catalog 和源码。当前事实层中仍可看到 openJiuwen 的 adapter fact，例如 `fact_id=code-symbol/com-huawei-ascend-runtime-engine-openjiuwen-openjiuwenagentruntimehandler`，以及 openJiuwen 测试 fact `fact_id=test/com-huawei-ascend-runtime-engine-openjiuwen-openjiuwenagentruntimehandlertest`；但没有任何 `AgentScope` / `agentscope` fact。

## 1. Findings

### P0-0. PR 148 没有 rebase 到 PR 147 后的 agent-runtime flat layout，自动合并后生产代码无法编译

证据:

- `git merge-base origin/main origin/pr-148` 返回 `4bf47278bc9a0803824d47ea642e3951a1683b8d`。
- `origin/main` 在该 merge-base 之后已经包含 `00761e42 refactor(agent-runtime): simplify to five flat modules (#147)`，并继续前进到 `66cd37b8 docs(agent-runtime): add L1 core features catalog (#149)`。
- PR 148 新增代码仍引用 PR 147 之前的旧包:
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/adapters/agentscope/AbstractAgentScopeRuntimeHandler.java:3` 导入 `com.huawei.ascend.runtime.engine.handler.AgentExecutionContext`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/adapters/agentscope/AgentScopeMessageAdapter.java:3-5` 导入 `engine.handler.AgentExecutionContext`、`engine.model.EngineInput`、`engine.model.EngineExecutionScope`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/adapters/agentscope/AgentScopeStreamAdapter.java:3` 导入 `engine.model.InterruptType`
  - 新增测试同样导入 `engine.handler.*` / `engine.model.*`
- 最新 `origin/main` 上这些类型已经扁平到 `com.huawei.ascend.runtime.engine` 根包，例如:
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/AgentExecutionContext.java:1`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/EngineExecutionScope.java:1`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/EngineInput.java:1`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/InterruptType.java:1`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/openjiuwen/OpenJiuwenAgentRuntimeHandler.java:1`

复现命令:

```text
git worktree add D:\chao_workspace\spring-ai-ascend-pr148-on-main origin/main
cd D:\chao_workspace\spring-ai-ascend-pr148-on-main
git merge --no-commit --no-ff origin/pr-148
.\mvnw -pl agent-runtime -DskipTests compile
```

合并结果:

```text
Automatic merge went well; stopped before committing as requested
```

编译失败摘要:

```text
package com.huawei.ascend.runtime.engine.handler does not exist
package com.huawei.ascend.runtime.engine.model does not exist
cannot find symbol: AgentExecutionContext / EngineExecutionScope / EngineInput / InterruptType
```

影响:

- GitHub 层面可能显示 PR 可合并，但在 PR 147 之后的真实主干上不能构建。
- 这会让专家误判为“只差测试替身/事实层刷新”，实际第一步应该是把 AgentScope adapter 移植到 PR 147 的 flat layout。
- 如果团队继续在 `runtime.engine.adapters.agentscope` 下提交，会再次引入 PR 147 刚刚消除的 nested adapter 结构漂移。当前主干上的 openJiuwen adapter 已位于 `runtime.engine.openjiuwen`，AgentScope 更自然的落点应是同级的 `runtime.engine.agentscope`，除非团队明确用 ADR/L1 决定恢复 `engine.adapters.*` 分层。

建议修复:

- 先 rebase / merge 最新 `origin/main`，把 AgentScope 代码移植到 PR 147 后的包结构。
- 把旧导入统一改为:
  - `com.huawei.ascend.runtime.engine.AgentExecutionContext`
  - `com.huawei.ascend.runtime.engine.EngineExecutionScope`
  - `com.huawei.ascend.runtime.engine.EngineInput`
  - `com.huawei.ascend.runtime.engine.InterruptType`
- 重新决定 package home：建议采用 `com.huawei.ascend.runtime.engine.agentscope`，与 `com.huawei.ascend.runtime.engine.openjiuwen` 对齐；如果坚持 `engine.adapters.agentscope`，需要同步更新 L1 和 contract authority，并解释为什么 PR 147 的 flat layout 不适用。
- 修完后先跑 `.\mvnw -pl agent-runtime -DskipTests compile`，再进入 P0-1 / P1 / P2 的后续闭环。

### P0-1. 新增测试类无法编译，PR 声明的验证命令不可复现

证据:

- 文件: `agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/adapters/agentscope/AgentScopeRuntimeClientHandlerTest.java:72`
- 代码: `private static final class CapturingHttpClient extends HttpClient`
- 该类实现了两个 `sendAsync(...)` overload，但没有实现 `java.net.http.HttpClient` 的抽象同步方法 `<T> send(HttpRequest, HttpResponse.BodyHandler<T>)`。

复现命令:

```text
.\mvnw -pl agent-runtime -Dtest="AgentScope*Test" test
```

失败摘要:

```text
COMPILATION ERROR
AgentScopeRuntimeClientHandlerTest.CapturingHttpClient is not abstract
and does not override abstract method <T>send(HttpRequest, BodyHandler<T>) in HttpClient
```

同一问题也会阻塞:

```text
.\mvnw -pl agent-runtime test
.\mvnw -pl agent-runtime -Pquality verify
```

影响:

- PR body 中列出的 `AgentScope*Test`、`agent-runtime test`、`agent-runtime -Pquality verify` 均无法在当前环境复现。
- 这是 testCompile 阶段失败，CI / reviewer / 开发者本地都会在真正跑测试前被挡住。
- 生产代码可以通过 `.\mvnw -pl agent-runtime -DskipTests compile`，所以阻塞点集中在新增测试替身，而不是主代码编译。

建议修复:

- 给 `CapturingHttpClient` 增加同步 `send(...)` 实现，并复用当前 `sendAsync(...)` 的固定响应逻辑；或者避免继承 JDK 抽象类，改成更小的 client port / fake。
- 修复后先跑:

```text
.\mvnw -pl agent-runtime -Dtest="AgentScope*Test" test
.\mvnw -pl agent-runtime test
.\mvnw -pl agent-runtime -Pquality verify
```

### P1-1. Rule G-15 generated facts 没有纳入新增 AgentScope adapter

证据:

- PR 新增源码目录: `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/adapters/agentscope/`
- 新增源码文件包括 12 个 `AgentScope*` / `AbstractAgentScope*` Java 文件。
- 本轮 main compile 后，`agent-runtime/target/classes/com/huawei/ascend/runtime/engine/adapters/agentscope/` 下已经产生 `AgentScopeRuntimeClient.class`、`AgentScopeRuntimeClientHandler.class`、`AgentScopeStreamAdapter.class` 等 class 文件。
- 但下面搜索没有任何命中:

```text
rg -n "AgentScopeRuntime|AgentScopeAgent|AgentScopeStreamAdapter|agentscope" \
  architecture/facts/generated/code-symbols.json \
  architecture/facts/generated/tests.json
```

字节一致性验证:

```text
.\mvnw -DskipTests compile
.\mvnw -f tools\architecture-workspace\pom.xml verify
```

失败摘要:

```text
FactLayerByteIdentityIT.committedFactsAreByteIdenticalToFreshExtractorOutput
--check: content drift detected on
architecture/facts/generated/code-symbols.json
```

影响:

- Rule G-15 / ADR-0154 要求对代码、契约、测试等事实性声明先以 generated facts 为准。当前 committed facts 不知道 AgentScope adapter 的存在，AI agent 和架构审计会读到旧事实。
- 这类变更不是单纯文档缺失，而是 authority surface 缺失。后续任何关于 “agent-runtime 已 shipped AgentScope adapter” 的事实性声明，都缺少对应 fact id 支撑。
- 当前因为 P0 测试编译失败，`tests.json` 还不能完成新增测试 inventory 的闭环；修复 P0 后，预计也需要同步刷新 `tests.json`。

建议修复:

- 先修复 P0，使 test classes 可以编译。
- 运行完整事实层刷新/校验流程，提交 extractor 生成的 `architecture/facts/generated/code-symbols.json` 和 `architecture/facts/generated/tests.json` 相关变更。
- 修复后至少验证:

```text
.\mvnw -DskipTests compile
.\mvnw -f tools\architecture-workspace\pom.xml verify
bash gate/check_architecture_sync.sh
python gate/lib/sync_baseline.py --check
```

注意: 本轮 `bash gate/check_architecture_sync.sh` 返回 PASS，但它没有暴露这次 code-symbols 字节漂移；真正暴露 Rule G-15.c.bytes 问题的是 `tools/architecture-workspace` 的 Failsafe IT。因此请不要只用 shell gate 作为事实层刷新成功的证据。

### P2-1. L1 / README / contract catalog 仍停留在 openJiuwen adapter 叙述，且没有吸收 PR 147 flat layout 约束

证据:

- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:49` 仍写 `runtime.engine.openjiuwen` 是 first concrete adapter。
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:88-89` 仍写 first adapter 是 `engine.openjiuwen`。
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:147` 的目录树仍只列 `openjiuwen/` adapter。
- `architecture/docs/L1/agent-runtime/ARCHITECTURE.md:176` 的 SPI 表仍写 `AgentRuntimeHandler` surface 是 openJiuwen adapter first。
- `agent-runtime/README.md:151` 仍只列内置 `OpenJiuwenAgentRuntimeHandler`。
- `docs/contracts/contract-catalog.md:46` 仍写 `AgentRuntimeHandler` surfaces output `(openJiuwen first)`。
- `docs/contracts/contract-catalog.md:172` 的 module promise 仍写 `framework-neutral engine (..., openJiuwen adapter)`。

影响:

- PR 的实际 shipped surface 已经新增 `runtime.engine.adapters.agentscope`，且包括普通 SDK、Harness SDK、Runtime REST/SSE client handler 三种形态；但 L1 和 contract catalog 没有承认这条 adapter surface。
- 新接入者从 authority docs 或 README 读不到 AgentScope handler 的 FQN、职责边界、配置入口和注册方式。
- 这会再次制造 “代码已经存在，但架构/契约 authority 仍描述旧世界” 的漂移，和之前 PR 140/147 里已经修过的问题属于同一类。
- 在 PR 147 之后，文档不应简单补一句 `runtime.engine.adapters.agentscope`；还要先决定是否和 `runtime.engine.openjiuwen` 同级。如果选择 `runtime.engine.agentscope`，L1 package table、目录树、contract catalog 和 generated facts 都应使用这一最终包名。

建议修复:

- 更新 `architecture/docs/L1/agent-runtime/ARCHITECTURE.md` 的 package table、目录树、SPI / adapter 说明，把 AgentScope 纳入 shipped adapter package；建议命名为 `runtime.engine.agentscope`，与当前 `runtime.engine.openjiuwen` 对齐。
- 更新 `agent-runtime/README.md`，列出至少这些 extension points:
  - `AgentScopeAgentRuntimeHandler`
  - `AgentScopeHarnessRuntimeHandler`
  - `AgentScopeRuntimeClientHandler`
  - `AgentScopeRuntimeClientProperties`
- 更新 `docs/contracts/contract-catalog.md`，把 `AgentRuntimeHandler` 的描述从 openJiuwen-only 调整为 openJiuwen + AgentScope adapters，并说明 AgentScope Runtime REST/SSE client handler 是 adapter implementation，不是新的 SPI。

### P3-1. REST/SSE map adapter 没有识别 `failed` / `failure` 状态

证据:

- 文件: `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/adapters/agentscope/AgentScopeStreamAdapter.java:35-48`
- 当前 `mapMap(...)` 只在 `status` 包含 `error` 或 map 包含 `error` key 时返回 `AgentExecutionResult.failed(...)`。
- 但同一个 adapter 的 typed event path 已经有 `AgentScopeEvent.Type.FAILED`，并在 `AgentScopeStreamAdapter.java:30` 映射为 failed。

影响:

- 如果 AgentScope Runtime REST/SSE 返回类似 `{"status":"failed","message":"boom"}` 或 `{"event":"failure","message":"boom"}`，当前实现会落到 `AgentExecutionResult.output(text)`，把失败事件报告为普通输出。
- 这会导致上层 run/task 状态误判，属于事件语义丢失。

建议修复:

- 在 `mapMap(...)` 中把 `failed`、`failure`、必要时 `exception` 纳入失败状态判断。
- 增加单测覆盖:

```text
Map.of("status", "failed", "message", "boom")
Map.of("event", "failure", "error_code", "BAD", "message", "boom")
```

## 2. Outsider / 可运行性视角

PR 148 当前只交付 Java adapter 类和单测，没有新增 AgentScope Runtime REST/SSE 的 example、`.env.example`、Spring properties binding 或运行脚本。考虑到 PR body 明确提到 private-cloud Runtime REST/SSE handler shapes，如果团队希望外部开发者能直接验证这条路径，建议补一个最小可配置入口:

- 本地部署 AgentScope Runtime 的 `baseUrl` / `endpointPath` 示例。
- agentId / tenantId / taskId / sessionId 的最小请求样例。
- 如果暂不做 Spring Boot auto-configuration，也应在 README 说明用户需要手动 new `AgentScopeRuntimeClientProperties`、`AgentScopeRuntimeClient`、`AgentScopeRuntimeClientHandler` 并注册到 `AgentRuntimeHandlerRegistry`。

这条不是合并阻塞项；阻塞项仍是 P0/P1/P2。

## 2.1 给专家的重点判断

如果专家只看 PR 148 的 isolated branch，会看到“生产代码可编译、测试替身编译失败、facts 没刷新”。但放到 PR 147 后的最新主干，结论需要升级:

- **第一优先级不是刷新 facts，而是 rebase 到 PR 147 后的 flat layout。** 当前新增代码引用的旧包已经不存在，自动合并后 main compile 失败。
- **AgentScope package home 需要架构确认。** PR 147 把 openJiuwen 放在 `runtime.engine.openjiuwen`；PR 148 新增 `runtime.engine.adapters.agentscope` 会重新引入一个不对称的 adapter namespace。
- **facts / L1 / contract 要在最终包路径确定后再刷新。** 否则会把错误包名固化进 authority surface。
- **P0-1 的测试替身问题仍需修。** 即使包路径修好，`CapturingHttpClient` 没有实现同步 `send(...)` 仍会挡住 testCompile。

## 3. 本轮验证记录

评审 worktree:

```text
D:\chao_workspace\spring-ai-ascend-pr148-review
HEAD = fc4fa59e6d63245547a73385e16af8152e625dfd
```

命令结果:

```text
.\mvnw -pl agent-runtime -Dtest="AgentScope*Test" test
FAIL: testCompile fails at AgentScopeRuntimeClientHandlerTest.CapturingHttpClient
```

```text
.\mvnw -pl agent-runtime test
FAIL: same testCompile failure
```

```text
.\mvnw -pl agent-runtime -Pquality verify
FAIL: same testCompile failure
```

```text
.\mvnw -pl agent-runtime -DskipTests compile
PASS
```

该 PASS 只代表 PR 148 的旧基线 isolated branch 上生产代码可编译，不代表最新 `origin/main` 上可构建。

Post-PR147 兼容性验证:

```text
git worktree add D:\chao_workspace\spring-ai-ascend-pr148-on-main origin/main
git merge --no-commit --no-ff origin/pr-148
.\mvnw -pl agent-runtime -DskipTests compile
FAIL: production compile fails because PR 148 imports pre-PR147 engine.handler / engine.model packages
```

```text
.\mvnw -DskipTests compile
PASS
```

```text
bash gate/check_architecture_sync.sh
PASS
```

```text
python gate/lib/sync_baseline.py --check
PASS: baseline_metrics all derivable fields match canonical counts
```

```text
.\mvnw -f tools\architecture-workspace\pom.xml verify
FAIL: FactLayerByteIdentityIT detects code-symbols.json drift
```

## 4. 建议修复顺序

1. 先把 PR 148 rebase / merge 到最新 `origin/main`，迁移到 PR 147 后的 flat package layout。
2. 确认 AgentScope 的最终 package home，建议 `runtime.engine.agentscope`，与 `runtime.engine.openjiuwen` 同级。
3. 修复 `CapturingHttpClient` 的同步 `send(...)` 方法缺失，让新增测试能编译。
4. 重新跑 `AgentScope*Test`、`agent-runtime test`、`agent-runtime -Pquality verify`。
5. 刷新并提交 generated facts，确保 `AgentScope*` code symbols 和新增 tests 都有 fact inventory。
6. 更新 L1 / README / contract catalog，把 AgentScope adapter surface 纳入 authority docs。
7. 补 `status=failed/failure` 的 REST/SSE map 测试和实现。

修完上述前 6 项后，这个 PR 才适合进入下一轮 merge-readiness review。

---

## 5. 复审 Round 3（2026-06-09, head `428cdf21` —— fix commit "Address AgentScope review findings"）

- 基线：`origin/main = 66cd37b8` (#149)，PR 已 rebase 其上，合并结果即 `pr-148-head`（无三方合并）。
- 方法：用 workflow 做 4 维对抗性复审（facts 内容 / adapter 正确性 / 文档一致性 / examples+治理）+ 亲自跑 CI 盲区 facts IT。
- 结论：**Request changes（再次暂不合并）**；GitHub 正式 review `CHANGES_REQUESTED`（chaosxingxc-orion, 2026-06-09T06:58:11Z）。

### 已闭环（独立核实）

- **P1-1（事实层字节一致性）已解决并核实**。证据：本 head clean `test-compile` 后单独跑 `./mvnw -f tools/architecture-workspace/pom.xml verify` → `FactLayerByteIdentityIT` BUILD SUCCESS / 0 失败；6 个 fact 文件字节一致。`adrs/contract-surfaces/module-build/runtime-config` 的改动经核实 100% 是 `repo_commit` provenance churn（`--check` 归一化 SHA 后不触发 gate）。**关键 CI 盲区**：`tools/architecture-workspace` 不在根 reactor `<modules>`，CI `./mvnw -Pquality verify` 不跑此 IT。
- **P2-1（L1/README/contract 文档一致性）已解决**：三处口径一致加入 `runtime.engine.agentscope`；4 个 FQN 真实存在；SPI 计数仍为 2；新增 ArchUnit 守卫非 vacuous；无 over-claim。
- **P3-1（mapMap failed/failure）base case 已修**（+2 测试），但同一改动扩大了子串匹配 → 见 P2-1(新)。

### 仍阻塞（本 PR 新代码，已核源核实）

- **P1-1(新) 阻塞** —— `AgentScopeRuntimeClient.syntheticFailure().body()` 字符串拼接 + `escapeJson` 不转义控制字符；IO message 含 `\n` → 非法 JSON → `readEvent` catch 回退 OUTPUT → 失败被降级、无终态 → 任务永挂。修：用 `objectMapper.writeValueAsString(Map.of(...))`，加 `\n` 回归测试。
- **P1-2(新) 阻塞** —— `AgentScopeStreamAdapter.mapMap` 的 `|| map.containsKey("error")` 对显式 `"error":null` 也判 FAILED（Jackson 保留 null 条目）→ 正常进行中事件被误判、输出丢失。修：error 值非空白才判失败。
- **P2-1(新)** —— `contains()` 子串匹配假阳性（`no_error`/`failover_ok`/`semifinal`…），被本轮 P3-1 修复扩大。修：对已知词表 `equals`/`startsWith`。
- **P2-2(新)** —— `streamEvents` 把 HTTP `599` 复用为 IO-失败哨兵，和真实上游 599 冲突 → 错误体喂给 SSE parser → 空流 → 挂起。修：带外信号 + 命名常量。

### 范围外（既有，建议单独 follow-up）

- `EngineDispatcher.runHandler` 无终态兜底：只 route 适配流事件、仅 handler 抛异常时补 FAILED；适配流非终态结束即任务停 RUNNING。这是放大上述缺陷的根因，但属既有 dispatcher 缺口，不在本 PR 扩大改动。

### 决策

- owner 选择 **发 request-changes 交回作者**（Kevin Hu），不代改分支、不合并。已发正式 review。

---

## 6. 复审 Round 4 + 闭环合并（2026-06-09, head `af518871` → fix `3879117a` → merge `b18ba80b`）

- 触发：Round-3 的 `CHANGES_REQUESTED`（head `428cdf21`）发出后，作者又推了 `af518871 "Harden AgentScope REST event handling"`，针对 4 个新阻塞做了修复，但无人复审。
- 方法：多智能体 workflow 4 维对抗复审（round3 复核 / 适配器找新 bug / facts+文档 / examples+治理）+ 每条 P1/P2 派对抗 verifier 反驳过滤；并亲自跑 CI 盲区 `FactLayerByteIdentityIT`。

### Round-3 四个新阻塞：全部已修，且各带回归测试（独立核实）

- **P1-1(新) 已修**：`ioFailure()` 改为直接返回 `Map`，IO 失败不再走字符串拼 JSON，控制字符不可能再产生非法 JSON / 降级为 OUTPUT。测试 `runtimeClientPreservesIoFailureMessageWithControlCharacters`。
- **P1-2(新) 已修**：`mapMap` 用 `firstText()`（跳过 null 值）+ `!isBlank()`，`{"error":null}` 不再误判 FAILED。测试 `mapsInProgressEventWithNullErrorToOutputResult`。
- **P2-1(新) 已修**：状态判定由 `contains()` 改为 `switch` 精确相等，`no_error`/`failover_ok`/`semifinal` 等落到 OUTPUT。测试 `doesNotTreatStatusSubstringsAsTerminalResults`。
- **P2-2(新) 已修**：非 2xx 走结构化 error map（`AGENTSCOPE_RUNTIME_HTTP_<code>`），不再复用 599 哨兵、不再把错误体喂给 SSE parser。测试 `runtimeClientTreatsHttp599AsUpstreamHttpFailure`。
- 之前已闭环项（facts 字节一致、L1/README/contract、mapMap base failed/failure）保持闭环。

### 本轮新发现并已修复（fix `3879117a`）

- **P2 连接泄漏（PR 引入，置信 82）**：`AgentScopeRuntimeClient.streamEvents()` 非 2xx 分支直接返回合成 error map，从未消费/关闭 `response.body()`（`ofLines()` 流持有 HTTP 连接）→ 每个上游非 2xx 响应泄漏一个连接。修复：返回前 `response.body().close()`；新增回归测试 `runtimeClientClosesResponseBodyOnNonSuccessStatus`（用 `onClose` 断言流被关闭）。事实层重抽，`tests.json` 纳入新测试方法，`repo_commit` 修正到真实父提交 `af518871`。

### 不阻塞 follow-up（留给作者，已写进 approve 评论）

- contract-catalog SPI 计数（2）可加注说明 `engine.agentscope` 扩展接口为何不计入 `spi_packages`。
- `AgentScopeE2eConfiguration:79` 用 byName 解析，零售财顾对应 bean 用 `@Qualifier` —— 口径不一致。
- 两个 example handler 重新实现了库里的 `AgentScopeRuntimeClientHandler`，`toRuntimeEvents` 逐字复制 —— 建议复用库类型。

### 验证记录（WSL）

- 生产 `compile` / `agent-runtime test-compile` / AgentScope 测试类（5/5/2）/ `FactLayerByteIdentityIT` 2/2 全绿；PR CI（`3879117a`）绿；合并后 main CI（`b18ba80b`）绿。

### 决策

- owner 复审确认达到合并标准：提交 approve（覆盖 Round-3 的 request-changes，`reviewDecision=APPROVED`），squash-merge 到 `main`（`b18ba80b`），删除分支 `appmod/java-upgrade-20260608125550`。**PR #148 已合并并经 main CI 验证为绿。**

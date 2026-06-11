# PR 172 / 176 / 177 合并后复审收口报告

日期：2026-06-12

## 背景与范围

PR #172 / #176 / #177 已于 2026-06-10 合入 main，其上又叠加了 #181–#188。本轮按 committer 决策执行"保留合入 + fix-forward"：以 workflow 多代理复审（5 个维度 finder：correctness / 授权闭环 / 安全出网 / 测试诚实性 / 既有 findings 状态核对；每条 finding 由独立 verifier 以反驳式核验对照当前 main HEAD `e0b867cf`；共 23 个 agent）重新审计三个 PR 引入的内容，并对确认缺陷在本 PR 中修复或转交。

前序报告 `2026-06-11-pr-172-176-177-architecture-review.cn.md` 的 7 条 findings 全部逐条复核。revert PR #178 因 main 已叠加 5+ 个后续 PR 而不再可行，处置见文末。

## 总体结论

17 项核验：**16 confirmed / 1 already-fixed / 0 rejected**。其中：

- 既有 7 条 findings：5 条在 HEAD 仍开放（P1-1、P1-2、P1-3、P2-1、P3-1），P2-2 已被 #186 重写设计文档修复，ADV-1 generated drift 被 #183 修复后又被 #184/#185 的源变更重新打开。
- 新发现 9 条（finder 维度交叉确认）：含 2 条 P2 主线缺陷（SSE 客户端无超时、X-Tenant-Id 虚假"已认证"声明）。

## 核验明细与处置

| ID | 核验定级 | 范围 | 结论 | 处置 |
|---|---|---|---|---|
| F-7 (=P1-1) | P1 | main-path | confirmed：engine.langgraph 无 L0/L1/catalog/ADR 授权，且 #184 catalog truth-up 后形成主动矛盾 | **本 PR 修复**：撤出 shipped 边界 — 4 类 + 2 测试迁至 examples e2e 模块 `com.huawei.ascend.examples.langgraph`（标注非 shipped 样例；置于 a2a 包之外以尊重 A2aClientPerspectiveTest 的 client 视角源码扫描），删 ArchUnit 白名单行，facts 再生成，基线 truth-up |
| F-1 | P2 | main-path | confirmed：AgentScope/LangGraph 客户端无 connect/request 超时，黑洞 upstream 令任务永久 WORKING + 每请求泄漏一个线程 | **本 PR 修复**：owned transport 加 connectTimeout(10s)，请求加 headers 超时(60s)，属性可配；LangGraph 样例同步修复；测试断言 |
| F-2 | P2 | main-path | confirmed：X-Tenant-Id 头被标注为"transport-authenticated"但模块内零认证，租户可被任意 wire client 选择 | **本 PR 修复（truth-fix）**：改正 A2aAgentExecutor / A2aJsonRpcController javadoc 的虚假声明；a2a-envelope.v1.yaml 增加 trust_boundaries 条款（要求多租户部署网关剥离/重注头）；删除 application.yml 幻影 auth 注释。真实认证由 W1 spine（PR #179 JwtTenantValidator 方向）承接 |
| F-4 | P3 | main-path | confirmed：cancel 落在 handler 连接阶段时丢失（注册晚于 execute() 返回），上游白跑 + 误报 INTERNAL | **本 PR 修复**：InFlightExecution 预注册（stream 槽后填 AtomicReference），消费循环逐次检查 cancelled；新增回归测试 |
| F-5 | P3 | main-path | confirmed：readEventBlock 部分解析失败时静默丢弃损坏尾部，损坏 terminal 变假 COMPLETED | **本 PR 修复**：两个客户端在部分解析失败时追加结构化 `*_RUNTIME_PARSE` 错误事件；新增测试 |
| F-6(replay) | P3 | examples（随迁移） | confirmed：LangGraphStreamAdapter 多轮会话把上一轮答案重放为新 OUTPUT | **本 PR 修复**（迁移后副本）：以"最后一条 human 消息为界"截断历史（`currentTurnAssistantText`），新增多轮回归测试 |
| F-11 (=P3-1) | P3 | main-path | confirmed：入站 A2A message 压平为单条 ROLE_USER 文本，非文本 part 静默丢弃 | **本 PR 修复（边界声明 + 可观测）**：非文本 part 丢弃时 WARN；contract-catalog agent-runtime 行 + a2a-envelope trust_boundaries 显式声明 W0 text-only 边界 |
| F-10 (=P2-1) | P2 | examples | confirmed：test-e2e 脚本/README 宣称跑测试，实际 skipTests=true 全跳 | **本 PR 修复**：test-e2e.sh/.ps1 显式 `-DskipTests=false` + "Tests are skipped." fail-fast；README.md/README.cn.md 直跑命令同步 |
| TVH-3 | P3 | examples | confirmed：#172 的 AgentScopeWireMessagesTest 从无管道执行 | **随 F-10 修复闭合**（脚本现在真实执行该测试） |
| PF-7 (=ADV-1) | ADVISORY | main-path | confirmed（重新打开）：modules.dsl 仍含 #185 已删除的 agent-runtime→agent-bus 依赖边；enforcers.dsl 仍含 #184 已迁移测试的旧路径 | **本 PR 修复**：AllFragmentsCli 再生成并提交 |
| F-8 (=P1-2) | P2（verifier 由 P1 降级：目标 host 来自 operator 配置而非 LLM） | agent-sdk | confirmed：HttpToolExecutor 默认跟随重定向、无 allowlist/私网阻断/审计/响应上限 | **转交** agent-sdk 负责团队（GitHub issue） |
| F-9 (=P1-3) | P1 | main-path 表面（root pom/facts/metadata） | confirmed：agent-sdk 不在 reactor/CI/facts/metadata/CODEOWNERS，5 个测试类无管道执行，ADR-0161 还给"agent-sdk owners"指派了义务 | **转交**（需 owner 对模块身份裁决：入 reactor+治理 或 正式 ADR 出治理） |
| S2 | P2 | agent-sdk | confirmed：HttpToolExecutor 无响应大小上限（ofString 无界缓冲，堆耗尽向量） | **转交** |
| S3 | P3 | agent-sdk | confirmed：异常路径回显完整 URL + 500 字符上游 body，经 openJiuwen ToolMessage 进入 LLM 上下文且绕过 TrajectoryMasking 键名脱敏 | **转交** |
| TVH-4 | P3 | examples(agent-sdk 配套) | confirmed：agent-sdk-example 无 parent/无测试/无 CI 编译，漂移类缺陷已发生过一次（c450da9d） | **转交** |
| F-3 | P3（verifier 由 P2 降级：NPE 交错被力学反驳，残留为 JLS 形式竞态 + renumbering 隐患） | main-path | confirmed：RemoteAgentCatalog Entry 明文字段跨线程无同步 | **缓议**：#187/#188 引入（非本批 PR），代码区属并发写者活跃区；以 GitHub issue 记录 immutable-snapshot 修复建议 |
| F-6(docs) | ADVISORY | docs | confirmed：L1 agent-runtime/L0 W0 描述已不存在的包面（AgentStateStore、agent-bus 边、EngineDispatcher 等） | **缓议**：#184/#187/#188 引入的已知文档滞后（merge-train 记录在案），单独 docs wave 一次性 lockstep true-up；GitHub issue 跟踪 |
| PF-5 (=P2-2) | — | docs | already-fixed：#186（7a3caf0a）重写 agent-sdk 设计文档，全部陈旧类引用清零（verifier 全文比对通过） | 无需动作 |

## 家族台账

按 Rule G-9 登记 `F-hand-authored-factual-drift` 一次 recurrence（catalog/L1 两-adapter 穷举声明 vs shipped langgraph、虚假 transport-authenticated javadoc、application.yml 幻影 auth 机制），无新家族。

## Verification

以下在 WSL（Rule G-7）、fix 分支（基于 main `e0b867cf`）上执行：

| 命令 | 结果 |
|---|---|
| `./mvnw -Pquality clean verify`（root reactor） | PASS（含本轮新增的 cancel/超时/解析尾包回归测试；SpotBugs/Checkstyle 干净） |
| `./mvnw -f tools/architecture-workspace/pom.xml verify` | PASS（FactLayerByteIdentityIT 对再生成 facts 字节一致） |
| `exec:java@extract-facts` + `exec:java@regenerate-fragments` | PASS；gate 的 fragment 幂等复检字节一致 |
| `bash gate/check_architecture_sync.sh` | 仓库自身文件零 FAIL；workspace baseline parity PASS（200==200）。仅剩 2 条 FAIL 全部指向并发会话工作树 `.claude/worktrees/pr-merge-train/` 内的文件（本地环境噪声；CI 干净 checkout 不含该目录） |
| `python3 gate/lib/sync_baseline.py --check` | PASS：all derivable fields match canonical counts |
| `./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml clean test -DskipTests=false` | PASS：51 tests / 0 failures / 0 errors / 3 conditional skips（真实 LLM 分支无 key 按 assumeTrue 跳过） |
| `bash scripts/test-e2e.sh`（修改后的 advertised 路径，含 fail-fast 守卫） | PASS：BUILD SUCCESS，51/0/0/3，守卫未触发 |
| `maven_tests_green` 实测 | 234（48 个 surefire/failsafe XML；公式与口径见 `architecture-status.yaml` baseline 注释） |

附带暴露并修复的隐性腐烂：example 钉 `agent-core-java 0.1.7` 而 agent-runtime 需要 0.1.12（缺 `memory.external.MemoryProvider`），此前被默认 skipTests 掩盖——按 README 路径实际无法启动 openJiuwen agent；升版后 e2e 上下文正常加载。这是 F-10（测试诚实性）修复价值的直接证据。

## PR #178 处置

revert 的窗口已关闭：main 在 #177 之后又合入 #181–#188（含 ADR-0161 生命周期、轨迹可观测、#186 agent-sdk 收窄），revert #172/#176/#177 将与其全部冲突且等价于重做后续工作。本轮以 fix-forward 收口三个 PR 的确认缺陷；此前对 #178 的 approve（review `4473485076`）基于当时 main 状态，已作废。已在 #178 评论说明并建议关闭。

## 复审方法备注

- Workflow：5 个维度 finder 并行 → prior_id 去重 → 每条 finding 独立 verifier 反驳式核验（缺省拒绝、必须在 HEAD 复现证据、检查 #181–#188 是否已修）。
- 0 rejected 的含义：finder 输出全部经受住了反驳式核验；其中 2 条被 verifier 降级（F-8 P1→P2、F-3 P2→P3）、1 条判定 already-fixed，证明核验层非橡皮图章。

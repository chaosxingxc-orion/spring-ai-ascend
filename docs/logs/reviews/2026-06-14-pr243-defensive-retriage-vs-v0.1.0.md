# PR #243 Defensive Re-triage vs AgentRuntime v0.1.0 — 裁决报告

> 生成:2026-06-14 · 方法:defensive triage workflow(18 组并行,逐组读 v0.1.0 实际代码)+ 人工对抗式复核两个 P0 反转 · 强防御姿态(举证才保留)

## 0. 结论(headline)

**18 个改动组,KEEP = 0 —— PR #243 没有一处是在修复 v0.1.0 实际代码里「使用方无法靠改用法规避的硬缺陷」。** 全部落入三类回退:

| 类别 | 数量 | 含义 |
|---|---|---|
| (a) 越界 / 未来范围 | 10 | v0.1.0 刻意未发布(release notes ⬜ 且 v0.1.0 代码确未提供),或属 v0.2.0+ 路线图 → 我们提前加了不支持的面 |
| (b) release 已含 | 3 | v0.1.0 实际代码已修/已具备,我们的改动冗余(甚至与 v0.1.0 方案冲突) |
| (c) 改用既有用法 | 5 | 目标可用 v0.1.0 已具备的用法达成,无需改代码 |
| KEEP | 0 | 无 |

> 印证研发团队 defensive 论点:issue 是「使用过程中的提问」,不是研发指令。默认假设「我们有错要改」导致 #243 整体性地越界 / 重复 / 替使用方改了本可换用法解决的问题。

## 1. 背景与权威

- **v0.1.0**(tag `846315a4`,从 `release/v0.1.0` 切出)= AgentRuntime 首个功能版,已并入 `origin/main`(`38d38a37`,随后 bump 0.2.0-SNAPSHOT)。release notes 是**带 ✅/⬜ 的权威范围声明** + v0.2.0+ 路线图。
- **PR #243**(分支 `fix/issues-2026-06-13-runtime-batch`,42 commits)基于**旧 main `fb9793b9`**,与 v0.1.0 **完全分叉**:既不含 v0.1.0,也未被其包含。**#243 的所有修复都不在 v0.1.0 里。**
- **重要张力**:release notes 的 ⬜ 标记有时与 v0.1.0 实际代码不一致(如 §5.3 把 OTLP 标未实现,但 v0.1.0 实有 `TrajectoryOtelConfiguration`)。因此每组裁决以 **v0.1.0 实际代码**为准,notes 仅参照。

## 2. 逐组裁决表

| 组 | issues | 裁决 | 置信 | v0.1.0 代码依据(摘) |
|---|---|---|---|---|
| traj-otlp-fields | #197,#198,#199,#200 | 回退(a)越界/未来范围 | high | Verified against v0.1.0 ACTUAL code (tag v0.1.0 = 846315a4), not just notes: (1) git show v0.1.0:.../engine/spi/TrajectoryEvent.java — `Usage` record … |
| traj-ttft | #201 | 回退(a)越界/未来范围 | high | v0.1.0 (tag → 6ff45cbe; merge-base with HEAD = fb9793b9 exactly as stated) ships NO TTFT capability, in code OR notes — they agree here. (1) agent-run… |
| traj-parent | #202,#203 | 回退(a)越界/未来范围 | high | NOTE: orchestrator-supplied issue ids #202/#203 are wrong for this group (those are payload_ref / sampleRate). The commits 71661f08+96a98dce and file/… |
| traj-sample | #204 | 回退(a)越界/未来范围 | high | v0.1.0 ships NO trajectory sampling. (1) git show v0.1.0:agent-runtime/.../engine/spi/TrajectorySettings.java is a 3-field record `(boolean enabled, P… |
| traj-redact-payload | #205 | 回退(a)越界/未来范围 | high | v0.1.0 ships ONLY key-name masking + inline truncation. `git show v0.1.0:agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/TrajectoryMa… |
| oj-rail-idempotent | #210 | 回退(c)改用既有用法 | high | v0.1.0 agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/openjiuwen/OpenJiuwenAgentRuntimeHandler.java:91 — doExecute() does `agent.registe… |
| oj-minor | #211,#212,#213,#221 | 回退(c)改用既有用法 | high | 检查 git show v0.1.0:agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/openjiuwen/OpenJiuwenAgentRuntimeHandler.java(452 行)。关键事实:(1) #213 称「需… |
| as-struct | #214,#215,#217 | 回退(c)改用既有用法 | high | Inspected v0.1.0 ACTUAL code (git show v0.1.0:...). #214: agent-runtime/.../agentscope/AgentScopeAgent.java and AgentScopeHarnessAgent.java are TWO di… |
| as-correctness | #216,#218,#219,#220 | 回退(b)release 已含 | high | Inspected v0.1.0 actual code (all four target files ship in v0.1.0). #219 git show v0.1.0:agent-runtime/.../common/RuntimeMessage.java: enum Role is O… |
| err-safety | #226 | 回退(b)release 已含 | high | Inspected git show v0.1.0:agent-runtime/.../boot/RuntimeAccessProperties.java and .../boot/A2aJsonRpcController.java (both identical to merge-base fb9… |
| config-decomp | #228 | 回退(a)越界/未来范围 | high | v0.1.0 (846315a4) === merge-base (fb9793b9) for every config file; v0.1.0 ships a fully-functional 320-line boot/RuntimeAutoConfiguration.java that is… |
| card-neutral-model | E1,#228-P1 | 回退(a)越界/未来范围 | medium | Inspected v0.1.0 ACTUAL code (git show v0.1.0:...): (1) agent-runtime/.../engine/a2a/AgentCardProvider.java — interface lives in engine.a2a and return… |
| card-honesty | #229,#230,#231,#232,#233 | 回退(c)改用既有用法 | high | Inspected v0.1.0 (tag=846315a4) ACTUAL code: 1) agent-runtime/.../engine/a2a/AgentCards.java — create(name,desc) hardcodes AgentCapabilities.streaming… |
| card-security | #234 | 回退(a)越界/未来范围 | high | v0.1.0 (tag = 6ff45cbe; release commit 846315a4, of which 6ff45cbe is an ancestor) ships NO card security at all. `git show v0.1.0:agent-runtime/.../e… |
| card-transport-meta | #235,#237,#241 | 回退(a)越界/未来范围 | high | v0.1.0 (=merge-base fb9793b9 for all card files; v0.1.0 made NO independent change) deliberately scopes the card narrow. (1) engine/a2a/AgentCards.jav… |
| card-overlay | #236 | 回退(c)改用既有用法 | high | v0.1.0 (846315a4) AgentCardProperties lives at agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/a2a/AgentCardProperties.java and ships EXA… |
| card-discovery | #238,#239,#240 | 回退(a)越界/未来范围 | high | git show v0.1.0:agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/AgentCardController.java — v0.1.0 already serves BOTH /.well-known/agent-ca… |
| mem-react | #247 | 回退(b)release 已含 | high | v0.1.0 (846315a4) ACTUAL code ALREADY fixes ReActAgent memory recall — via a different mechanism than PR #243, which the issue/our-reply never saw. In… |

## 3. 两个 P0 KEEP 候选 — 已人工 git 复核反转

### #247 ReActAgent 记忆失效 → (b) release 已含
- v0.1.0 `OpenJiuwenAgentRuntimeHandler` L354 `mergeMemoryIntoPromptBuilder()` **短路在** L359 `mergeMemoryIntoSystemMessage` 之前;L406 对 ReActAgent 调 `addPromptBuilderSection(MEMORY_SECTION_NAME,…)` 写入**逐轮 prompt builder**(正是 issue 声称被绕过的路径)。
- v0.1.0 自带真实回归测试 `memoryRuntimeRailInjectsSearchResultsIntoRealReActPromptBuilder`(test L153/159/168,含负例 L181-189)。
- merge-base `fb9793b9`:`addPromptBuilderSection` **0 处** → v0.1.0 分叉后**独立修好**。
- 结论:#243 的 `beforeModelCall→ModelCallInputs` 是**第二套分叉重修**,在含 v0.1.0 的 main 上冗余(双重注入风险)。owner 当时确认的 bug 是分叉分支代码,非 v0.1.0。

### #210 rail 重复发射 → (c) 改用既有用法
- v0.1.0 `OpenJiuwenAgentRuntimeHandler` 每次 `doExecute` L87 新建 agent、L91 `registerRail`,全文件 `unregisterRail` **0 处**;SDK `BaseAgent.registerRail` 确会累积(若复用 agent)。
- 但 v0.1.0 **文档化契约 = 每次执行重建 agent**:`createOpenJiuwenAgent`「每次执行前调用」;所有示例每次新建 ReActAgent(`OpenJiuwenSimpleAgentConfiguration` L94→L103 `new ReActAgent`)。
- 累积 P0 **仅当使用方违反契约去缓存/复用 BaseAgent**时才出现 → 按既有「每次执行重建」用法即零重复发射,无需改代码。

## 4. 架构团队拍板项(不可单方回退)

**card-neutral-model(E1 + ADR-0163,置信 medium)**:中性 `AgentCardDescriptor` 模型(6 类型 + mapper + SPI 迁移)在 v0.1.0 与 merge-base **均不存在**,v0.1.0 照常发布。v0.1.0 `boot/RuntimeAutoConfiguration.a2aAgentCard()` 是 `@ConditionalOnMissingBean`,host **注册一个 `AgentCardProvider` bean 返回手搭 A2A AgentCard 即可全权控制 card 元数据**(streaming/skills/security/transports)——这正是 #229-#234 的既有用法路径(c)。中性模型唯一净增值是「让 A2A-free 适配器声明 card 元数据」,即 ADR-0162 当初判为投机/YAGNI 而 defer 的场景。**但 ADR-0163 状态=accepted 且推翻了 accepted 的 ADR-0162,回退它是架构决策而非 triage**:需架构团队裁定,并与 #229-#241 同批处理,确保 card 诚实诉求经既有 AgentCardProvider 路径不丢失。

## 5. 关键「既有用法」(支撑 c 类)
- **Agent Card 任意元数据**:注册一个 `AgentCardProvider` bean 返回手搭 `org.a2aproject AgentCard`(v0.1.0 `a2aAgentCard()` @ConditionalOnMissingBean 已支持全量接管)→ 覆盖 #229-233 诚实声明、#236 overlay、#234/#235/#237 诉求,无需中性 SPI。
- **openjiuwen rail 无重复发射**:遵循 v0.1.0「每次执行新建 agent」契约(`createOpenJiuwenAgent`)→ 覆盖 #210。
- **错误分类**:v0.1.0 `RuntimeErrorCode`(5 值,A2A 调用方面)已具备 → 北向错误诉求无需 OTel 10 值枚举。

## 6. 合并回退清单(Phase B:重建到当前 main 时「不重放」)

- **traj-otlp-fields** (#197,#198,#199,#200) — 回退(a)越界/未来范围
  - `7211917e — feat(runtime/spi): introduce ErrorCategory enum (ErrorCategory.java NEW + ErrorInfo.category)`
  - `469abc54 — refactor: enforce non-null ErrorInfo.category`
  - `44bb1880 — feat(trajectory): first-class finishReason + Usage provider/cost fields (TrajectoryEvent.java, TrajectoryDraft.java)`
  - `0ae143ea — feat(trajectory): OtelSpanSink/A2aNorthboundSink gen_ai.system/error.type/finish_reasons/cost emission`
  - `1b66fb99 — feat(trajectory): AgentScopeErrorCategories.java + map agentscope codes (AbstractAgentScopeRuntimeHandler.java)`
  - `6c093cd4 — openJiuwen rail Throwable→ErrorCategory mapping + provider=null`
  - `d90addc6 — PARTIAL: revert only the gen_ai-enrichment fields of the SCHEMA_VERSION 3 bump (finishReason/provider/cost/error-category); keep parent-ids/first-token if those sibling groups stay (re-resolve schemaVersion back to 2 if all siblings revert)`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/ErrorCategory.java`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/agentscope/AgentScopeErrorCategories.java`
- **traj-ttft** (#201) — 回退(a)越界/未来范围
  - `6ed76117 — feat(trajectory): MODEL_CALL_FIRST_TOKEN point event for TTFT (agentscope)`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/TrajectoryEvent.java — remove MODEL_CALL_FIRST_TOKEN Kind`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/TrajectoryDraft.java — remove modelCallFirstToken()`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/agentscope/AbstractAgentScopeRuntimeHandler.java — remove first-token latch + emit`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/otel/OtelSpanSink.java — remove gen_ai.first_token span event`
  - `test changes in AgentScopeRuntimeHandlerTest.java, OtelSpanSinkTest.java, TrajectoryEventTest.java (note: TTFT-relevant assertions also touched later by d90addc6 wire-schema-v3 — revert TTFT slice carefully)`
- **traj-parent** (#202,#203) — 回退(a)越界/未来范围
  - `71661f08`
  - `96a98dce`
  - `3883bbe2 (companion: runtime.parent.* key constant, referenced in #204 reply)`
  - `agent-runtime/.../engine/spi/TrajectoryEvent.java (parentTaskId/parentTraceId fields)`
  - `agent-runtime/.../engine/AgentExecutionContext.java (parent fields + withParentLinkage)`
  - `agent-runtime/.../engine/spi/StampingTrajectoryEmitter.java (six-arg ctor + parent stamping)`
  - `agent-runtime/.../engine/spi/AbstractAgentRuntimeHandler.java (openTrajectory parent pass-through)`
  - `agent-runtime/.../engine/a2a/A2aNorthboundSink.java (parent wire map)`
  - `agent-runtime/.../engine/otel/OtelSpanSink.java (trajectory.parent_task_id/parent_trace_id attrs)`
  - `agent-runtime/.../engine/a2a/A2aRemoteAgentOutboundAdapter.java (parentMetadata merge in toParams)`
- **traj-sample** (#204) — 回退(a)越界/未来范围
  - `6c15eb5a`
  - `fc080285`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/TrajectorySettings.java (sampleRate field + basic() factory)`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/StampingTrajectoryEmitter.java (keptInvocation + samplingAllows/isAlwaysKept gate)`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/TrajectoryProperties.java (sampleRate property)`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/RuntimeAutoConfiguration.java (sampleRate pass-through)`
  - `agent-runtime/src/test/.../StampingTrajectoryEmitterTest.java + TrajectorySettingsTest.java + RuntimeAutoConfigurationTest.java (sampling assertions) + TrajectorySettings.basic() migrations across A2aAgentExecutorTest/AgentScopeRuntimeHandlerTest/OpenJiuwenAgentRuntimeHandlerTest/OpenJiuwenTrajectoryRailTest/AbstractAgentRuntimeHandlerTest`
- **traj-redact-payload** (#205) — 回退(a)越界/未来范围
  - `2476c9fa (feat(trajectory): pluggable Redactor SPI — engine/spi/Redactor.java + ValueRecognizingRedactor.java + TrajectorySettings/StampingTrajectoryEmitter/RuntimeAutoConfiguration wiring + tests)`
  - `d3262c19 (feat(trajectory): payload_ref:// store — engine/spi/PayloadRefStore.java + LocalFsPayloadRefStore.java + TrajectoryProperties/TrajectorySettings/StampingTrajectoryEmitter/RuntimeAutoConfiguration wiring + tests)`
- **oj-rail-idempotent** (#210) — 回退(c)改用既有用法
  - `deab5aa4`
  - `309e88e2`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/openjiuwen/MemoryRuntimeRail.java`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/openjiuwen/OpenJiuwenAgentRuntimeHandler.java`
  - `agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/openjiuwen/OpenJiuwenAgentRuntimeHandlerTest.java`
- **oj-minor** (#211,#212,#213,#221) — 回退(c)改用既有用法
  - `755d0254 (OpenJiuwenReActHandler.java + OpenJiuwenReActHandlerTest.java)`
  - `deab5aa4 (MemoryRuntimeRail.java extract — revert to v0.1.0 nested class)`
  - `309e88e2 installRails/openJiuwenRails javadoc portion (keep只有与 #210 幂等性 bug 真修复无关的纯 javadoc 增量;trajectory-rail 幂等修复属另组,按其组裁决)`
  - `ab228eb1 (framework-adapter-guide.md)`
  - `f9bf80a3 (SampleOpenJiuwenReactAgentHandler javadoc + OpenJiuwenSimpleAgentConfiguration Step 4)`
- **as-struct** (#214,#215,#217) — 回退(c)改用既有用法
  - `036dda8f (refactor: merge AgentScopeHarnessAgent into AgentScopeAgent — restores deleted public type AgentScopeHarnessAgent.java + AgentScopeHarnessRuntimeHandler reference)`
  - `52d5216e (refactor: AgentScopeStatus enum + WARN — removes new AgentScopeStatus.java, restores 3 isXxxStatus predicates in AgentScopeStreamAdapter.java + its test)`
  - `13e125e7 (fix: warn only on authoritative status field — folded into the 52d5216e revert of AgentScopeStreamAdapter.java)`
  - `the single-source invocationMetadata portion of 8967a4cd touching AgentScopeMessageAdapter.java (#217 — restore independent Map.of in toInvocation + LinkedHashMap in AgentScopeRuntimeClient.requestBody); revert ONLY the invocationMetadata-consolidation hunks, keeping unrelated 8967a4cd fixes (role mapping / userId) for separate triage`
- **as-correctness** (#216,#218,#219,#220) — 回退(b)release 已含
  - `8967a4cd`
  - `25c2bf91`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/agentscope/AgentScopeMessageAdapter.java`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/agentscope/AgentScopeRuntimeClient.java`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/agentscope/AgentScopeEvent.java`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/agentscope/AgentScopeErrorCategories.java`
- **err-safety** (#226) — 回退(b)release 已含
  - `ba0d05c0`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/A2aJsonRpcController.java (handleNotWritable @ExceptionHandler + HttpHeaders/HttpMessageNotWritableException/ExceptionHandler imports)`
  - `agent-runtime/src/test/java/com/huawei/ascend/runtime/boot/A2aJsonRpcControllerTest.java (notWritableExceptionHandlerReturnsJsonRpcInternalErrorWithApplicationJsonContentType)`
- **config-decomp** (#228) — 回退(a)越界/未来范围
  - `ee79fa1d (TenantContract single-source)`
  - `9129abc5 (move AgentCardProperties/RemoteAgentProperties to boot; card construction in AgentCards)`
  - `2b9f7166 (TrajectorySettings.from converter)`
  - `0b7777f3 (split RuntimeAutoConfiguration into 3 @Configuration classes)`
  - `628299fb — partial: revert the ADR-0162 portion only (withdraw ADR-0162); ADR-0163 in the same commit belongs to the separate #229-#241 card-batch group — coordinate before touching it`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/TenantContract.java (NEW — remove)`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/TenantContractTest.java (NEW — remove)`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/A2aExecutionConfiguration.java (NEW — remove)`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/A2aInfrastructureConfiguration.java (NEW — remove)`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/RuntimeLifecycleConfiguration.java (NEW — remove)`
- **card-neutral-model** (E1,#228-P1) — 回退(a)越界/未来范围
  - `da569661`
  - `628299fb`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/AgentCardDescriptor.java`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/AgentCapabilitiesDescriptor.java`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/AgentSkillDescriptor.java`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/AgentInterfaceDescriptor.java`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/SecuritySchemeDescriptor.java`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/SignatureDescriptor.java`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/AgentCardProvider.java (revert move; restore engine.a2a/AgentCardProvider.java agentCard():AgentCard)`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/a2a/A2aAgentCardMapper.java`
- **card-honesty** (#229,#230,#231,#232,#233) — 回退(c)改用既有用法
  - `commit 59047204 (entire commit) on fix/issues-2026-06-13-runtime-batch`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/AgentRuntimeHandler.java (3 new default methods supportsStreaming()/skills()/defaultOutputModes())`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/a2a/BuildVersion.java (NEW file)`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/a2a/A2aAgentCardMapper.java (default flips streaming/push/outputModes; revert to v0.1.0 AgentCards.create semantics)`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/AgentCapabilitiesDescriptor.java`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/AgentCardDescriptor.java`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/A2aExecutionConfiguration.java (push-durability derivation)`
  - `agent-runtime/pom.xml (build-version filtering)`
  - `agent-runtime/src/main/resources/.../agent-runtime-build.properties (NEW)`
  - `test deltas in RuntimeAutoConfigurationTest.java / A2aAgentCardMapperTest.java / AgentCardDescriptorTest.java`
- **card-security** (#234) — 回退(a)越界/未来范围
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/SecuritySchemeDescriptor.java (NEW — delete)`
  - `A2aAgentCardMapper.java: security mapping (apiKey/http/mutualTLS/oauth2-fallback) added by bf9475fc / da569661`
  - `AgentCardDescriptor.java: withSecuritySchemes / withSecurityRequirements withers (bf9475fc)`
  - `AgentCardProperties.java (boot): securityScheme/securityRequirements nested YAML config (bf9475fc)`
  - `A2aExecutionConfiguration.java: applyYamlOverlay security wiring + (already-removed) default-emit logic (bf9475fc, c3909158)`
  - `Tests: A2aAgentCardMapperTest security cases; RuntimeAutoConfigurationTest security cases`
  - `commits bf9475fc (security portion) + c3909158 (the no-op-default fix to the reverted feature)`
- **card-transport-meta** (#235,#237,#241) — 回退(a)越界/未来范围
  - `bf9475fc (the #235/#237 parts: AgentCardProperties.additionalEndpoints + documentationUrl/iconUrl + AgentCardDescriptor.withAdditionalInterfaces/withSignatures/withDocumentationUrl/withIconUrl/withSecuritySchemes withers + A2aExecutionConfiguration.applyYamlOverlay multi-transport/doc/icon)`
  - `c3b80198 (the #241 part: AgentCardController.resolveUrls additionalInterfaces[].url rewrite + AgentCard.builder(card) additionalInterfaces passthrough + AgentCardControllerTest additionalInterfaces cases)`
  - `agent-runtime/.../engine/spi/AgentInterfaceDescriptor.java (NEW — revert)`
  - `agent-runtime/.../engine/spi/SignatureDescriptor.java (NEW — revert)`
  - `A2aAgentCardMapperTest / AgentCardControllerTest additionalInterfaces+doc/icon+signature assertions added by these commits`
- **card-overlay** (#236) — 回退(c)改用既有用法
  - `bf9475fc (#236 applyYamlOverlay deep-field overlay in boot/A2aExecutionConfiguration + boot/AgentCardProperties deep YAML fields)`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/A2aExecutionConfiguration.java (applyYamlOverlay)`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/AgentCardProperties.java (deep fields beyond the v0.1.0 6-field set: capabilities/defaultInputModes/defaultOutputModes/skills/additionalEndpoints/documentationUrl/iconUrl)`
  - `AgentCardDescriptor deep-field withers added for overlay (withDefaultInputModes/withDefaultOutputModes/withSkills/withSecuritySchemes/withSecurityRequirements/withAdditionalInterfaces/withSignatures) — revert insofar as they exist only to back the overlay; coordinate with the AgentCardDescriptor model decision (da569661)`
- **card-discovery** (#238,#239,#240) — 回退(a)越界/未来范围
  - `c3b80198 — agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/AgentCardController.java: revert the ETag/Cache-Control/304 logic (#239), the legacy Link rel=canonical header + INFO log (#238), and the ResponseEntity conversion of both @GetMapping methods back to raw AgentCard returns`
  - `c3b80198 — agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/RuntimeAutoConfigurationTest.java: revert the hasExplicitName() no-match WARN test cases (#240)`
  - `c3b80198 — agent-runtime/src/test/java/com/huawei/ascend/runtime/boot/AgentCardControllerTest.java: revert the ETag/304/Cache-Control/Link assertions (#238/#239)`
  - `NOTE: the hasExplicitName() WARN code for #240 lives in RuntimeAutoConfiguration.a2aAgentCard() (modified by this commit/branch) — revert that branch's added handler-match WARN, restoring v0.1.0 behavior `name = cardProperties.getName();``
  - `NOTE: c3b80198 also bundles an additionalInterfaces[].url rewrite (Legacy_0_3_AgentInterface) — that is a SIBLING concern outside the card-discovery group (#238/#239/#240); do NOT revert it here, leave it to its owning group`
- **mem-react** (#247) — 回退(b)release 已含
  - `b544a2c2`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/openjiuwen/MemoryRuntimeRail.java`
  - `agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/openjiuwen/OpenJiuwenAgentRuntimeHandlerTest.java`

> Phase B 机制(owner 选定):从 `origin/main`(含 v0.1.0)建新分支,**只重放架构团队确认保留的项**(当前为空);a/b/c 全部不重放;ADR-0162/0163 一并撤出或转「已被 v0.1.0 范围决策推翻」记录。原 #243 保留备查。

## 7. GitHub 回复草稿(逐组,中文;经确认后再贴)

### traj-otlp-fields — #197,#198,#199,#200 [回退(a)越界/未来范围]

感谢 hotel dogfood 团队这组高质量的可观测性反馈（#197–#200），方向我们完全认同。核对 v0.1.0 实际代码后说明一下边界：v0.1.0 的轨迹/OTel 导出是按"窄范围"刻意发布的——`OtelSpanSink` 当前只发出 `gen_ai.request.model` 与 `gen_ai.usage.input_tokens/output_tokens`（外加 tenant/trace 关联属性），`Usage` 记录仅 `(inputTokens, outputTokens, latencyMs, model)`、`ErrorInfo` 仅 `(code, message)`，均无 provider / cost / ErrorCategory / 一等 finishReason；发布说明 §5.3「轨迹能力待补」已把 OTLP 这条线的增强列为未实现，§5/§7.3 的「错误码分类」指的是面向 A2A 调用方的 `RuntimeErrorCode`（INVALID_INPUT/TIMEOUT/… 5 值闭集，仅在 A2aAgentExecutor 里用），与 OTel `gen_ai.error.type` 是两套不同语义、不同 sink。也就是说，这四项并非 v0.1.0 已发布能力里的硬缺陷（现有发出的属性是正确的、无 workaround 障碍），而是对轨迹观测面的增量补齐——正好落在 v0.2.0 路线图「特性 1：agent-runtime 能力补齐 — 完善轨迹记录 + 生产可观测最佳实践」里。补充一点佐证：#197 的 provider 字段在 openJiuwen 适配器上因 `UsageMetadata`（agent-core 0.1.12，javap 验证）不暴露 provider 而只能填 null、`gen_ai.system` 实际从不发出，也说明现在落地偏早。因此本组改动我们计划从 PR #243 回退，统一纳入 v0.2.0 的轨迹增强一并设计（gen_ai.system / cost / gen_ai.error.type / gen_ai.response.finish_reasons + schema v3 一次性升版）。需求我们已完整记录、不会丢。可否请确认按此关闭这四个 issue（转 v0.2.0 路线图跟踪）？如对 v0.2.0 的字段口径有具体诉求，欢迎在路线图条目下继续补充。

### traj-ttft — #201 [回退(a)越界/未来范围]

感谢 hotel dogfood 团队提出 TTFT 观测需求，这确实是流式体验调优的重要指标。经核对 v0.1.0 实际代码：该版本的轨迹能力按发布说明 §5.3 是有意收窄的——`TrajectoryEvent.Kind` 只含 9 个枚举（无 `MODEL_CALL_FIRST_TOKEN`），`TrajectoryDraft` 无 `modelCallFirstToken()`，`OtelSpanSink` 无 `gen_ai.first_token`，AgentScope 处理器在 OUTPUT 增量上只发 `progress()`、并无首 token 计时点；发布说明 §5.3 也明确将「首 Token 延迟（TTFT）观测」标记为 ⬜ 未实现，代码与说明一致。因此 TTFT 属于 v0.1.0 之外的路线图能力，本次 PR #243 是在已发布范围外新增了未承诺的接口面，我们会先回退这部分改动，并把 TTFT（含 AgentScope best-effort 与 openJiuwen 待上游 SDK 暴露流式钩子的 rail 适配）纳入 v0.2.0+ 路线图统一设计。麻烦确认是否可据此关闭本 issue？届时我们会在路线图中跟踪。

### traj-parent — #202,#203 [回退(a)越界/未来范围]

感谢 dogfood 团队的反馈，也感谢你们在 issue 里主动留了"如已实现请关闭"的余地。我们复核了 v0.1.0 的实际发布代码（git tag v0.1.0），结论是：跨 run 的 parentTaskId/parentTraceId 轨迹串联在 v0.1.0 中确实尚未落地——`TrajectoryEvent`、`AgentExecutionContext` 都没有 parent 字段，`OtelSpanSink`/`A2aNorthboundSink` 也不透出 parent 属性，入站侧也不读 `runtime.parent.*`。v0.1.0 现有的 `parentTaskId/parentContextId` 是 A2A 的"结果回路由"通道（把子 agent 结果路由回父 task），与本 issue 想要的"重建 trace tree 的可观测性字段"是两件事，且不含 traceId。也就是说这是 v0.1.0 刻意收窄范围内尚未提供的能力（§5.3 也把相邻的 OTLP / 采样 / 大载荷外置标为待补），release notes §5.1 的 ✅ 标记属于超前标注，我们会同步修正。因此 PR #243 里这部分新增面会随该 PR 一并撤回，纳入 v0.2.0+ 的"跨 run trace tree"路线项统一设计（含 W3C traceparent 透传 + OTel 远端 span nesting）。本 issue 我们建议作为 v0.2.0 roadmap 跟踪保留/转标；如你们认可这一处置，欢迎确认后由我们关闭或转为 roadmap 标签。再次感谢精准的现场反馈！

### traj-sample — #204 [回退(a)越界/未来范围]

感谢 hotel dogfood 团队的反馈。关于本批次中我们额外补的「轨迹采样率（sampleRate per-invocation Bernoulli 采样）」能力，经与今天发布的 AgentRuntime v0.1.0 实际代码核对：v0.1.0 的轨迹系统刻意只提供「开/关」二元开关（全局 app.trajectory.enabled + 单请求 trajectory.level=off 退订），TrajectorySettings/TrajectoryProperties/StampingTrajectoryEmitter 中均无任何采样字段或概率门控（git grep 采样相关符号 0 命中），这与发布说明 §5.3「轨迹能力待补」中明确标注的「⬜ 采样率控制」一致——它是 v0.1.0 有意收窄、留待后续版本的能力。因此我们会把这部分采样改动从 PR #243 回退，纳入 v0.2.0+ 路线图统一设计（与外置载荷存储、TTFT 等 5.3 项一并推进）。需要说明的是，issue #204 本身诉求是子 agent 的 parentTaskId/parentTraceId 跨 run 串联，这一项 v0.1.0 实际已具备（A2aParentTaskProjector / RemoteAgentInvocationService 的 parentTaskId 链路，发布说明 §5.1 已标 ✅），我们另有针对性回复。本条仅就「采样率」部分回退说明，若您认可此处置请帮忙确认，谢谢！

### traj-redact-payload — #205 [回退(a)越界/未来范围]

感谢 hotel dogfood 团队的反馈，定位很准。我们复核了 v0.1.0 实际代码：当前发布版的 `TrajectoryMasking` 仅做 key-name 正则掩码 + 长字符串截断，确实不识别"值级"语义敏感数据（GPS/身份证/卡号），也没有大载荷外置存储——这两项在 v0.1.0 发布说明 §5.3 中已明确标注为"⬜ 待补"（自定义脱敏逻辑注入 + 大载荷外置存储），属于本次发布有意收窄的范围。因此本 issue 提出的 `Redactor` SPI（值级/自定义脱敏注入）与 `PayloadRefStore`（大载荷外置存储）都不是 v0.1.0 的缺陷，而是规划中的增强能力，已纳入 v0.2.0 候选的"完善日志轨迹记录"特性统一设计与落地（含值级脱敏识别器、生产环境轨迹收集/存储/查询最佳实践）。在 v0.2.0 实现前，对值敏感字段建议先通过给字段命名加上敏感关键字（命中现有 key-name 正则）或在 Handler 侧自行预处理 payload 规避。我们会把本条作为 v0.2.0 该特性的需求输入。可以的话，麻烦确认是否可先关闭本 issue，待 v0.2.0 设计稿出来再邀您评审？谢谢！

### oj-rail-idempotent — #210 [回退(c)改用既有用法]

感谢 hotel agent 团队的反馈,也认可你们对 SDK rail 语义的判断是准确的——openJiuwen 的 `registerRail` 确实不幂等,callback 挂在 agent 生命周期上,若复用同一个 `BaseAgent` 实例确会跨执行累加。不过我们重新核对了 v0.1.0 的契约后认为这里无需在框架侧修改:`createOpenJiuwenAgent` 的契约是"每次执行重新构建 agent"(类 javadoc: "Build the concrete openJiuwen agent instance for this execution";simple 示例 javadoc: "The runtime calls this method before each execution"),v0.1.0 随附的所有示例子类(OpenJiuwenReactAgentConfiguration / OpenJiuwenSimpleAgentConfiguration 等)都是在 `createOpenJiuwenAgent` 内 `new ReActAgent(...)`,均不缓存。按这一既有用法,每次执行得到全新 agent、恰好挂一个 trajectory rail,不会出现重复 emit。issue 中描述的累加只在"子类自行选择缓存 BaseAgent"这一偏离文档契约的用法下才发生,属可通过遵循现有用法规避,而非 v0.1.0 必现且无法绕过的硬缺陷。因此我们计划在 v0.1.0 维持现状(不引入 try-finally 注销逻辑),建议缓存场景下沿用"每次执行重建 agent"的既有模式即可。如果你们有必须缓存 `BaseAgent` 的具体场景/性能数据,欢迎补充,我们再评估是否在后续版本提供官方的幂等保障。请确认是否可据此关闭本 issue,谢谢!

### oj-minor — #211,#212,#213,#221 [回退(c)改用既有用法]

感谢 hotel agent dogfood 团队细致的接入体验反馈。结合今日发布的 v0.1.0 实际代码复核后,这四项更偏维护性/文档/DX 偏好,而非 v0.1.0 的能力缺口或硬缺陷,因此我们倾向不在本批次并入,改为按既有用法说明:接入异构 Agent 的支持路径见 v0.1.0 release notes §1.4——继承 `OpenJiuwenAgentRuntimeHandler` 实际只需实现 1 个抽象方法 `createOpenJiuwenAgent(AgentExecutionContext)`(`openJiuwenRails` 已是返回空列表的默认实现,并非抽象),配套文档 `agent-runtime/docs/guides/openjiuwen-adapter.md`(三步 worked example)、`handler-spi.md`、`memory-services.md` 已覆盖 rail/记忆挂载与注册顺序;AgentScope/Versatile 各有对应 guide。其中 #213 提到的「4 个抽象方法」与 v0.1.0 代码不符(实为 1 个),便捷子类只是把这一个方法的返回类型从 `BaseAgent` 收窄为 `ReActAgent`,属可选 DX 糖。统一选型文档(#221)是有价值的 nice-to-have,我们会作为独立文档工作流纳入 v0.2.0+ roadmap 评估,而不绑定到运行时代码改动。如以上既有用法能满足你们接入需求,烦请确认可关闭;若实际接入中遇到既有 SPI 无法表达的硬约束,欢迎附最小复现,我们再单独评估。

### as-struct — #214,#215,#217 [回退(c)改用既有用法]

感谢 hotel agent dogfood 团队的细致 review。我们对照 v0.1.0(tag v0.1.0 = 846315a4)实际发布的代码复核了这三项,结论是它们属于内部代码结构/可读性偏好,而非 v0.1.0 已发布能力中的硬缺陷,因此倾向在本批次回退、不并入 PR #243,留待 v0.2.0+ 的结构化重构窗口再统一处理:① #214 — v0.1.0 中 AgentScopeAgent 与 AgentScopeHarnessAgent 是两个可正常工作、且在 agentscope-adapter.md 与 L1/L2 文档中公开记录的公开类型,如只想用一个接入点,直接使用 AgentScopeAgent 即可,无需删除公开类型;② #215 — 复核 v0.1.0 AgentScopeStreamAdapter.mapMap(),未识别 status 一直落到 OUTPUT 分支,与本 PR 改后的分类逐字节一致,不存在真实 status 被错分类的运行时缺陷;新增的只是一条 WARN 日志(可观测性增强),不改变对调用方可见的行为;③ #217 — v0.1.0 中两处装配的 5 个字段都取自同一个 invocation 对象,当前取值完全一致,属于 DRY/未来改名维护性诉求,而非现网错配。综上,这三项不构成 v0.1.0 用户无法绕过的缺陷。如该判断与你们在真实接入(尤其是 #215 上游新 status)中观察到的现象不符,欢迎补充可复现的具体 case,我们会重新评估;否则可否确认关闭本组 issue?

### as-correctness — #216,#218,#219,#220 [回退(b)release 已含]

感谢 hotel agent dogfood 团队的细致反馈。我们对照 v0.1.0 实际发布代码逐条复核后,认为这四项在 v0.1.0 中均已得到正确处理,原代码并非缺陷,因此本批改动我们计划回退、保持 v0.1.0 行为:#219(角色塌缩)——v0.1.0 的 RuntimeMessage.Role 枚举只有 USER/AGENT,system/tool 等富角色按设计走 metadata 承载(见 RuntimeMessage 类注释),二元映射对该枚举已是穷尽且无损,不存在 system/tool 被塞成 user 的情况;#218(userId 置空)——RuntimeIdentity 构造时已强制 userId 非空非空白,适配器里的 null 置空分支不可达(死代码),不会产生空串匿名用户或跨租户串联;#216(.join())——v0.1.0 的 send 返回惰性 Stream,.join() 只阻塞到响应头,SSE 正文仍按需流式消费(见代码内注释 Bounds time-to-response-headers only),send() 与 sendAsync().join() 行为等价,不会丢失 first-token/chunk 时机;#220(AGENTSCOPE_ERROR)——v0.1.0 对真实上游 errorCode 始终保留,仅在确实缺失时填通用 sentinel(即未知错误语义),而改动依赖的 ErrorCategory/AgentScopeErrorCategories 映射设施在 v0.1.0 中并不存在(属 Route 1A / #199 范围)。综上,这些目标在 v0.1.0 已满足,无需改动。如果你们后续确有让 client 支持 system/tool 角色、或把错误分类与 #199 ErrorCategory 打通的需求,这属于 v0.2.0+ 的能力扩展,欢迎另开 issue 跟踪。可否确认关闭本组 issue?

### err-safety — #226 [回退(b)release 已含]

感谢反馈并提供了清晰的复现步骤。我们对照 v0.1.0 实际发布代码核查后确认:该问题在 v0.1.0 已不可复现——`RuntimeAccessProperties.defaultTenantId` 默认值即为 `"default"`(非 null),因此即便 `application.yaml` 未配置 `default-tenant-id` 且请求不带 `X-Tenant-Id`,`serverContext(...)` 也不会产出 null 租户;同时 `A2aJsonRpcController.handle()` 的所有返回路径(成功路径经 `handleBlocking` 返回 `ResponseEntity<String>` / 流式经 `handleStream` 返回 `Flux<ServerSentEvent<String>>`,六个 catch 分支均经 `errorResponse(...)` 返回 `ResponseEntity<String>`)都是已注册 converter 可写出的类型,代码中也从未引用 `A2AErrorResponse` 这一 SDK POJO,故 `HttpMessageNotWritableException`(Content-Type=null)在已发布代码里不存在可达触发路径。换言之,规范的 JSON-RPC 错误响应(含正确 `Content-Type: application/json`)是 v0.1.0 本就保证的能力,无需任何配置或代码改动即可获得。基于此我们计划撤回 PR #243 中这条额外的安全网改动(它防护的是一条假设中的未来路径,而非现网缺陷)。如果你们在某个具体部署/版本仍能复现该 WARN,烦请贴出触发请求与版本号,我们会据此补针对性修复。否则建议关闭本 issue,辛苦确认~

### config-decomp — #228 [回退(a)越界/未来范围]

感谢这份很深入的根因分析。复核后我们的结论是：#228 本质是一次内部结构/设计债观察（@Configuration 拆分、@ConfigurationProperties 归属、约定耦合、转换缝内聚），并非用户在使用中遇到的能力缺失或缺陷。v0.1.0 实际发布的代码已经以单文件 RuntimeAutoConfiguration（320 行，含 HealthIndicator/Northbound 等嵌套 @Configuration、全量 @ConditionalOnMissingBean、独立的 toTrajectorySettings 转换方法）正常装配并 ops-ready 上线，AutoConfiguration.imports 的注册入口与 bean 集合在拆分前后字节一致——即拆分不改变任何对外行为。按 v0.1.0 刻意收窄的发布范围，这次 P2-P6 的分解属于内部质量偏好而非发布范围内的修复。因此我们计划在 PR #243 中撤回这组改动（4 个提交 + ADR-0162），让 v0.1.0 的既有结构保持稳定；待后续版本（v0.2.0+）有真实的跨适配器/可测试性诉求时，再以路线图方式系统性引入。说明一下：ADR-0162 中 P1（中立 Card 抽象）原本就是缓议，后续 ADR-0163 是基于另一批 #229-#241 卡片需求才反转 P1 的——那部分独立处理，不随本组撤回。若你认同此处置，请确认可关闭本 issue；任何具体的用户使用受阻场景也欢迎补充，我们会据此重新评估。

### card-neutral-model — E1,#228-P1 [回退(a)越界/未来范围]

感谢深入的根因分析。复盘后我们认为 P1（中立 `engine.spi.AgentCardDescriptor` 模型 + `A2aAgentCardMapper` 投影 + 把 `AgentCardProvider` 迁入 `engine.spi`）应归为 v0.2.0+ 路线项、暂不并入 v0.1.0：v0.1.0 实际代码并未提供该中立模型（`AgentCardProvider` 仍在 `engine.a2a`、直接返回 `org.a2aproject.sdk.spec.AgentCard`，6 个 descriptor 类型在 v0.1.0 源码中均不存在），且这正是我们在本 issue 回复中明确缓议、ADR-0162（已 accepted）以 YAGNI 记录待架构师定夺的那一项。更关键的是，你们关心的"卡片元信息要诚实声明（streaming/skills/security/transport）"在 v0.1.0 下已可达：注册一个 `AgentCardProvider` Bean 返回手工构造的 A2A `AgentCard` 即可整体覆盖（`a2aAgentCard` 为 `@ConditionalOnMissingBean`，存在该 Bean 时直接采用），#229 也提到了这条路径。中立模型唯一新增的价值，是让 A2A-free 适配器（agentscope/openjiuwen）在不触发现有 `protocolNeutralPackagesAreA2aSdkFree` ArchUnit 约束的前提下声明卡片元信息——这恰是 ADR-0162 判定的推测性场景。因此这部分会作为架构演进项交由架构师团队统一裁决（ADR-0163 是否落地需架构师批准），不阻塞 v0.1.0。卡片诚实性的具体诉求（#229-#241）我们会用现有 `AgentCardProvider`/A2A 路径继续跟进。如认可此处置，请确认本条可关闭，谢谢。

### card-honesty — #229,#230,#231,#232,#233 [回退(c)改用既有用法]

感谢 hotel agent dogfood 团队的细致反馈(#229/#230/#231/#232/#233)。复核 v0.1.0 实际发布代码后,我们决定回退本组改动:这五点诉求(诚实的 streaming/pushNotifications、真实 skills、defaultOutputModes=["text"]、版本/protocolVersion)在 v0.1.0 已可通过现成扩展点 **AgentCardProvider** 完整实现,无需改动 runtime 代码。具体用法:在你的应用里注册一个 `AgentCardProvider` Bean(或让 Handler 同时实现该接口),用 `AgentCard.builder()` 手工构造一张完全诚实的卡片——`capabilities.streaming(false)`、`pushNotifications(false)`、真实的 `skills(...)`、`defaultOutputModes(List.of("text"))`、正确的 `version` 与 `new AgentInterface(binding, url, tenant, protocolVersion)`。`RuntimeAutoConfiguration.a2aAgentCard()` 会在生成默认卡之前优先返回你这张卡(`if (cp != null) return cp.agentCard();`),`/.well-known/agent-card.json` 即按你声明的能力对外暴露,这就是 v0.1.0 文档里的"@Bean AgentCard → 完全接管"路径。需要说明的是:`AgentCards.create()` 里的 true/true/[text,artifact] 只是工厂的默认值,不是协议谎报——知道自身真实能力的接入方用上述 override 即可消除偏差;而 v0.1.0 的 YAML(`AgentCardProperties`)与 Handler SPI 当前确实只覆盖 name/description/version 等元信息、尚无 streaming/skills/outputModes 钩子(发布说明 §4.2 与 agent-card-configuration.md 对此有过度承诺,我们会同步修正文档表述)。把 SPI 钩子化、让 YAML/Handler 直接声明 capabilities/skills/outputModes 这类"更省事的声明式入口",我们纳入 v0.2.0+ 路线讨论。麻烦确认是否可以基于 AgentCardProvider 用法关闭本组 issue,谢谢!

### card-security — #234 [回退(a)越界/未来范围]

感谢 hotel agent dogfood 团队的反馈。复核结论：v0.1.0 的 Agent Card 范围是**刻意收窄**的——发布说明 §4.2 明确只覆盖 skills / capabilities / version，securitySchemes / securityRequirements 不在 v0.1.0 卡片范围内；v0.1.0 实际代码（`engine/a2a/AgentCards.java` 的 `AgentCards.create()` 与 `AgentCardProperties`）也确实完全不产生任何 security 字段，亦无 `SecuritySchemeDescriptor` / mapper。因此 runtime 默认不在卡上声明 auth 并非缺陷，而是范围内的正确行为。更重要的是，我们在 PR #243 自检中已证实：默认 emit 一个 `X-Tenant-Id` APIKey scheme 会让卡片被 A2A SDK 的 `A2ACardResolver` 判为不可解析（protobuf-JSON 要求 scope 为 message 对象，spec-JSON 的空数组 `[]` 触发 "Expect message object but got: []"），整张卡变得不可发现——这是比"未声明 security"更严重的回归。基于以上，我们将把这条 security SPI 表面回退，作为 v0.2.0 的认证信令设计项重新评估（需先解决 SDK resolver 的 protobuf-JSON 解析问题）。在此之前，如果你们现在就需要在卡上声明 tenant/auth，v0.1.0 已经支持、**无需改 runtime**：直接提供一个自定义 `@Bean AgentCard`，用 `AgentCard.builder().securitySchemes(...).securityRequirements(...)` 构造（SDK 的 AgentCard record 原生带这两个字段），`AgentCardController` 会原样对外服务——只是请注意上面提到的 SDK resolver 解析限制，建议用能解析 spec-JSON security 的 client 端验证。是否可以按"out-of-scope，转 v0.2.0 路线 + 已提供 v0.1.0 自定义卡用法"关闭本 issue？如需继续跟踪 SDK 侧 resolver 修复也请告知，我们另开跟踪项。

### card-transport-meta — #235,#237,#241 [回退(a)越界/未来范围]

感谢 hotel agent dogfood 团队细致的 card 审查。复核 v0.1.0 实际代码（tag v0.1.0）后,这三项(#235 多 transport additionalInterfaces、#237 documentationUrl/iconUrl/signatures 透传、#241 additionalInterfaces[].url 重写)落在 v0.1.0 刻意收窄的 card 范围之外:发行说明 §3.4 标记 gRPC/其他 transport 当前未实现(仅 HTTP+SSE),§4.2 将 card 范围限定为 skills/capabilities/version —— 在 runtime 没有 gRPC/REST 端点的前提下对外广告额外 transport 反而会发布服务端并不提供的接口;而 #241 所述泄露在 v0.1.0 也只是假设场景(默认 card 的 AgentCards.create() 从不设置 additionalInterfaces,issue 本身亦称"当前不设、未来若启用"),并非已观测到的缺陷。更重要的是:v0.1.0 已经提供 AgentCardProvider SPI —— 实现 agentCard() 即可返回你完整自定义的 A2A AgentCard(含 additionalInterfaces、documentationUrl、iconUrl、signatures、多 transport supportedInterfaces),URL 直接给绝对地址即可,无需框架改动就能满足高安全/catalog 展示等诉求。因此本批改动(新增中立 descriptor 模型 + multi-transport/doc/icon/签名透传)将作为 v0.2.0+ 路线项回退,待 gRPC transport 真正落地后再随之开放声明面。烦请确认是否可以据此关闭这三个 issue,谢谢!

### card-overlay — #236 [回退(c)改用既有用法]

感谢反馈。我们重新核对了 v0.1.0（tag v0.1.0 = 846315a4）的实际代码：`AgentCardProperties` 当前确实只开放 6 个浅字段的 YAML 配置，这是有意的设计——深层卡片字段（skills / capabilities / defaultOutputModes / securitySchemes / 多 transport）被刻意收敛到了 `AgentCardProvider` 这个编程式 SPI，而不是 YAML。也就是说，您"纯配置装一个带 skills 的 agent"的目标在 v0.1.0 无需改动 runtime 代码即可达成：只需注册一个 `AgentCardProvider` 类型的 `@Bean`（或直接提供一个 `@Bean AgentCard`），返回您手工构建的完整卡片即可——`RuntimeAutoConfiguration` 会用它"完全接管"自动生成的卡片（仓内 `VersatileAgentRuntimeHandler` 即为带 skills 的现成范例，见 `agent-card-configuration.md` 方式 A/B）。因此本批 PR 中把 overlay 扩展到深层字段的改动会回退到 v0.1.0 的范围。把深层字段也做成 YAML 全量覆盖是个合理的体验增强，我们将其纳入 v0.2.0+ 的路线（与 2A YAML-first 装配方向合并评估）。如确认上述 SPI 用法满足当前需求，烦请回复以便关闭，谢谢！（另：v0.1.0 release-features §4.2 与 agent-card 配置指南中关于 skills/capabilities "YAML 全量覆盖"的描述与实际代码存在文档漂移，我们会一并修订。）

### card-discovery — #238,#239,#240 [回退(a)越界/未来范围]

感谢 hotel agent dogfood 团队的细致反馈，这三条（#238 canonical Link / #239 Cache-Control+ETag+304 / #240 name 校验告警）我们一并回复。经核对 v0.1.0 实际代码（boot/AgentCardController.java、boot/RuntimeAutoConfiguration.java）：双发现端点 /.well-known/agent-card.json 与 /.well-known/agent.json 在 v0.1.0 已正常提供并返回正确 card（见 release note §4.1）。其中 HTTP 缓存（ETag/Cache-Control/304）与 legacy 路径的 canonical/Deprecation 信号属于发现层加固特性，v0.1.0 有意收窄了范围未纳入；且这类能力是反向代理/CDN 的标准职责——前置 nginx/CDN 即可注入 Cache-Control、计算 ETag、应答 If-None-Match→304 并补 Link/Deprecation header，无需改动应用代码；旧 client 收敛也可经 server 访问日志识别。#240 的 name 不匹配并非 v0.1.0 代码缺陷：卡片仍正常出（createAgentCard 不抛异常），“200+card 但调用被 reject”属配置项 agent-runtime.access.a2a.agent-card.name 设置成了未注册的 agentId，将其改为已注册 agentId（或省略以走 handler 派生默认名）即可完全规避。因此我们计划将 PR #243 中这三项还原，缓存/弃用信号加固纳入 v0.2.0+ 路线图评估。如认可此处置，烦请确认关闭，谢谢！

### mem-react — #247 [回退(b)release 已含]

感谢非常详尽的复现与根因分析。我们对照 v0.1.0（tag `v0.1.0` = `846315a4`，已随 main 发布）的**实际发布代码**核对后发现：这个 ReActAgent 记忆召回丢失的问题，在 v0.1.0 里其实已经修复了，只是走的是另一条注入路径——并不是 issue 里描述的 `ModelContext.setMessages` 路径。v0.1.0 中 `MemoryRuntimeRail`（位于 `OpenJiuwenAgentRuntimeHandler` 内部类）的 `injectMemory()` 会先判断 `callbackContext.getAgent() instanceof ReActAgent`：命中时调用 `reactAgent.addPromptBuilderSection("runtime_long_term_memory", memoryBlock, 50)` 并直接返回，**根本不会走到 `mergeMemoryIntoSystemMessage`**；它写入的是 ReActAgent 每轮真正读取并重建 prompt 的 promptBuilder，因此召回内容会出现在外发 LLM 请求里。v0.1.0 也带了对应的真实 ReActAgent 回归测试 `memoryRuntimeRailInjectsSearchResultsIntoRealReActPromptBuilder`（构造真实 `ReActAgent`，断言 `reactAgent.getPromptBuilder().build()` 含召回内容），正是大家担心被 `RecordingModelContext` 漏掉的那种验证。issue 与之前确认结论复核的是 PR #243 分支（从 merge-base `fb9793b9` 分叉、仅有 `mergeMemoryIntoSystemMessage` 的旧实现），而 PR #243 用 `beforeModelCall`→`ModelCallInputs` 又独立重写了一遍这条已修复的逻辑。因此我们计划在本批次里回退 commit `b544a2c2`，保留 v0.1.0 的 `addPromptBuilderSection` 路径（v0.1.0 release notes §2 第 65 行也标注了「记忆仅在 ReAct 轮次开始前注入」即为此行为；中途检索仍为 ⬜ roadmap）。如对此处置有异议，或你的现场（`agent-hotel-a2a`）确实是基于 v0.1.0 jar 仍观察到召回丢失，请贴一下 v0.1.0 实际运行下 `getPromptBuilder().build()` 的内容，我们再深入排查；否则烦请确认可关闭。


## 8. 执行记录(2026-06-14)

本报告确认后已执行的动作:

- **37 个 issue 逐条贴出 defensive 回复**(#197-205、#210-221、#226、#228-241、#247),均以「请提交者确认是否可关闭」收尾,未擅自关闭。
- **PR #243 已关闭**(`CLOSED`),并贴了汇总裁决评论。理由:#243 基于旧 main `fb9793b9`、与 v0.1.0 完全分叉且未合并,KEEP=0 → 全量不带入 = 关闭即可,`main` 无代码改动。**分支 `fix/issues-2026-06-13-runtime-batch` 保留备查,可重开。**
- **架构团队待裁项**:Card 中性 `AgentCardDescriptor` 模型 + ADR-0163(推翻 accepted 的 ADR-0162 P1)。因 #243 不合并,ADR-0163 不落 main、不构成既成决策;是否在 v0.2.0 引入由架构团队单独决定。
- **未触碰**:#190(他队 agent-sdk)、#191(另案跟踪)、#206(需截图)—— 不在 #243 改动范围。
- 本报告经独立 PR 入库以保持决策可追踪。

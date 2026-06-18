# MemOpt 企业级记忆 —— kit 范式 + 闭源引擎(形态 C)设计稿

> **⚠️ 已重构(2026-06-16)**:与需求方对齐后,架构调整为**两层 kit**——
> **A2A 共享记忆**是独立中间件 kit(模块 `a2a-shared-memory/`,本次已落地),
> **MemOpt** 退化为其**可插拔后端之一**(闭源引擎,属后续后端阶段)。
> 本文中"MemOpt 承载 A2A 共享记忆"的旧表述以 **[the a2a-shared-memory design decision](2026-06-16-a2a-shared-memory-design-decision.yaml)** 为准。
>
> **⚠️ 最终交付决策(2026-06-16,本文下方推演中已被取代的点)**:MemOpt 后端阶段
> 最终落地为 **闭源容器镜像 + 现有 HTTP `/v1` 契约**(`a2a-shared-memory-memopt` kit
> 即该镜像的瘦 HTTP 客户端,见其 README + MemOpt 仓库 `docs/DEPLOY_CONTAINER.md`)。
> 因此下方**两条"已确认决策"已被推翻,以本横幅为准**:
> - 第 6 条"引擎 Java 重写"→ **取消**:MemOpt 仍为 **Python**,以镜像交付(不开源源码),不重写。
> - 第 11 条"传输 = gRPC 为主"→ **取消**:传输用**现有 HTTP `/v1`**(可选 bearer token + TLS);
>   gRPC / mTLS / protobuf 仅为本文推演,**未采纳**。
> 本文其余内容保留为设计推演历史。

状态:草案(设计评审稿,未进架构-of-record;已被上方重构说明与 the a2a-shared-memory design decision 取代主体)。
日期:2026-06-16
关联:`docs/governance/contracts/architecture-design.md`(设计阶段契约)、`financial/kit`(kit 范式范本)、collaboration 工作区(A2A 协作引擎,A2A 共享记忆的承载)。

---

## 0. 已确认决策(2026-06-16,与需求方对齐)

1. **A2A 共享记忆跨 run**:不仅 run 内黑板,还要**跨 run 持续沉淀"协作经验"**(哪种协作模式/结论有效)→ 两层结构。
2. **非平等访问 = 所有权写入模型**:每个 agent **只写自己负责的结论**,其他 agent **只读、不可改**(写入按 owner 锁定,读共享)。
3. **冲突语义 = append-log + 出处**(可审计;银行场景)。
4. **per-user 记什么**:偏好 / 风险态度 / 对话结论;**持仓等权威数据走 SoR,不进记忆**。(确认)
5. **per-user 隔离粒度**:默认 `tenantId + userId`(用户记忆在该用户的各 agent 间共享);`agentId` 作为可选子命名空间(某 agent 需要私有用户记忆时再用)。
6. **引擎 Java 重写**:MemOpt 引擎用 **Java** 自实现(不复用、不改动 Python `doushuaigong`)。
7. **经验层强制脱敏**:跨 run 经验蒸馏时**必须剥离用户 PII**(租户级共享,绝不把某客户隐私漏给别的协作)→ PrivacyRail 默认强制。
8. **任务签名 = 能力组合 + 任务类型**(决定经验召回的相关性)。
9. **经验蒸馏触发点 = Coordinator 的 run 结束钩子**(它最清楚一次协作全貌;复用已有事件流)。
10. **所有权 + 交接**:handover A→B 后,B 只能写**自己的新 key**,**A 写的 key 仍只读**(A 的结论不可被改)。
11. **传输 = gRPC 为主**:引擎边界用 **gRPC + protobuf 契约**(强类型、HTTP/2 多路复用、原生流式与 mTLS,Java 引擎一等支持,平台已带 protobuf 工具链);可选保留**薄 HTTP/JSON 网关**给调试/简单接入。

---

## 1. 背景与决策

需求:在 spring-ai-ascend 上提供**运行态**记忆能力,服务两类消费者:

1. **面向用户的长期记忆**(例:理财偏好;"我之前买了什么"——其中**持仓走银行 SoR,不进记忆**,记忆只存 SoR 没有的软信息)。
2. **A2A 多智能体协作的共享记忆**(黑板/working memory)——**优先级更高**,因为现有 handover/distribution 只传 payload、不传累积共享知识。

约束:手机银行级(月活可能上亿),每用户记忆**隐私隔离**、**成本可控**;MemOpt 有技术 IP,**不开放引擎源码,但要可被调用**;**不考虑与 openJiuwen 兼容**(企业级记忆自立门户)。

**决策(形态 C)**:**闭源引擎以容器交付 + 开放 kit 客户端 + 版本化 wire 契约**。引擎跑在客户(银行)内网,源码不出门;客户通过开放 kit 调用。

被否方案:A 纯 SaaS 远程(银行数据驻留通常不允许出行)、B 进程内闭源 jar(Java 字节码可反编译,保护弱,除非混淆)。C 在"IP 保护 / 数据驻留 / 运维"三者间最平衡。

---

## 2. 目标 / 非目标

**目标**
- 运行态、per-user 隔离的长期记忆;A2A 协作共享记忆。
- 引擎闭源、容器交付、内网自托管、源码不外泄。
- 开发体验:沿用 `financial/kit` 范式,业务开发者"继承基类/调门面 + 填意图"即可。
- 海量用户下成本可控(蒸馏、封顶、冷热分层、复用 SoR)。

**非目标**
- 不做开发者**开发态**记忆。
- 不在本仓开放引擎实现(只开放 kit + 契约)。
- 不绑 openJiuwen 记忆模型(只在一个薄适配层接 `MemoryProvider` SPI)。
- 持仓/交易等权威数据不进记忆(实时查 SoR 工具)。

---

## 3. 架构:开/闭边界

```
┌─────────────────────────── 本仓 spring-ai-ascend/memopt/(开放) ───────────────────────────┐
│  MemoryKit / SharedMemoryKit (门面)                                                          │
│    + memory rails: ScopeIsolation · Privacy · Cost · Audit                                   │
│    + wire client (gRPC, fail-open + 熔断 + 超时;可选 HTTP/JSON 网关)                         │
│    + MemoryProvider 适配(接 agent-runtime 长期记忆轴,可选)                                  │
│    + proto 契约(memopt.v1)+ 对 stub 的测试 + TCK                                             │
└───────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                             │  gRPC + protobuf 契约 memopt.v1(唯一共享面,开放 .proto)
                                             ▼
┌──────────────── 独立私有 repo + Docker 镜像(闭源,只给镜像)──────────────────────────────┐
│  MemOpt Engine:蒸馏 / 索引·排序 / 存储分层 / embedding / 多租户隔离(真 IP)               │
│  在银行内网运行;服务端强制租户·用户隔离;鉴权(mTLS / token)                              │
└───────────────────────────────────────────────────────────────────────────────────────────┘
```

三层职责:
- **开放 kit**:消费面 + 客户端韧性 + scope 约定 + 运行时适配。无 IP,可给源码。
- **版本化契约**:**gRPC + protobuf**(`memopt.v1` proto package);双方唯一耦合点,强类型、可 codegen、独立演进(R-C.1)。可选 HTTP/JSON 网关仅作调试/简单接入。
- **闭源引擎**:IP 所在;server-sovereign(P-M);只交付镜像。

---

## 4. 开放 kit 表面(对齐 `financial/kit` 范式)

`financial/kit` 范式提炼:抽象基类 + 极少"必须定义" + 可覆盖安全默认 + 横切 rail(默认空、override 即挂)+ 平台接线封 `final` + 声明式 YAML 旁路 + `templates/` 成品。MemOpt 照此:

### 4.1 per-user 长期记忆门面
```java
// 继承式:业务开发者只填"记什么"
public abstract class AbstractUserMemoryKit {
    protected abstract MemoryScope scope(AgentExecutionContext ctx);   // 必填:身份来源
    protected List<Fact> distill(Turn turn) { /* 默认:抽取要点 */ }    // 可覆盖
    // 安全默认:fail-open、熔断、每用户事实上限、TTL、蒸馏开
    // 平台/后端接线:final,开发者不碰
    public final List<MemoryHit> recall(String query, int limit) {...}
    public final void remember(List<Fact> facts) {...}
    public final void forget() {...}                                   // 被遗忘权,一行
}
```

### 4.2 A2A 共享记忆门面(优先级最高,**两层**)

**第一层 — run 内共享黑板**(working memory,协作进行中):
```java
SharedMemoryKit shared = SharedMemoryKit.forCollaboration(taskToken); // key = tenantId + 协作根 taskId
// 所有权写入:agent 只写自己负责的结论,key 归 writerAgentId 所有
shared.put("riskAssessment", result);   // 只有 risk agent 能写/改这个 key
shared.get("riskAssessment");           // 其他 agent 只读
shared.list();                          // 按 capability scope 过滤可见(级联权限令牌,ADR-0052)
// 冲突:append-log + 出处(writerAgentId, ts),不静默覆盖;可审计
```

**第二层 — 跨 run 经验记忆**(experience memory,协作结束后沉淀):
```java
// run 结束:把本次协作的有效结论/模式蒸馏成持久"经验"
ExperienceMemoryKit exp = ExperienceMemoryKit.forTenant(tenantId);
exp.record(collaborationSignature, distilledLessons);   // key = tenantId + 任务签名/能力组合(不按 user)
// 新协作开始:召回相关历史经验,指导分发/交接
exp.recall(collaborationSignature, limit);
```
经验层**按 tenantId + 任务签名(能力组合/任务类型)**索引,**不按 user**;是协作域的长期记忆,区别于 per-user。

### 4.3 横切 memory rails(开箱即用的企业级能力,kit 内置兜底)
| Rail | 职责 |
|---|---|
| `ScopeIsolationRail` | 每次调用强制带 tenantId+userId;隔离不可绕过 |
| `PrivacyRail` | PII 最小化、按 userId 一键遗忘、保留期;**经验层 record 前强制剥离用户 PII** |
| `CostRail` | 蒸馏、每用户封顶、冷热分层、写入摊销 |
| `AuditRail` | 结构化审计(双模可观测:dev 详尽 / 运行态精简) |

### 4.4 声明式旁路 + 模板
- YAML 声明:某 agent 记哪些、保留多久、隔离粒度。
- `templates/`:`UserPreferenceMemory`(理财偏好)、`A2ASharedBlackboard`(协作共享)。

---

## 5. wire 契约 —— gRPC + protobuf(`memopt.v1`)

唯一共享面;proto package 带版本(`memopt.v1`),大版本不兼容明确报错(协议兼容性)。下表是 RPC 概形,字段以最终 `.proto` 为准。

### 5.1 A2A 共享记忆(先做,两层)—— `service SharedMemory` / `service Experience`

**run 内黑板** `service SharedMemory`:
| RPC | 入参概形 | 说明 |
|---|---|---|
| `Put` | `collaborationId`(=协作根 taskId)、`key`、`value`、`writerAgentId`、`token` | 写自己的结论 |
| `Get` | `collaborationId`、`key`、`readerAgentId`、`token` | 读(可只读最新) |
| `List` | `collaborationId`、`token` | 按 capability scope 过滤可见 key |
| `Subscribe`(server-stream) | `collaborationId`、`token` | live 订阅黑板变更(gRPC 原生流式) |

语义(锁定):
- **所有权写入**:key 首次写入绑定 `writerAgentId`;后续**仅 owner 可写/改**,他人写同 key → `PERMISSION_DENIED`。服务端强制,不信客户端。
- **append-log + 出处**:每次写追加一条(value, writerAgentId, ts),不静默覆盖;读默认取最新,可拉历史。
- run 结束:触发经验蒸馏(下),黑板可归档/释放。

**跨 run 经验** `service Experience`:
| RPC | 入参概形 | 说明 |
|---|---|---|
| `Record` | `tenantId`、`signature`、`lessons[]`(蒸馏+脱敏后) | run 结束沉淀 |
| `Recall` | `tenantId`、`signature`、`top_k` | 新协作开始召回 |

语义:
- `signature` = **能力组合 + 任务类型**;按 `tenantId + signature` 索引(**不按 user**)。
- **强制脱敏**:Record 前剥离用户 PII(PrivacyRail);经验只留"协作模式/结论"层面的知识。
- **触发**:由 collaboration `Coordinator` 的 **run 结束钩子**蒸馏并 Record。
- 新协作开始时 Recall 相关经验,指导分发/交接。

### 5.2 per-user 长期记忆 —— `service UserMemory`
| RPC | 入参概形 | 说明 |
|---|---|---|
| `Recall` | `query`、`top_k`、`scope{tenantId,userId,agentId?}` | 召回(可 server-stream 大结果) |
| `Remember` | `records[]`(蒸馏后)、`scope`、`infer` | 写回 |
| `Forget` | `scope{tenantId,userId}` | 被遗忘权 |

> 现有 `DoushuaiRestMemoryProvider` 的 HTTP `/v1/memory/search|save` + scope + fail-open/熔断,是**模式上的雏形**(瘦客户端 + 契约 + 降级);MemOpt 契约切到 gRPC、引擎 Java 重写。

### 5.3 边界安全(C 是网络服务,必须)
- 传输:**gRPC over mTLS**(双向证书);如开 HTTP 网关另加 bearer token。
- **服务端强制隔离**:不信客户端传的 scope,由银行网关注入**认证身份**(gRPC metadata,类比 `X-Tenant-Id`);引擎按注入身份过滤(R-J / P-J 租户隔离)。
- 客户端 scope 仅作路由/可读性,**不是**安全边界。
- gRPC 原生 **deadline / cancellation** 贯穿超时与取消。

---

## 6. 横切设计(DFX 五维,呼应个人工程质量底线)

| 维度 | 设计 |
|---|---|
| **鲁棒性** | 记忆是旁路:fail-open + 熔断 + 独立超时(kit 已有);引擎挂不拖垮主响应 |
| **协议兼容** | protobuf `memopt.v1`(加字段向后兼容、大版本不兼容明确报错);kit 与引擎独立演进(R-C.1) |
| **韧性/反压** | 客户端熔断 + 退避;引擎侧限流(海量并发) |
| **可观测(双模)** | 同一埋点配置切强度:dev 详尽逐操作、运行态精简(采样、低基数、热路径守卫);AuditRail 承载 |
| **大规模弹性** | per-user 共享存储按 (tenantId,userId) 分片(**不是每用户一张表**);冷热分层只为活跃会话付费;引擎可水平扩 |
| **成本** | 蒸馏而非堆原文、每用户封顶、复用 SoR(持仓不进记忆)、结构化 profile 走 KV / 仅模糊项进向量 |

---

## 7. 仓库布局与模块边界

- `spring-ai-ascend/memopt/`(新一等模块,**与 agent-runtime 并列**):开放 kit + 客户端 + 契约 spec + `MemoryProvider` 适配 + stub 测试 + TCK。
- **不反依赖 agent-runtime**:MemOpt 定义自有门面;接 runtime 的耦合关在薄子模块 `memopt-runtime-adapter`(实现 `MemoryProvider`)。
- 引擎:**Java 重写**,**独立私有 repo + Docker 镜像**,不在本仓(不复用/不改 Python `doushuaigong`)。
- 包名:`com.huawei.ascend.memopt`(从 `examples…doushuai` 迁移)。

---

## 8. 测试策略

- **kit/客户端**:对**进程内 gRPC stub server**(grpc-java in-process)测,离线、含 fail-open/熔断/超时/scope。
- **契约 TCK**:一套契约一致性测试,引擎实现必须过(R-D 的 TCK 共同设计)。
- **A2A 共享**:接 collaboration 工作区的 `Coordinator`,验证多 worker 经 token 共读写。

---

## 9. 阶段与待决

**阶段**(A2A 共享优先):
1. A2A 共享记忆契约 + kit 门面 + 对接 Coordinator(原型,in-memory 后端可评测)。
2. per-user 长期记忆契约 + kit。
3. MemOpt 抽取成一等模块 + `memopt-runtime-adapter`。
4. 引擎私有 repo + 镜像交付 + mTLS/鉴权。

**已决**(2026-06-16,见 §0,共 10 条):访问 = 所有权写入(+ 交接后 A 的 key 只读);冲突 = append-log + 出处;跨 run 经验**在范围内**(`tenantId + 能力组合·任务类型` 索引、强制脱敏、Coordinator run 结束钩子触发);引擎 Java 重写;per-user 记什么 / 隔离粒度确认。

传输已定:**gRPC + protobuf `memopt.v1` 为主**(可选 HTTP/JSON 网关),见 §0.11 / §5。

**仍待决**:
- 抽取/改名涉及你自有的 doushuaigong 代码——破坏性重构,**等明确指令再动**;且**当前先不提交**。

**治理提醒**:本稿在 `docs/logs/reviews/`(门禁语料排除)。若提升为正式架构变更,需补:`docs/adr/NNNN-*.yaml`、`docs/dfx/memopt.yaml`(五维)、`module-metadata.yaml#spi_packages`、`docs/contracts/contract-catalog.md`,并过 G-1/G-2/G-8(架构图字节级重生成)。

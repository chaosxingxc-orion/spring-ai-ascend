# AI 原生研发范式 —— B 篇：存量治理/知识资产处置 charter（判据驱动）

Date: 2026-06-04
Status: decision ledger / 待 owner 批准后分步执行
Owner（方向 + 批准）: repository owner
前篇（A）: `docs/reviews/2026-06-04-ai-native-rd-paradigm-reflection.zh.md`
范围: 本文是 **B**——拿 A 的判据把存量资产逐项判决并给出可执行减法。**A 立范式，B 处置存量。**
判据来源: A 篇 §11
证据来源: 本仓侦察（2026-06-04，对当前真实状态盘底，非记忆旧数）
本轮决策拨盘（owner 已定）:
- **共享逻辑层 = 激进重置**（phase contracts 5→1；P 原则只留能测试表达的、其余归档；ADR 激进撤权威只留 live seam-ADR）。
- **「派生」本轮只立判据 + 设计出口，不动手实现**（派生器实现留作脊柱稳定后的独立切片）。

诚实声明: 下文 gate 的"留/拆/派生"三桶，是读**规则头**得到的分类；**执行时每条须对规则体复核**。这条声明是为了不让本文掉进它批判的"无 grounding 产物"坑。

---

## 1. 判据（一句尺子）

> **这个资产，是在给运行系统装证伪器，还是在生产又一份会漂移的产物？**

装证伪器的——留或强化。生产会漂移产物的——退役、或改造成"从运行系统派生"，让它定义上不可能漂移。

## 2. 四种判决

- **薄留**：本身就是证伪器 / 接缝守卫 / 脊柱不变量 / 无法用测试表达的安全约束。留，必要时下沉成 gate 检查。
- **派生**：当前是手维护的投影（图、状态、计数、目录）。改成从运行系统 emit；**它一派生，专门 policing 它的那批 gate 仪式规则同时消失**。
- **改造**：知识体系——从"人维护的语料库"改造成"对活系统的查询能力 + 自动派生投影"。
- **退役**：用流程当成本函数、要求预先内化全局、无 grounding 的规约/essay。激进者直接删；保留价值仅限历史者，移出主路径归 `docs/logs/`。

---

## 3. 总账（当前真实存量 → 判决）

| 资产 | 当前量（侦察实测） | 判决 |
|---|---|---|
| gate 证伪规则 | ~半数 / 56 条规则卡、2019 行 monolith | **薄留** |
| gate 仪式/policing 规则 | ~另半数 | **派生（随派生消失）/ 退役（纯 meta）** |
| phase contracts（/design·/impl·/verify·/commit·/review-mode） | 5 | **退役 → 收成 1 薄** |
| 治理原则 P-A…P-M | 13 | **激进重置：留能测试表达的、其余归档** |
| 协作原则 D-1…D-9 / G-7（kernel） | 9 | **薄留**（可测试部分下沉 gate） |
| `contracts/*.v1.yaml`（接缝） | 43 | **薄留脊柱实穿的，归档投机的** |
| ADR | 61 | **激进撤权威：留 live seam-ADR，删超期/过程史** |
| 架构记录（workspace.dsl / L0·L1 / architecture-graph·status.yaml） | — | **派生（本轮只设计）** |
| 知识系统（`knowledge/` + `_tools/`） | README + check_integrity.py + search.sh | **改造** |
| 架构月规划 essay（competitive-baselines / whitepaper-alignment-matrix / evolution-modalities / posture-coverage / skill-capacity / w6-sunset…） | 一批 yaml/md | **退役 → 归档 `docs/logs/`** |
| 历史台账（rule-history / retired-rules-audit / recurring-defect-families / retracted-tags） | — | **薄留为历史**，移出 always-loaded |
| `docs/onboarding/` | **空目录** | n/a（印证"入场≠语料库"） |

---

## 4. 激进重置：共享逻辑层怎么拆

**4.1 Phase contracts 5 → 1。**
`architecture-design / engineering-implementation / integration-verification / system-commit / review-response` 五份契约 + 五个 `*-mode` 技能，是"用流程当成本函数 + 要求预先内化全局"的本体。收成**一份薄 `how-we-work.md`**，只讲四件事：
1. 跑脊柱（端到端 sample 必须绿）；2. 挂接缝（动 seam 走重流程，模块内放开）；3. 保持脊柱绿（gate 是真门禁）；4. 用赛跑 spike 取代评审辩论。
五个 `*-mode` 技能：退役，或并成一个 `/work` 薄入口。**门禁的活交给 gate，不交给"进相位前先把 50 条规则读进上下文"。**

**4.2 原则 P-A…P-M（13）。**
逐条问：它有没有对应的 gate 检查？
- **有** → 原则散文是冗余，**gate 规则本身就是那条原则**；留 gate，原则 prose 归档。
- **没有、但能落成检查** → 下沉成一条 gate 规则，prose 归档。
- **没有、也落不成检查** → 它是纯 essay（"共享逻辑体系"层），归档为历史，不再 always-load。
预期净留 2-3 条真不变量，其余进 `docs/logs/`。

**4.3 ADR（61，激进撤权威）。**
- **留**：gate 仍引用的 **live seam-ADR**（侦察可见被引用的：0042/0045/0055/0059/0067/0068/0074/0078/0159…），它们记录的是活接缝决策。
- **删**：超期 / 被推翻 / 过程史 / over-reach 的 ADR（沿用你一贯的 aggressive validity cleanup，REMOVE 而非 immutable-history）。
- **撤权威但留档的中间态**：尚不确定的，先把"代码必须对齐 ADR"的权威性摘掉，降级为历史，软跑后再删。
原则：**ADR 是该被扔掉的 spike 的决策记录，可作历史，但绝不是代码必须对齐的权威。**

---

## 5. 派生（本轮只立方向 + 出口，不实现）

**方向**：架构记录（`architecture-graph.yaml`、`architecture-status.yaml`、契约目录 SPI 表、SPI 计数、fact 层）应当从**单一 emitter**（`workspace.dsl` + 源码扫描）产出。机器已部分存在（`gate/build_architecture_graph.py`）。目标态：**记录是输出，不是输入；reality→record 自动成立，ADR-0159 那种事后对齐的痛消失。**

**Retire-on-derive 清单（派生器落地即退役，本轮先打标不删）**：
- Rule 17 `contract_catalog_spi_table_matches_source` → 派生后目录即源码投影，规则无意义
- Rule 38 `architecture_graph_well_formed`、Rule 42 `architecture_graph_idempotent` → 图被 emit，well-formed/idempotent 是 emitter 的属性
- Rule 95 `spi_catalog_exhaustiveness`、Rule 129 `contract_spi_count_truth` → 计数从源码 emit
- Rule 104 `openapi_implemented_route_catalog_truth` → 路由目录从实现 emit
- Rule 106 `cross_authority_parity` → "多份文档保持 lockstep"的典型，派生后只有一个源，parity 恒真
- Rule 131 `fact_layer_integrity` → fact 层本就是投影

**本轮交付**：上面这份"派生设计 + retire-on-derive 映射"。**派生器实现 = 脊柱稳定后的独立切片**，不在本轮。

---

## 6. gate 瘦身：留证伪器、拆仪式（执行时按规则体复核）

**薄留（证伪器 / 接缝 / 安全 —— 这是新范式的核心资产，不许烧）**：
Rule 7 `shipped_impl_paths_exist`、10 `module_dep_direction`(守接缝)、11 `contract_spine_tenant_id_required`、16 http 契约一致性、19/24 shipped evidence、28a/b/c tenant·secret·cardinality、36 `domain_module_has_spi_package`、50 `rls_for_new_tenant_tables`、58 s2c callback、105 `edge_no_direct_compute_link`(守接缝)、115 D-9 lint、127 `release_note_no_pending_evidence`。

**派生桶（随 §5 派生器落地退役）**：17、38、42、95、104、106、129、131（+28f/28j 治理产物自检）。

**退役桶（gate 查 gate 的纯 meta / 过程 ceremony，无派生依赖，可较早删）**：
28d `out_of_scope_name_guard`、41 `enforcer_anchor_resolves`、88 `serial_parallel_gate_slug_parity`、89 `self_test_harness_fail_closed_coverage`、111 `architecture_refresh_defect_family_re_eval`[META]、140 `shipped_frame_anchor_integrity`、28k javadoc citation。

---

## 7. 契约过度规约：43 → 脊柱实穿

**事实**：43 个 `*.v1.yaml` 配一个 3 天的 runtime = 架构月的过度规约，多数描述的接缝**脊柱还没穿过**。
**判据**："脊柱实穿" = 被那条 always-green 的端到端 sample（A2A LLM e2e）真实跨越的契约。
**方法**：从 sample 反向 trace 它经过的接口 → 命中的 `.v1.yaml` 为 live，其余降级为 `proposed/`（投机，未 ground）。
**排程**：本轮立判据；triage 作**紧邻减法**（需轻量 trace 脊柱，非派生那种重活）。

## 8. 知识系统改造方向

`knowledge/` 已在对的方向（advisory + searchable）。再推一步：
- `search.sh` 是"对活系统的查询能力"的种子 → 投资它能查到运行系统/源码/契约，而不是查一份并行语料。
- `check_integrity.py` 保持 **advisory**（红线：它一旦变成 coverage/authority gate，病就回来了）。
- `knowledge/README.md` 的框架从"语料库"改写成"对活系统的查询能力 + 自动派生投影"。

## 9. 本轮明确不动的（边界）

- 派生器**实现**（只设计）。
- 契约 triage 的**实际降级**（只立判据 + 方法）。
- 运行时产品代码（B 是处置治理/知识资产，不碰 runtime）。
- 历史台账内容（只改其"是否 always-load"，不改其历史记载）。

---

## 10. 执行顺序

1. **立即减法（本轮批准即可落地，纯减法、低风险）**：phase contracts 5→1 + 退役 5 个 `*-mode`；P 原则按 §4.2 三分；架构月 essay 归档 `docs/logs/`；历史台账移出 always-load；空 `onboarding/` 确认删。
2. **激进 ADR 清理**：删超期/过程史 ADR，撤其余权威（保留 live seam-ADR）。需对 gate 引用复核 → 软跑一遍 gate 确认无悬挂引用。
3. **gate 退役桶**（§6 第三桶）：删纯 meta 规则 + 同步 baseline。
4. **立判据 + 设计、不实现**：§5 派生设计 + retire-on-derive 映射；§6 三桶分类落档。
5. **紧邻减法（下一步）**：契约 43→spine triage。
6. **未来独立切片**：实现派生器 → 触发派生桶规则退役。

每步都是**独立可 revert 的 commit**，落 B 分支，**未经 owner 批准不并 main**（沿用 rebalancing charter 的安全不变量）。

---

## 11. 验收：B 怎么证明自己不是又一份会漂移的 essay

B 必须对自己也可证伪。它的成功**不是"产出了一份新规约"**，而是一组可观测的删除/降级：

- phase contracts：5 → 1；`*-mode` 技能：5 → ≤1。
- P 原则 always-load：13 → 2-3。
- ADR：61 → 显著更少（删超期）。
- gate 规则：退役桶清空 + 派生桶打上 retire-on-derive 标。
- 架构月 essay：从主路径消失（进 `docs/logs/`）。
- **以上全部体现在一次 green 的 gate run 里**（减法不能把脊柱跑红）。

> **若 B 做完没有任何文件被删、没有任何规则被拆、only 新增了文档——那么 B 失败了，它变成了它自己批判的东西。** 这是 B 给自己装的成本函数。

---

## 12. 自检课正（2026-06-04 执行中发现 —— 覆盖 §4 / §10 / §11 的前提）

执行时做了**仓库级**引用扫描（兜底纪律），**§4 的"孤儿 / 整对退役"前提被证伪**：

- 团队的退役程序退的是**门禁规则**，不是**制品文件**。被点名的制品（skill-capacity / sandbox-policies / evolution-scope / bus-channels / competitive-baselines / evolution-modalities / deployment-loci）**仍被活语料库重度引用**——`docs/contracts/contract-catalog.md`、live `architecture/docs/L1/agent-service/*`、`samples/finance-loan-review`、`product/journey.md`、`deploy/` 模板、ADR-0101/0102。归档或删除会制造**数十处悬挂引用 = bug**。
- phase contracts / `*-mode` 技能同理：`architecture-design` 28 处、`impl-mode` 42 处 live 引用（CLAUDE.md / CONTRIBUTING / ADR-0119 / PRODUCT.md / persona 文档 / rule-G-11 / dist/skills）。collapse = 制造 bug。
- `structurizr-w6-sunset` 的 soak **跑到 2026-07-25**，非废弃。

**结论：可安全删除/归档的"残骸" ≈ 0。** §11 的成功判据（"只新增文档 = 失败"）**其前提即错**——它假设存在可移除的承重外残骸，而这些制品其实是**语料承重**的。在 owner 的"自检无 bugs"约束下，正确动作恰恰是 **不删**。

这反而是 **A 篇的硬验证**：架构月把 speculative 概念**织进了**文档语料，已**无法干净切除**。根治不是冒险大删，而是让**运行脊柱 + 派生记录**随时间成为权威（见 `2026-06-04-derive-baseline-metrics-design.zh.md`）。

**本轮实际落地（全部 bug-free）**：
- W1 `active_gate_checks` 35→32 全面对齐（6 面，grep 零残留）——团队退掉 Rule 82 后留在 main 的真实漂移。
- 派生设计（design-only）+ 契约 triage 判据/标注（label-only）+ 知识 README 轻量收口。
- **未执行**（自检判定会制造 bug）：W2/W3/W4/W8 的删除/归档/collapse。

— 完（B 篇 + 自检课正）。

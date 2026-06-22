---
level: L1
view: governance
status: draft
---

# A2D 工具构建计划(步骤 3 + 步骤 8)

## 0. 本文档定位

本文是 **A2D 工具链(步骤 3「架构更新稿」+ 步骤 8「基线固化」)的构建参照**,不是 A2D 流程本身的一环。

- 流程主表(9 步、两闸门、4+1 参与度)见 [a2d-expanded-flow.html](a2d-expanded-flow.html)。
- 阶段输入/输出/工具链权威定义见 [a2d-stage-inputs-and-tools.md](a2d-stage-inputs-and-tools.md)。
- 制品模板见 [a2d-artifact-templates.md](a2d-artifact-templates.md)。
- 本文只回答:**这两个步骤的工具要建成什么样、需要哪些组件、按什么切片落地**。

本文是 v2 编号体系下的构建计划。步骤编号一律采用 [a2d-expanded-flow.html](a2d-expanded-flow.html) 的 9 步编号,不使用旧 H0/H1/H2/H3 + AI-1..9(迁移见 §7)。

---

## 1. 已确认决定

### 1.1 形态决定(4 项)

| 编号 | 决定 | 含义 |
|---|---|---|
| D1 | **Claude 插件(市场分发)** | 不做成较轻的 project skill;走 plugin + marketplace 形态,可团队分发 |
| D2 | **步骤 3 完整 skill(含影响分析)** | 端到端:影响分析 → delta 草稿 → HTML 渲染 → 升级标记 lint |
| D3 | **输出 expanded-flow.html 风格静态审阅 HTML** | 渲染目标是「审阅文档」(表格/卡片),不是 PlantUML 图 |
| D4 | **全部切到 v2 的 9 步编号** | 废弃 H0/H1/H2/H3 + AI-1..9 与 9 信息对象双编号(迁移见 §7) |

### 1.2 实施细节决定(5 项)

| 编号 | 决定 |
|---|---|
| D5 | 命名:plugin=`a2d`、marketplace=`a2d-toolkit`、skill=`arch-update-draft` / `baseline-freeze` |
| D6 | 插件代码落在当前仓库 **`tools/a2d-plugin/`**,先用 `/plugin marketplace add ./tools/a2d-plugin` 本地测试,后续再拆独立 repo |
| D7 | renderer 技术栈:**Python**(与 graphify 一致;Jinja2 模板) |
| D8 | 落地顺序:先消化本计划,不立即启动 S0/S1 |
| D9 | 本计划固化为本文档,供消化与后续推进参照 |

---

## 2. 4+1 枢纽现状(关键背景)

工具链不是凭空建,要先看清 4+1 这个枢纽现在到了哪一步。

| 层 | 现状 | 证据 |
|---|---|---|
| 图源层 | ✓ **存在** | 8 个 `.puml`:`docs/architecture/l0/architecture-views/plantuml/l0/`(scenario/logical/development/process/physical)+ `plantuml/common/`(elements/links/theme) |
| 图渲染层 | ✗ 缺失 | README 规划了 `scripts/render-architecture-views.sh`(Docker + PlantUML + C4-PlantUML),脚本未落地 |
| 导出层 | ✗ 缺失 | `exports/svg/`、`exports/png/` 不存在,无 SVG/PNG 产物 |
| 数据模板层 | ✗ 不存在 | 4+1 五视图的**结构化字段模板**(每视图填什么 delta)尚无定义 |
| 审阅文档生成器 | ✗ 不存在 | [a2d-expanded-flow.html](a2d-expanded-flow.html) 是**手写样板**,无生成器 |

> ⚠ **README 路径 bug**:[architecture-views/README.md](../architecture-views/README.md) 通篇写 `docs/architecture-views/`,真实路径是 `docs/architecture/l0/architecture-views/`。属文档缺陷,本文不负责修,但在 §9 记一笔。

**结论**:A2D 工具链(步骤 3/8)的本质,是把「只有 .puml 图源」的 4+1 枢纽补齐到「可审阅 / 可 diff / 可基线化」。图事实有源,但从图到「可审阅文档」的整条链都要新建。这直接影响 §6 renderer 的设计。

---

## 3. 插件仓库布局

照本机 `karpathy-skills` 的**单 plugin = marketplace** 最简形态:仓库根同时是 marketplace 根和 plugin 根,`marketplace.json` 里 `source: "./"`。

```
tools/a2d-plugin/                         ← 仓库根 = marketplace 根 = plugin 根
├── .claude-plugin/
│   ├── plugin.json                       ← 插件清单
│   └── marketplace.json                  ← 市场清单(source: "./")
├── skills/
│   ├── arch-update-draft/                ← 步骤 3(闸门 1 前)
│   │   ├── SKILL.md
│   │   ├── templates/                    ← delta 模板、4+1 五视图 delta 模板
│   │   └── scripts/                      ← 影响分析、升级标记 lint(Python)
│   ├── baseline-freeze/                  ← 步骤 8
│   │   ├── SKILL.md
│   │   └── templates/                    ← accepted/deferred/release-risk 模板
│   └── _shared/                          ← 共享组件(两步依赖)
│       ├── baseline-rw/                  ← 4+1 基线读写库(Python)
│       ├── renderer/                     ← expanded-flow 风格 HTML 渲染器(Python+Jinja2)
│       └── templates/4plus1/             ← 4+1 五视图标准数据模板
├── hooks/                                ← 可选:升级标记 lint(产物校验 hook)
├── CLAUDE.md                             ← 插件级说明
└── README.md
```

### plugin.json(最小字段 + skills 显式声明)

```json
{
  "name": "a2d",
  "description": "A2D (Architecture to Delivery) 工具链 — 步骤 3 架构更新稿生成与渲染、步骤 8 基线固化,以 4+1 为枢纽的两闸门模型",
  "version": "0.1.0",
  "author": { "name": "<团队名>" },
  "license": "<license>",
  "keywords": ["a2d", "architecture", "4+1", "baseline", "governance"],
  "skills": ["./skills/arch-update-draft", "./skills/baseline-freeze"]
}
```

### marketplace.json(单 plugin 最简形态)

```json
{
  "name": "a2d-toolkit",
  "owner": { "name": "<团队名>" },
  "plugins": [
    {
      "name": "a2d",
      "source": "./",
      "description": "A2D 步骤 3/8 工具链",
      "version": "0.1.0",
      "category": "workflow"
    }
  ]
}
```

### 本地测试

```bash
# 在仓库根
/plugin marketplace add ./tools/a2d-plugin
/plugin install a2d@a2d-toolkit
```

调用:`/a2d:arch-update-draft`、`/a2d:baseline-freeze`。命名空间前缀(`a2d:`)只在调用时拼,**不写进 SKILL.md frontmatter**。

---

## 4. 步骤 3 skill:`/a2d:arch-update-draft`

**定位**:需求 + 现状 + 4+1 基线 → 本轮架构 delta(待合并更新稿),渲染成审阅 HTML,供闸门 1 人类审核。这是 D2 选定的「完整 skill 含影响分析」,端到端。

### 4.1 输入 / 输出

| 维度 | 内容 |
|---|---|
| **输入** | 版本需求文档(step 1 产物)、代码现状报告(step 2 产物)、当前 4+1 基线(经 `_shared/baseline-rw` 读)、历史信封(`architecture-envelopes/`) |
| **输出** | `arch-update-draft-<version>.html`(delta 视图:左基线 / 右本轮)+ 结构化 delta 源文件(yaml/md) |
| **输出字段**(HTML 每条都要有) | `delta`(本轮 4+1 变更点)/ `理由`(每个 delta 为何改)/ `冲击不变量`(碰了哪些模块边界、状态 owner、公开契约)/ `gate 变更`(需新增/调整的 ArchUnit/schema check/drift check)/ `升级标记`(是否触发越界/改 owner/假设崩 → 回闸门 1) |

### 4.2 内部子能力

1. **影响分析**:扫受影响模块 / 状态 / 契约 / 依赖 / 风险 / 未知点(对应旧 AI-2 的职责)
2. **4+1 标准模板收敛**:五视图各自只填 delta(模板来自 `_shared/templates/4plus1/`)
3. **渲染**:调 `_shared/renderer` 出 expanded-flow 风格 HTML(delta 模式)
4. **升级标记 lint**:产物里若声明了 forbidden 路径变更却没打升级标记,报错(空检查防护:每个 gate 变更必须指向具体 forbidden 项)

### 4.3 SKILL.md 步骤骨架

```
1. 读输入:需求文档 / 现状报告 / 基线(调 baseline-rw)
2. 影响分析:产出受影响清单(模块/状态/契约/依赖/风险/未知)
3. 生成 delta 草稿:按 4+1 五视图标准模板,每视图只写本轮变更
4. 标升级口:delta 里凡是碰 forbidden / owner / 假设的,打 escalation 标记
5. 渲染:调 renderer 生成 arch-update-draft-<version>.html(delta 视图)
6. 自检:升级标记 lint(每个 gate 变更须指向具体 forbidden 项,防空检查绿灯)
```

### 4.4 与步骤 7 的视图区分

- 步骤 3 HTML = **delta 视图**(基线 vs 本轮更新稿)
- 步骤 7 HTML = **diff 视图**(基线 vs 代码反推,graphify/OpenAPI 对账)

两者共用 renderer,视图模式参数不同(delta / diff)。步骤 7 工具不在本计划范围,但 renderer 要为其预留模式。

---

## 5. 步骤 8 skill:`/a2d:baseline-freeze`

**定位**:按实际落地把 4+1 基线更新成 accepted/deferred 草案。现状 ◐(「确定性 4+1 模板下 prompt 即可」),所以比 step 3 轻——核心是**合并 + 状态批量更新 + deferred 追踪**,不是重推理。

### 5.1 输入 / 输出

| 维度 | 内容 |
|---|---|
| **输入** | 实现证据包(step 6)、对账报告(step 7)、更新稿(step 3 已审核)、当前 4+1 基线 |
| **输出** | 更新后的 4+1 基线(accepted/deferred 草案)+ `baseline-<version>.html`(全貌视图)+ 文档状态批量更新 |
| **输出字段** | `accepted facts`(本轮固化的 4+1 事实)/ `deferred 项`(必须有 owner+触发条件+后续入口)/ `release risk`(发布风险决策草案) |

### 5.2 内部子能力

1. **基线合并**:把 step 3 的 delta + step 6/7 的实证合并进基线(调 `_shared/baseline-rw` 写)
2. **deferred 追踪**:结转开放问题,挂 owner(对应 [phase-archive.md](a2d-phases/phase-archive.md) 的结转逻辑)
3. **文档状态批量更新**:改各 A2D 文档 frontmatter(draft → accepted/superseded),建替代关系
4. **渲染**:调 renderer 生成更新后基线全貌 HTML(full 模式)

### 5.3 复用来源

直接复用 [a2d-artifact-templates.md](a2d-artifact-templates.md) §6 基线说明模板 + [phase-archive.md](a2d-phases/phase-archive.md) 的归档逻辑。skill 本质是把这两份人工流程固化成可执行步骤。

---

## 6. 共享组件(两步都依赖,必须先建)

| 组件 | 职责 | 谁用 | 技术栈 |
|---|---|---|---|
| **`_shared/baseline-rw`** | 4+1 基线结构化读写(yaml/md 解析 + 五视图存取)。两步的**事实中枢** | step 3 读、step 8 读写、未来 step 7 可读 | Python |
| **`_shared/renderer`** | 把结构化 4+1 数据渲染成 [a2d-expanded-flow.html](a2d-expanded-flow.html) 同款静态审阅 HTML。复用那份 CSS(暖纸底 `#f7f4ed`、人=琥珀/AI=蓝/机器=绿/闸门=红、卡片网格)。**确定性渲染,无 AI** | step 3(delta)、step 8(full)、未来 step 7(diff) | Python + Jinja2 |
| **`_shared/templates/4plus1`** | 4+1 五视图(逻辑/开发/进程/物理/场景)的**标准数据模板**。直接补齐 step 3「✗ 缺 4+1 标准模板」 | step 3 填 delta、step 8 填全量、step 9 验收基准 | yaml/md |

### 6.1 renderer 的「图部分怎么办」(关键设计决策,见 §9 待澄清)

现状:4+1 **图源存在**(8 个 `.puml`),但 puml→SVG 渲染脚本缺失,无导出产物。renderer 要在审阅 HTML 里呈现 4+1 视图时,有两条路:

| 路径 | 做法 | 范围 | 建议 |
|---|---|---|---|
| **路 A**(推荐 MVP) | renderer 只渲染「文档层」(delta 表格/字段/卡片/升级标记);4+1 图部分**链接到 `.puml` 源文件**,审阅者点开看源 | 轻;step 3/8 不依赖 puml→SVG 管线 | ✓ S1 MVP |
| **路 B**(完整) | renderer 同时建 puml→SVG 脚本并嵌入 HTML | 重;审阅体验完整但范围扩大 | ✗ 暂缓 |

**建议**:S1 走路 A;「puml→SVG 渲染管线」(即 [architecture-views/README.md](../architecture-views/README.md) 规划但未落地的 `render-architecture-views.sh`)作为**独立议题**,不裹进步骤 3/8 的 MVP。该议题可归到 architecture-views 工具或 step 2 现状扫描。

> **关键洞察**:[a2d-expanded-flow.html](a2d-expanded-flow.html) 本身就是从 [a2d-stage-inputs-and-tools.md](a2d-stage-inputs-and-tools.md) 手工重构的「md→可视化渲染」产物——**它就是 renderer 应自动生成内容的人工样板**。renderer 目标是复刻其视觉与结构,只是输入从「手工编辑」变成「结构化 4+1 数据」。

---

## 7. v2 9 步编号迁移(D4)

这是独立于工具构建的文档工程,但必须在工具产出前完成——否则 skill 渲染出的 HTML 引用的还是旧编号。

### 7.1 v2 步骤 ↔ 现有编号映射

| v2 步骤 | 现有编号 | 角色 | 闸门 |
|---|---|---|---|
| 1 需求澄清 | H0 + AI-1 | 人机密集 | — |
| 2 现状扫描 | (散在 AI-2/AI-3 扫描部分) | 自动 | — |
| 3 架构更新稿 | AI-2 + AI-3 + AI-4/AI-5 | AI 草案 | **闸门 1**(末尾人类审核) |
| 4 gate 派生 | AI-3 信封 `legal_layer_policy` + AI-4 | 自动 | — |
| 5 切片/计划 | AI-6(交付视图) | AI 草案 | — |
| 6 AI 实现 | AI-7 | AI 自主 | — |
| 7 后置对账 | AI-8(验证与漂移) | 自动 | **闸门 2**(人类裁决) |
| 8 基线固化 | AI-9(归档) | 人类确认 | — |
| 9 发布验收 | H3 + AI-9 发布部分 | 人类裁决 | — |

### 7.2 结构性变化(迁移要点)

现有是 **H0/H1/H2/H3 三检查点**,v2 是**两闸门**(闸门 1 = step 3、闸门 2 = step 7)。v2 把旧 **H2(审核 4+1)的职责并入闸门 1**——因为 v2 里 4+1 审核就是审「本轮更新稿」,不再是独立门。迁移时:

- 旧 H1(确认开发边界)+ H2(审核 4+1) → **v2 闸门 1**
- 旧 H3(审核发布风险) → 拆入 **v2 闸门 2**(step 7)与 **step 9**(发布决策)

### 7.3 信息对象链处理

9 个信息对象(原始意图→版本需求文档→开发边界说明→4+1 架构包→架构展开/切片化→交付计划→实现证据包→后置投影检查包→基线决策记录)是**产物**,不是阶段编号。**保留对象名**,只给每个标「产出于 step N」。这样产物链不断,阶段编号统一。

### 7.4 受影响文件清单(按改动量排序)

| 文件 | 改什么 | 量 |
|---|---|---|
| [a2d-stage-inputs-and-tools.md](a2d-stage-inputs-and-tools.md) | 阶段编号全面重排 H0/AI-x → step 1-9;信息对象链保留但标 step | **大** |
| [a2d-phases/phase-*.md](a2d-phases/)(9 个文件) | frontmatter `phase: AI-x` → `step: N`;文件名是否随 step 重命名待定(见 §9);交叉引用 | 中 |
| [a2d-working-model.md](a2d-working-model.md) | H0/AI-1..9 流程图 → step 1-9;H1/H2/H3 → 闸门 1/2;信息对象链标 step | 中 |
| [a2d-artifact-templates.md](a2d-artifact-templates.md) | 流程产物总览表的 H0/AI-x 列 → step;模板字段不变 | 小 |
| [a2d-human-checkpoints.md](a2d-human-checkpoints.md) | H0-H3 检查点流程 → 闸门 1/2 | 小 |
| [a2d-roles.md](a2d-roles.md) / [a2d-views.md](a2d-views.md) | 阶段引用(grep H0/AI-/H1/H2/H3) | 小 |
| [a2d-expanded-flow.html](a2d-expanded-flow.html) | 已是 v2;补 step 锚点;页脚「改自…重构」更新为「v2 已为唯一编号」 | 极小 |

---

## 8. 落地切片顺序

按依赖关系分 4 个切片:

| 切片 | 内容 | 依赖 |
|---|---|---|
| **S0 v2 迁移** | D4 — 重排编号。纯文档,无代码 | 无,但应最先做(否则工具产物的编号是错的) |
| **S1 共享组件** | `_shared/baseline-rw` + `_shared/renderer`(路 A) + `4plus1` 模板 | S0(模板要引用新 step 编号) |
| **S2 步骤 3 skill** | `/a2d:arch-update-draft` 端到端(含影响分析、升级标记 lint) | S1 |
| **S3 步骤 8 skill** | `/a2d:baseline-freeze`(合并、deferred、状态更新、渲染) | S1 |

**并行**:S0 与 S1 可并行(S0 改文档、S1 写组件,互不阻塞)。S2/S3 都依赖 S1。

---

## 9. 后续待澄清(启动各切片前需定)

| 编号 | 待定项 | 影响 | 建议 |
|---|---|---|---|
| Q1 | renderer 图策略:路 A(链接 puml)vs 路 B(嵌入 SVG) | S1 范围 | 路 A 作 MVP |
| Q2 | 4+1 五视图标准数据模板:每视图具体填哪些字段 | S1 | 从现有 `.puml` 图源 + [a2d-artifact-templates.md](a2d-artifact-templates.md) §3 评审包模板抽取 |
| Q3 | [a2d-phases/phase-*.md](a2d-phases/) 文件名是否随 step 重命名(如 `phase-intake.md` → `phase-step1-intake.md`) | S0 | 倾向重命名,frontmatter 加 `step: N` 锚点 |
| Q4 | README 路径 bug(`docs/architecture-views/` → `docs/architecture/l0/architecture-views/`)是否纳入 S0 顺带修 | S0 | 纳入,代价小 |
| Q5 | puml→SVG 渲染管线([architecture-views/README.md](../architecture-views/README.md) 规划未落地)归谁、何时做 | 独立议题,不阻塞 S1(路 A) | 单列,不裹进步骤 3/8 MVP |

---

## 维护规则

- 本文件是构建参照,不是 A2D 流程产物;不进 9 步编号链。
- 决定(D1-D9)变更时更新 §1;待澄清项(Q1-Q5)定案后从 §9 移入对应章节或删除。
- 各切片(S0-S3)启动时,可基于本计划展开为逐文件 diff 级执行清单。

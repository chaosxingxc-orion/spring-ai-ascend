# Vibe Coding 项目复盘 PPT 制图提示词

> 用途：逐页调用 MCP 制图工具生成 16:9 PPT 图片。  
> 口径：时间轴采用 2026-05-26 到 2026-06-14 的参与窗口；统计数据采用 `upstream/main` merged PR 的全项目口径。  
> 风格：工程复盘，不做宣传；强调事实细节、PR 数据、代码片段、问题与转折。

## 全局视觉约束

- 画幅：16:9 PPT slide。
- 语言：中文为主，PR 编号、代码、路径保留英文。所有中文必须是准确、可读的简体中文；不得出现乱码、异体字误替换、伪中文或缺字。
- 视觉风格：参考 chatgpt.com 里生成的架构图/系统图效果：干净、理性、模块化、轻量科技感，但不要营销海报感。
- 图形语言：圆角矩形模块、浅色卡片、细连接线、轻阴影、清晰层级、少量强调色；像“架构说明图 + 工程数据图”的结合。所有线条、箭头、时间轴、模块边框、图表连线必须规整清晰，不能扭曲、抖动、断裂、错位、漂浮或呈现手绘变形感。
- 背景：暖白或浅灰，允许非常轻的网格/分区线；不使用炫彩渐变、装饰光球、人物、3D 物体、拟物插画。
- 字体：清晰、紧凑、适合数据和代码阅读；标题醒目，正文克制，代码使用等宽风格。
- 颜色：黑/灰为主；蓝色表示新增/功能；红色表示删除/风险；绿色表示验证/闭环；黄色表示观察/提醒。颜色要柔和，不要高饱和。
- 每页必须保留事实细节：PR 编号、增删行、文件数、代码片段或路径。
- 默认生成参数：优先使用 `gpt-image-2-pro` + `2048x1152`；若 MCP 主路径 fallback 到约 `1672x941`，先接受该结果用于逐页审核。

---

## Slide 01 - 不平滑的时间线：先看全项目节奏

### 页面文案

标题：不平滑的时间线：先看全项目节奏  
副标题：从反复试错，到边界收敛，再到闭环提速

上方时间轴定义四个阶段：

1. 05-26 ~ 05-28：设计输入过载  
   大量设计文案和评审意见进入，AI 快速产出，人还没有把握住方向，容易机械顺从 AI 堆积产物。

2. 05-29 ~ 06-01：分模块并行试错  
   确定 5 个模块、3 名开发者并行推进；同时删除大量历史代码和设计约束。产出很多，但反馈仍慢。

3. 06-02 ~ 06-09：先立脊柱，再做减法  
   06-02 添加第一个 ping -> pong E2E，成为人可以守护的最小闭环；随后通过大幅减法让 runtime 目录和边界逐渐清晰。

4. 06-10 ~ 06-14：快速闭环  
   功能、review 修正、E2E、文档、发布开始成组推进，v0.1.0 发布前形成可验证链路。

阶段备注：

- 05-26 ~ 05-28：多名开发者贡献大量设计/治理/评审文案，约 25 个 PR。
- 05-29 ~ 06-01：出现大规模删除和重分区，约 +28k / -56k 行。
- 06-02：#119 添加 A2A LLM E2E sample，ping -> pong 守护点出现。
- 06-08 ~ 06-09：#145 / #155 做 runtime 减法，#155 明确出现 `121 -> 37 files (-69%)`。
- 06-10 ~ 06-14：约 56 个 PR，功能/重构/修复/文档集中闭环；当前 agent-runtime/examples 相关测试约 62 个；#264 发布 agent-runtime v0.1.0。

### MCP 制图提示词

```text
Use case: productivity-visual.
Asset type: 16:9 PPT slide in Chinese.
All Chinese text must be accurate, readable Simplified Chinese. Do not use mojibake, pseudo-Chinese, corrupted glyphs, missing characters, or traditional/variant character substitutions.
Create a four-stage narrative engineering timeline slide, not a dense data dashboard and not a marketing poster.

Title text exactly:
“不平滑的时间线：先看全项目节奏”

Subtitle:
“从反复试错，到边界收敛，再到闭环提速”

Layout:
Top 35% of the slide:
Draw one clean horizontal timeline divided into exactly four time ranges, using rounded stage cards connected by a thin line. Each stage card should have a date range, a short title, and one concise evidence badge.

Stage 1:
“05-26 ~ 05-28”
“设计输入过载”
Evidence badge: “大量设计/治理/评审文案 · 约 25 PR”

Stage 2:
“05-29 ~ 06-01”
“分模块并行试错”
Evidence badge: “5 个模块 · 3 名开发者 · 约 +28k / -56k 行”

Stage 3:
“06-02 ~ 06-09”
“先立脊柱，再做减法”
Evidence badge: “#119 ping -> pong · #155 121 -> 37 files (-69%)”

Stage 4:
“06-10 ~ 06-14”
“快速闭环”
Evidence badge: “约 56 PR · 62 tests · v0.1.0”

Bottom 55% of the slide:
Create four narrative panels corresponding exactly to the four timeline stages. Use small architecture-diagram style sketches, not decorative illustrations.

Panel 1 title:
“第一阶段：AI 快速产出，但方向失焦”
Panel 1 text:
“大量设计输入进入，AI 迅速生成文案和结构；人还没有把握住方向，容易机械顺从 AI 堆积产物。”

Panel 2 title:
“第二阶段：5 模块、3 人并行，产出很多但反馈慢”
Panel 2 text:
“分模块开发启动，同时删除大量历史代码和设计约束；但缺少稳定脊柱，反复仍然明显。”

Panel 3 title:
“第三阶段：ping -> pong 成为脊柱，随后做减法”
Panel 3 text:
“#119 把 agent card、A2A streaming、runtime handler、LLM response 串起来；#145/#155 之后 runtime 目录和边界逐渐清晰。”

Panel 4 title:
“第四阶段：功能、review、E2E、文档成组闭环”
Panel 4 text:
“边界清楚后，功能点、测试用例、文档刷新、v0.1.0 发布进入快速迭代。”

Important:
Do not fill the page with daily data labels. Do not create a dense chart. The main purpose is to explain what went wrong early, what changed, and why the later rhythm accelerated. Use data only as small factual badges for each stage.

Style reference:
Use the visual language of architecture diagrams generated on chatgpt.com: clean modular rounded rectangles, light shadows, thin connector lines, subtle grid, restrained engineering palette, high readability. All lines, arrows, timelines, card borders, chart paths, and connector strokes must be geometrically clean and stable: no warped lines, no wobbly curves, no broken arrows, no floating misaligned connectors, no hand-drawn distortion. Avoid marketing-poster aesthetics, decorative blobs, people, 3D objects, or glossy illustrations.

Style:
Warm off-white background, black/gray text, muted blue for progress, muted red for friction, muted green for validation, very clean layout, strong hierarchy, readable Chinese text.
```


## Slide 02 - 前期困难一：AI 容易通过加法掩盖问题

### 页面文案

标题：前期困难一：AI 容易通过加法掩盖问题  
副标题：AI 会继续补洞，但不一定回头拆错墙

核心判断：

- AI 遇到失败时，常见动作是“再加一层”：加测试 client、加兼容逻辑、加条件分支。
- 但它不一定会回看：是不是前一个设计规则已经错了。
- 人类要做的是识别“该补洞”还是“该重定规则”。

案例 1：A2A 连通性失败 -> 手写 HTTP client

- 现象：A2A 连通性测试遇到问题。
- AI 加法：没有优先使用标准 A2A 协议库，而是在 example 里自己写了一套 HTTP 调用实现。
- 问题：这不是在修复 A2A 调用问题，而是在测试侧“绕过去”。
- 人类收敛：删除手写 client，回到标准 A2A client / 项目 client facade。

案例 2：SSE 中断不断流 -> 先增强模拟 client，而不是修 runtime

- 用户期望：agent 中断等待用户输入时，当前 SSE 流要结束，前端/用户才能发起下一次输入。
- AI 加法：没有先改 runtime 服务行为，而是增强模拟 A2A client，让它在流没有断开的情况下继续接收输入。
- 冲突：测试工具更强了，但业务诉求没有被修复。
- 人类收敛：修 runtime，让中断态/终态都结束本次流式响应。

案例 3：message 与 metadata 双规则并存 -> 条件分支越补越多

- 现象：早期 remote A2A 固定把用户输入塞进自定义 `message` 字段；这个方案本身不具备通用性。
- AI 加法：当 Versatile 邻居需要 `metadata` 透传请求头和结构化参数时，又补了一套 metadata 逻辑。
- 问题：两套传递规则并存，第二次中断请求透传到 Versatile 邻居时走错路径。
- AI 倾向：继续加条件判断，既兼容 `message` 又兼容 `metadata`。
- 人类决策：承认早期自定义 `message` 硬编码方案不可取，统一规则：用户输入走 A2A 协议 `text`，请求头/查询/结构化字段走 `metadata`。

### MCP 制图提示词

```text
Use case: productivity-visual.
Asset type: high-quality 16:9 PPT slide in Chinese.
All Chinese text must be accurate, readable Simplified Chinese. Do not use mojibake, pseudo-Chinese, corrupted glyphs, missing characters, or traditional/variant character substitutions.
Create a concrete engineering retrospective slide with the same visual style as Slide 01. Inherit all global constraints: clean modular architecture-diagram style, rounded cards, light shadows, thin stable connector lines, no warped lines, no wobbly curves, no broken arrows, no floating misaligned connectors, no hand-drawn distortion, no marketing-poster aesthetics.

Title exactly:
“前期困难一：AI 容易通过加法掩盖问题”

Subtitle:
“AI 会继续补洞，但不一定回头拆错墙”

Layout:
One top insight band + three narrative case cards + one conclusion card. Keep the slide readable. Do not create evidence badges, PR-number chips, or a dense code wall.

Top insight band text:
“AI 遇到失败时，常见动作是再加一层：加测试 client、加兼容逻辑、加条件分支。人要判断：该补洞，还是该重定规则。”

Case card 1 title:
“案例 1：A2A 连通性失败 -> 手写 HTTP client”
Case card 1 key points:
"现象：A2A 连通性测试遇到问题"
"AI 加法：自己写一套 HTTP 调用实现"
"问题：不是修复 A2A 调用，而是在测试侧绕过去"
"收敛：删除手写 client，回到标准 A2A client / 项目 client facade"
Case card 2 title:
“案例 2：SSE 中断不断流 -> 先增强模拟 client”
Case card 2 key points:
"用户期望：中断等待输入时，本次 SSE 流结束"
"AI 加法：增强模拟 client，让它在不断流时继续输入"
"冲突：测试工具更强了，但 runtime 行为没有修"
"收敛：runtime 在中断态/终态结束本次流式响应"
Case card 3 title:
“案例 3：message + metadata 双规则 -> 条件分支越补越多”
Case card 3 key points:
"早期：自定义 message 字段硬编码用户输入，本身不通用"
"AI 加法：Versatile 场景再补 metadata 逻辑"
"问题：两套规则并存，第二次中断请求走错路径"
"收敛：用户输入走 A2A text；请求头/查询/结构化字段走 metadata"
Conclusion card text:
“结论：AI 擅长补洞；开发者必须识别规则是否已经错了。”

Style reference:
Use the visual language of architecture diagrams generated on chatgpt.com: clean modular rounded rectangles, light shadows, thin connector lines, subtle grid, restrained engineering palette, high readability. All lines, arrows, timelines, card borders, chart paths, and connector strokes must be geometrically clean and stable: no warped lines, no wobbly curves, no broken arrows, no floating misaligned connectors, no hand-drawn distortion. Avoid marketing-poster aesthetics, decorative blobs, people, 3D objects, or glossy illustrations.

Style:
Same visual language as Slide 01: warm off-white background, black/gray text, muted red for risk, muted blue for evidence, muted green for corrected direction, elegant rounded modules, subtle grid, light shadows, strong hierarchy, readable Chinese text. Use concise narrative bullets, not evidence badges, PR-number chips, or detailed code snippets.
```

---

## Slide 03 - 前期困难二：复杂项目不能一开始就丰满

### 页面文案

标题：前期困难二：复杂项目不能一开始就丰满  
副标题：设计文档越多，不等于 AI 越能把复杂系统做对

核心观点：

- 前期容易高估 AI：以为只要设计文档足够多、描述足够清楚，复杂功能就能靠 AI 自我迭代解决。
- 这个误区还会继续升级：如果当前 AI 解决不了，就换更强的 AI。
- 但真正的问题不是模型不够强，而是目标太宏大、锚点太多、缺少可验证的脊柱。

错误假设链路：

- 更多设计输入 -> 更完整的理解
- 更强模型 -> 自动处理复杂度
- 更多约束 -> 更安全的代码
- 更多代码 -> 更接近可用系统

图例要表达的实际结果：

- 用户给了很多锚点来描述宏大目标：方案提案、架构设计、设计约束、用户场景、代码约束。
- 这些锚点的本意，是帮助 AI 对齐目标。
- 但缺少可验证脊柱时，AI 可能只是机械拼接锚点，生成一个偏离目标的奇怪产物：代码很多，却不知道怎么用、没人能完整理解，也说不清为什么不能用。

要转向的方式：

- 先做骨感脊柱，而不是完整蓝图。
- 先让一个关键路径可运行、可测试、可走读。
- 再在脊柱上逐步丰满能力。

### MCP 制图提示词

```text
Use case: productivity-visual.
Asset type: high-quality 16:9 PPT slide in Chinese.
All Chinese text must be accurate, readable Simplified Chinese. Do not use mojibake, pseudo-Chinese, corrupted glyphs, missing characters, or traditional/variant character substitutions.
Create a conceptual engineering retrospective slide with the same visual style as Slide 01. Inherit all global constraints: clean modular architecture-diagram style, rounded cards, light shadows, thin stable connector lines, no warped lines, no wobbly curves, no broken arrows, no floating misaligned connectors, no hand-drawn distortion, no marketing-poster aesthetics.

Title exactly:
“前期困难二：复杂项目不能一开始就丰满”

Subtitle:
“设计文档越多，不等于 AI 越能把复杂系统做对”

Layout:
Create one central illustrative diagram and three supporting panels. The central diagram is the main visual explanation of the slide.

Top center title inside the diagram:
“很多锚点，不等于自动对齐目标”

Central illustrative diagram:
Show a large intended target labeled “宏大目标”.
Around it place many anchor cards:
“方案提案”, “架构设计”, “设计约束”, “用户场景”, “代码约束”.
Use clean arrows from these anchor cards into an “AI 生成” box.
The output should be a red, visibly misaligned architecture/module artifact, labeled:
“奇怪产物：代码很多，但不知道怎么用”.
The artifact should look like a schematic pile of mismatched modules, not a useful architecture, but keep it clean and non-cartoon.
Then show a small corrected green path beside it:
“先做骨感脊柱”.

Left panel title:
“前期误区”
Text:
“以为设计文档越多、描述越清楚，AI 就越能靠自我迭代解决复杂功能；如果不行，就换更强的 AI。”

Middle panel title:
“实际后果”
Text:
“锚点本意是对齐宏大目标；但缺少可验证脊柱时，AI 可能机械拼接锚点，生成偏离目标的奇怪产物。”

Right panel title:
“需要转向”
Text:
“先做骨感脊柱：关键路径能跑、能测、能走读；再逐步丰满能力。”

Visual metaphor:
Use a lightweight architecture-diagram metaphor: the user places many clear anchor points around a large intended target, labeled “宏大目标”. The anchor points are “方案提案”, “架构设计”, “设计约束”, “用户场景”, “代码约束”. Show arrows from these anchors into an AI generation box. The output should be a weird misaligned system artifact, labeled “奇怪产物：代码很多，但不知道怎么用”, not a useful architecture. Then show a small corrected green path labeled “先做骨感脊柱”. Keep it schematic and readable, not decorative.

Style reference:
Use the visual language of architecture diagrams generated on chatgpt.com: clean modular rounded rectangles, light shadows, thin connector lines, subtle grid, restrained engineering palette, high readability. All lines, arrows, timelines, card borders, chart paths, and connector strokes must be geometrically clean and stable: no warped lines, no wobbly curves, no broken arrows, no floating misaligned connectors, no hand-drawn distortion. Avoid marketing-poster aesthetics, decorative blobs, people, 3D objects, or glossy illustrations.

Style:
Same visual language as Slide 01: warm off-white background, black/gray text, muted red for wrong assumptions/outcome, muted blue for inputs, muted green for the corrected path, elegant rounded modules, subtle grid, light shadows, strong hierarchy, readable Chinese text. Do not use a literal cartoon tree. Do not use people or decorative illustrations.
```

---

## Slide 04 - 案例一：先设计边界，再让 AI 开发

### 页面文案

标题：案例一：先设计边界，再让 AI 开发  
副标题：人要握有决策权，review 用来守住承重墙

核心判断：

- Vibe coding 不是先让 AI 写一大段代码，再由人被动验收。
- 正确顺序是：人先设计边界和承重墙，再让 AI 在边界内小步开发。
- 人必须能走读代码，持续握有判断能力；否则 AI 写得越快，项目越容易失序。
- Review 的作用，是帮助人确认 AI 有没有越界、有没有拆墙，然后才看细节实现和测试覆盖。

案例背景：

- 我们需要支持 runtime 调用远端 A2A agent：发现远端 agent、读取 agent card、发起协议调用、处理流式响应和中断。
- AI 为了把功能补起来，曾把这类能力放进 `engine/service` 这样的泛化服务模块。
- 冲突点在于：这不是普通业务服务，而是 A2A 协议外联能力；放到泛化 service 模块，会让协议细节穿透到不该承担的层。
- 一旦这个口子打开，后续新的协议能力、邻居调用能力、兼容逻辑都容易继续往 service 里堆，模块边界会越来越模糊。
- 人类决策是：远端 A2A client 能力要回到 A2A 协议边界内，保持 runtime 的承重墙不被拆掉。

Vibe coding 技巧：

1. 先设计：明确哪些模块是承重墙，哪些能力只能在边界内生长。
2. 再开发：让 AI 小步补实现，不允许为了“跑通”跨边界堆代码。
3. 持续走读：人要能读懂 AI 生成的代码，才能保留决策权。
4. 用 review 校正：review 不是只挑语法和细节，而是把越界修改拉回正确位置。

一句话：

- AI 可以加速实现，但不能替人决定架构边界。

### MCP 制图提示词

```text
Use case: productivity-visual.
Asset type: high-quality 16:9 PPT slide in Chinese.
All Chinese text must be accurate, readable Simplified Chinese. Do not use mojibake, pseudo-Chinese, corrupted glyphs, missing characters, or traditional/variant character substitutions.
Create a concrete engineering retrospective case slide about vibe coding practice, with the same visual style as Slide 01. Inherit all global constraints: clean modular architecture-diagram style, rounded cards, light shadows, thin stable connector lines, no warped lines, no wobbly curves, no broken arrows, no floating misaligned connectors, no hand-drawn distortion, no marketing-poster aesthetics.

Title exactly:
“案例一：先设计边界，再让 AI 开发”

Subtitle:
“人要握有决策权，review 用来守住承重墙”

Layout:
Top method band + middle conflict diagram + bottom practice checklist.

Top method band:
Show a clean five-step flow:
“先设计边界” -> “AI 小步开发” -> “人持续走读” -> “review 守承重墙” -> “边界内继续迭代”
Under the flow, add this sentence:
“Vibe coding 不是把决策权交给 AI；人必须先定义边界，再让 AI 在边界内加速。”

Middle diagram:
Create a clean side-by-side conflict diagram. Do not show PR numbers, commit IDs, or long function/class names.

Left side title:
“冲突点：功能跑通了，但落点错了”
Left side subtitle:
“远端 A2A client 能力被放进泛化 service 模块”
Show three nested module boxes:
“agent-runtime”
inside it “engine”
inside it a red warning module “service”
Inside the red module place:
“远端 agent 调用”
“agent card 读取”
“流式响应 / 中断处理”
Add a small warning label:
“看起来是在补能力，实际让协议细节穿透边界”

Right side title:
“人类决策：能力回到协议边界”
Right side subtitle:
“A2A 外联能力属于 A2A client 边界”
Show three nested module boxes:
“agent-runtime”
inside it “engine/a2a”
inside it a green module “client”
Inside the green module place:
“发现远端 agent”
“发起 A2A 调用”
“处理 streaming / interrupt”
Add a small validation label:
“协议能力留在所属边界，承重墙不被拆掉”

Between left and right:
Use one clean arrow labeled:
“review 校正：不是继续补 service，而是重定能力归属”

Bottom checklist title:
“Vibe coding 技巧”
Checklist items:
“1. 先设计：明确模块边界和承重墙”
“2. 再开发：让 AI 在边界内小步补实现”
“3. 要走读：人必须读得懂，才能保留判断力”
“4. 用 review 校正：把越界修改拉回正确位置”

Bottom conclusion sentence:
“AI 可以加速实现，但不能替人决定架构边界。”

Style reference:
Use the visual language of architecture diagrams generated on chatgpt.com: clean modular rounded rectangles, light shadows, thin connector lines, subtle grid, restrained engineering palette, high readability. All lines, arrows, timelines, card borders, chart paths, and connector strokes must be geometrically clean and stable: no warped lines, no wobbly curves, no broken arrows, no floating misaligned connectors, no hand-drawn distortion. Avoid marketing-poster aesthetics, decorative blobs, people, 3D objects, or glossy illustrations.

Style:
Same visual language as Slide 01: warm off-white background, black/gray text, muted red for wrong boundary placement, muted blue for the vibe coding workflow, muted green for corrected module boundary, elegant rounded modules, subtle grid, light shadows, strong hierarchy, readable Chinese text. Keep the slide clean and case-based; do not create a dense code wall, evidence badges, PR chips, or long code snippets.
```

---

## Slide 05 - 案例二：Versatile Adapter 从骨感到丰满

### 页面文案

标题：案例二：Versatile Adapter 从骨感到丰满  
副标题：复杂适配不是独立定制，而是收敛到平台通用能力

场景：

- Versatile Adapter 是一个新的 adapter 适配：把远端 REST Agent 包装成 A2A 可路由的 agent。
- 它的使用场景不是独立调用，而是作为子 agent 被主 agent 通过 A2A 多邻居调用能力发现、注册为 tool，并发起调用。

冲突点：

- Versatile 的参数传递、请求头透传、URL 变量、业务规则、结果提取都更定制化。
- 如果为了 Versatile 单独改 A2A 邻居调用主链路，就会干扰通用邻居调用能力。
- 这类差异不能扩散到 A2A 通用链路里，否则每接一个特殊 agent，主链路都会变重。

实践路径：

1. 先独立实现 Versatile Adapter，跑通 REST 代理、参数映射、SSE 结果适配。
2. 再继承其它同事实现的 A2A 通用邻居调用能力，把 Versatile 作为子 agent 接入。
3. 调试 parent-child A2A 场景，验证主 agent 能通过通用邻居能力调用 Versatile。
4. 对 Versatile 的特殊参数差异，不定制 A2A 主链路，而是用 metadata、配置、Agent Card skills / SKILL.md 等通用扩展点说明参数如何封装。
5. 最终在一个开发平台能力里交付，而不是为 Versatile 独立定制一套调用链路。

一句话：

- 骨感是先有 adapter 和 A2A 邻居调用脊柱；丰满是把 Versatile 的特殊性放进通用扩展点，而不是污染主链路。

### MCP 制图提示词

```text
Use case: productivity-visual.
Asset type: high-quality 16:9 PPT slide in Chinese.
All Chinese text must be accurate, readable Simplified Chinese. Do not use mojibake, pseudo-Chinese, corrupted glyphs, missing characters, or traditional/variant character substitutions.
Create a concrete engineering retrospective case slide about vibe coding practice, with the same visual style as Slide 01 and Slide 04. Inherit all global constraints: clean modular architecture-diagram style, rounded cards, light shadows, thin stable connector lines, no warped lines, no wobbly curves, no broken arrows, no floating misaligned connectors, no hand-drawn distortion, no marketing-poster aesthetics.

Title exactly:
“案例二：Versatile Adapter 从骨感到丰满”

Subtitle:
“复杂适配不是独立定制，而是收敛到平台通用能力”

Layout:
Top scenario band + middle integration diagram + bottom practice path. Do not make this a PR timeline. Do not show PR numbers, code snippets, or long class names.

Top scenario band:
Text:
“Versatile Adapter 把远端 REST Agent 包装成 A2A 可路由的 agent；真实场景不是独立调用，而是作为子 agent 被主 agent 通过 A2A 多邻居能力发现、注册为 tool，并发起调用。”

Middle integration diagram:
Create a left-to-right architecture diagram with three zones.

Left zone title:
“特殊性：Versatile Adapter”
Inside left zone show four compact chips:
“REST API”
“URL 变量”
“请求头透传”
“结果提取”
Add small note:
“参数和业务规则更定制化”

Center zone title:
“承重墙：A2A 通用邻居调用”
Inside center zone show a clean backbone line:
“主 agent” -> “发现邻居” -> “注册为 tool” -> “A2A 调用子 agent”
Add a red warning callout above the backbone:
“风险：为 Versatile 单独改主链路，会干扰通用邻居能力”

Right zone title:
“收敛：平台能力交付”
Inside right zone show three green extension cards:
“metadata 透传”
“配置化参数映射”
“Agent Card skills / SKILL.md 说明工具参数”
Add small note:
“特殊差异放进通用扩展点，不污染 A2A 主链路”

Bottom practice path title:
“实践路径”
Four-step horizontal flow:
“1. 独立实现 Versatile Adapter”
“2. 继承 A2A 多邻居调用能力”
“3. 调试 parent-child 子 agent 场景”
“4. 用通用扩展点承载特殊参数”

Bottom conclusion sentence:
“从骨感到丰满：先保住 adapter + A2A 邻居调用脊柱，再把业务差异收敛到平台扩展点。”


Style reference:
Use the visual language of architecture diagrams generated on chatgpt.com: clean modular rounded rectangles, light shadows, thin connector lines, subtle grid, restrained engineering palette, high readability. All lines, arrows, timelines, card borders, chart paths, and connector strokes must be geometrically clean and stable: no warped lines, no wobbly curves, no broken arrows, no floating misaligned connectors, no hand-drawn distortion. Avoid marketing-poster aesthetics, decorative blobs, people, 3D objects, or glossy illustrations.

Style:
Same visual language as Slide 01 and Slide 04: warm off-white background, black/gray text, muted blue for common A2A backbone, muted red for customization risk, muted green for platform extension points, elegant rounded modules, subtle grid, light shadows, strong hierarchy, readable Chinese text. Keep the slide clean and case-based; do not create a dense code wall, evidence badges, PR chips, or long code snippets.
```

---

## Slide 06 - 最后总结：进入工程反馈循环

### 页面文案

标题：最后总结：不是让 AI 多写，而是进入工程反馈循环  
副标题：Vibe coding 要靠工程约束，而不是靠感觉

核心结论：

- 前期反复不是失败，后期提速也不是魔法。
- 关键变化不是 AI 写得更多，而是人把 AI 放进工程反馈循环里。
- 有效的 vibe coding = 承重墙 + 边界内加速 + 测试和 review 持续校正。

反馈循环：

- 人定义承重墙
- AI 在边界内实现
- E2E 守住脊柱
- Review 校正越界
- 文档回填事实

这次实践留下的判断：

1. 承重墙：架构设计要明确软件工程的承重墙，AI 只能在边界内发挥。
2. 警惕加法：AI 遇到问题常通过加法解决，不会自然反思上一个修改是否不合理。
3. 必须能走读：人必须能走读代码；如果不能维护，项目会走向失序。
4. 骨感到丰满：先建立项目脊柱并保证关键测试常绿，再逐步补能力。

收束：

- 不是把判断交给 AI，而是让 AI 在工程约束内持续产出。

### MCP 制图提示词

```text
Use case: productivity-visual.
Asset type: high-quality 16:9 PPT slide in Chinese.
All Chinese text must be accurate, readable Simplified Chinese. Do not use mojibake, pseudo-Chinese, corrupted glyphs, missing characters, or traditional/variant character substitutions.
Create the final conclusion slide for a developer retrospective about vibe coding practice, with the same visual style as Slide 01, Slide 04, and Slide 05. Inherit all global constraints: clean modular architecture-diagram style, rounded cards, light shadows, thin stable connector lines, no warped lines, no wobbly curves, no broken arrows, no floating misaligned connectors, no hand-drawn distortion, no marketing-poster aesthetics.

Title exactly:
“最后总结：不是让 AI 多写，而是进入工程反馈循环”

Subtitle:
“Vibe coding 要靠工程约束，而不是靠感觉”

Layout:
Top conclusion band + center feedback loop diagram + bottom four principle cards.

Top conclusion band:
“前期反复不是失败，后期提速也不是魔法；关键变化不是 AI 写得更多，而是人把 AI 放进工程反馈循环里。”

Center feedback loop:
Create a clean circular or hexagonal loop with five nodes connected by stable arrows:
“人定义承重墙”
“AI 在边界内实现”
“E2E 守住脊柱”
“Review 校正越界”
“文档回填事实”
Place this phrase in the center:
“工程反馈循环”

Bottom four principle cards:
Card 1 title:
“承重墙”
Text:
“架构设计要明确承重墙，AI 只能在边界内发挥。”

Card 2 title:
“警惕加法”
Text:
“AI 常通过加法补洞，不会自然反思上一个修改是否不合理。”

Card 3 title:
“必须能走读”
Text:
“人必须能读懂代码；不能维护，项目就会走向失序。”

Card 4 title:
“骨感到丰满”
Text:
“先让脊柱测试常绿，再逐步补能力。”

Bottom final sentence:
“有效的 vibe coding = 承重墙 + 边界内加速 + 测试和 review 持续校正”


Style reference:
Use the visual language of architecture diagrams generated on chatgpt.com: clean modular rounded rectangles, light shadows, thin connector lines, subtle grid, restrained engineering palette, high readability. All lines, arrows, timelines, card borders, chart paths, and connector strokes must be geometrically clean and stable: no warped lines, no wobbly curves, no broken arrows, no floating misaligned connectors, no hand-drawn distortion. Avoid marketing-poster aesthetics, decorative blobs, people, 3D objects, or glossy illustrations.

Style:
Same visual language as previous slides: warm off-white background, black/gray text, muted blue for feedback loop, muted green for validation, muted red only for caution, elegant rounded modules, subtle grid, light shadows, strong hierarchy, readable Chinese text. Serious retrospective conclusion slide, no hype, no decorative illustration, no dense code wall.
```

---
scope: v0730
module: agent-core
feature_type: functional
feature_id: FEAT-020
status: active
updated: 2026-07-31
---

# Agent 与 Workflow 意图匹配及后续处理

## 1. 特性定位

本特性为 Agent 和 Workflow 提供统一的意图处理流程：上游提交待匹配语义，意图匹配 SPI 使用初始化后的意图项完成选择，再调用意图结果生成 SPI 执行所选意图项的结果工具函数，向上游返回最终意图结果。

Agent Card 是意图套件支持的一种协议标准输入。Agent Card Skill 和用户自定义配置使用统一意图项与结果工具函数抽象；fallback 是匹配未命中后的独立路径，只复用结果工具函数抽象。Agent 和 Workflow 使用各自独立的接入方式。

## 2. 当前版本能力要求

### 2.1 初始化 SPI

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| 初始化 SPI | MUST | 意图套件必须提供可替换的初始化 SPI。SPI 接收 Agent Card 列表和用户自定义 `kwargs`，返回可匹配意图项及可选的 fallback 配置。 |
| 统一意图项 | MUST | 每个可匹配意图项必须包含匹配所需的描述信息和命中后可执行的结果工具函数。意图项的来源不得改变后续匹配和结果生成流程。 |
| 默认 Agent Card 初始化 | MUST | 默认初始化必须将 Agent Card 中每条 Skill 建立为独立意图项。每个意图项保留对应 Agent Card 和 Skill，并关联返回该 Agent Card 和 Skill 的结果工具函数。 |
| 默认自定义意图项初始化 | MUST | 默认初始化必须支持从 `kwargs` 读取用户提供的 `description` 和 `tool func`，并将其建立为意图项的匹配描述和结果工具函数。 |
| fallback 配置 | MUST | 初始化必须支持配置 fallback 意图项或 fallback 工具，并为 fallback 关联结果工具函数。fallback 是匹配未命中后的独立路径，不加入可匹配意图项。 |
| 自定义初始化 | MUST | 开发者必须能够替换默认初始化 SPI，自行解释 Agent Card 列表和 `kwargs`，但输出必须满足统一意图项和 fallback 语义。 |
| 意图描述固定 | MUST | 初始化完成后，本次意图套件使用的 Agent Card Skill 描述和用户自定义 `description` 不支持动态更新。需要变更描述时必须重新初始化意图套件。 |

### 2.2 意图匹配 SPI

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| 意图匹配 SPI | MUST | 意图套件必须提供可替换的意图匹配 SPI。上游向该 SPI 提交待匹配语义；SPI 使用初始化后的可匹配意图项完成选择。 |
| 统一匹配 | MUST | 意图匹配 SPI 不区分意图项来自 Agent Card 还是用户自定义配置，所有可匹配意图项使用同一匹配流程。 |
| 默认匹配方式 | MUST | 默认实现必须使用 Reranker 对全部可匹配意图项进行排序，并选择一个意图项或得到未匹配结果。 |
| 调用意图结果生成 SPI | MUST | 选择出意图项后，意图匹配 SPI 必须调用意图结果生成 SPI，执行该意图项的结果工具函数，并将生成结果作为最终意图结果返回上游。 |
| fallback 处理 | MUST | 没有选择出意图项且配置了 fallback 时，意图匹配 SPI 必须进入 fallback 路径，调用意图结果生成 SPI 执行 fallback 的结果工具函数，并将生成结果返回上游。 |
| 默认未匹配 | MUST | 没有选择出意图项且未配置 fallback 时，意图匹配 SPI 必须直接返回“意图未匹配”。 |
| 单次匹配 | MUST | 一次意图匹配最多选择一个意图项，不在意图套件内部拆分和匹配多个子任务。 |
| 匹配失败 | MUST | 意图匹配 SPI 执行失败时必须返回明确失败结果，不得执行其他意图项或 fallback。 |

### 2.3 意图结果生成 SPI

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| 意图结果生成 SPI | MUST | 意图套件必须提供可替换的意图结果生成 SPI。SPI 接收待匹配语义、选中的意图项或 fallback 配置以及用户自定义 `kwargs`，执行其结果工具函数并返回最终意图结果。 |
| 统一结果生成 | MUST | Agent Card Skill 意图项、用户自定义意图项和 fallback 都必须通过同一个意图结果生成 SPI，不得各自建立独立执行接口。 |
| 默认 Agent Card 结果函数 | MUST | Agent Card Skill 意图项的默认结果工具函数必须返回对应的 Agent Card、Skill 以及足以关联 FEAT-008 远端代理调用的信息，使 LLM 能准确调用 runtime 注入的对应远端 Agent 工具，并由 runtime 进入 `a2a_delegate` 代理调用路径。该函数不发起远端调用。 |
| 默认自定义结果函数 | MUST | 用户自定义意图项和 fallback 的默认结果工具函数必须调用对应的 `tool func`，并将其返回值作为最终意图结果。 |
| 结果返回 | MUST | 意图结果生成 SPI 的输出必须返回给意图匹配 SPI，由意图匹配 SPI 原样作为本次调用的最终意图结果返回上游。 |
| 结果生成失败 | MUST | 结果工具函数执行失败时必须返回明确失败结果，不得执行其他意图项或 fallback。 |

### 2.4 提示词

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| Agent 默认提示词 | MUST | 意图套件必须为 Agent 提供默认提示词，使 LLM 能识别需要进行意图匹配的场景，并调用意图工具。 |
| 用户覆盖 | MUST | 开发者必须能够在初始化意图套件时提供自定义提示词，完整覆盖默认提示词。 |
| 远端调用提示 | MUST | 默认提示词必须说明：意图结果包含 Agent Card 和 Skill 时，LLM 应根据结果调用 runtime 注入的对应远端 Agent 工具，由 runtime 按 FEAT-008 进入 `a2a_delegate` 路径完成代理请求。 |
| 意图跳变提示 | MUST | 默认提示词必须说明：远端 Agent 工具或本地工具在用户交互中断后返回包含最新用户意图的意图跳变语义时，LLM 应提取该最新意图，再次调用意图工具并传入该意图。 |
| Workflow 不适用 | MUST | 提示词注入能力仅用于 Agent 场景，对 Workflow 意图组件不生效。Workflow 按流程输入直接调用意图匹配 SPI。 |

### 2.5 未匹配与 fallback

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| fallback 独立路径 | MUST | fallback 不参与 Reranker 或自定义匹配，只在意图匹配 SPI 正常得到未匹配结果后执行。 |
| fallback 意图项 | MUST | 用户配置 fallback 意图项时，意图匹配未命中后必须调用意图结果生成 SPI，执行该配置的结果工具函数并返回结果。 |
| fallback 工具 | MUST | 用户直接配置 fallback 工具时，意图匹配未命中后必须调用意图结果生成 SPI，执行该工具并返回结果。 |
| 未配置 fallback | MUST | 未配置 fallback 意图项或 fallback 工具时，意图匹配未命中后必须返回“意图未匹配”，不得执行任何工具。 |
| fallback 触发边界 | MUST | 初始化失败、意图匹配 SPI 执行失败或意图结果生成失败不得触发 fallback。 |

### 2.6 Agent 场景

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| 意图工具 | MUST | 意图套件必须能够作为 LLM 可调用的工具加入 Agent，并接收 LLM 提交的用户请求或子任务。 |
| DeepAgent 支持 | MUST | DeepAgent 必须能够使用意图工具，并与已有本地工具、远端 Agent 工具和任务处理流程配合。 |
| ReAct Agent 支持 | MUST | ReAct Agent 必须能够使用同一意图能力，不要求提供另一套匹配逻辑。 |
| 提示词生效 | MUST | Agent 开始模型决策前，意图套件的默认提示词或用户覆盖提示词必须加入 Agent 可见上下文。 |
| 调用入口 | MUST | Agent 意图工具必须调用意图匹配 SPI，并将该 SPI 返回的最终意图结果返回给 LLM；不得在意图工具中重复选择意图项或调用结果工具函数。 |
| Agent Card Skill 结果 | MUST | Agent Card Skill 的最终意图结果必须使 LLM 能准确调用 runtime 注入的对应远端 Agent 工具，并使该调用进入 FEAT-008 的 `a2a_delegate` 代理路径。 |
| 自定义与 fallback 结果 | MUST | 用户自定义意图项或 fallback 的最终意图结果可以指导 LLM 调用下一个工具，也可以提供可直接答复用户的内容。 |
| 正常结束 | MUST | 远端 Agent 工具或本地工具正常执行完成后，LLM 根据工具结果完成本轮答复，本轮会话结束，不自动重新执行意图匹配。 |
| 意图跳变后重新执行 | MUST | 远端 Agent 工具或本地工具在用户交互中断后返回包含最新用户意图的意图跳变语义时，LLM 必须按生效提示词提取该最新意图，并再次调用意图工具进入新的意图处理循环。 |
| LLM 继续决策 | MUST | 意图工具返回后，控制权交回 LLM，由 LLM 执行下一次工具调用或直接答复；只有收到明确的意图跳变语义时才按上述要求重新调用意图工具。 |

### 2.7 Workflow 场景

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| 独立 Workflow 意图组件 | MUST | 必须提供面向 Workflow 的意图组件，不使用 Agent 意图工具代替 Workflow 组件。 |
| 提示词不生效 | MUST | Agent 场景的默认提示词和用户覆盖提示词不注入 Workflow，Workflow 意图组件只处理流程传入的待匹配语义。 |
| Agent 重匹配机制不适用 | MUST | 基于提示词识别意图跳变并重新调用意图工具的机制仅适用于 Agent，不适用于 Workflow。Workflow 意图组件仅在流程经过该组件时执行匹配。 |
| 调用入口 | MUST | Workflow 意图组件必须调用意图匹配 SPI，并将该 SPI 返回的最终意图结果交给下一个节点；不得在组件中重复选择意图项或调用结果工具函数。 |
| Agent Card Skill 结果 | MUST | 最终意图结果包含 Agent Card 和 Skill 时，Workflow 必须能够根据结果进入远端 Agent 调用流程。 |
| 自定义与 fallback 结果 | MUST | 用户自定义意图项或 fallback 的最终意图结果必须能够被下一个节点用于本地工具、直接答复或其他业务处理。 |
| 未匹配结果 | MUST | 未配置 fallback 且没有匹配到意图项时，Workflow 意图组件必须将“意图未匹配”交给下一个节点。 |
| 场景差异 | MUST | Agent 与 Workflow 可以使用不同的输入输出形式和流程控制方式，但必须遵守相同的初始化、匹配、结果生成和 fallback 语义。 |

## 3. 接口与入口要求

| 入口 | 输入 | 输出 |
|---|---|---|
| 初始化 SPI | Agent Card 列表、用户自定义 `kwargs` | 可匹配意图项和可选的 fallback 配置；每项均关联结果工具函数。 |
| Agent 提示词配置 | 可选的用户自定义提示词 | 仅对 Agent 生效；未配置时使用默认提示词，已配置时使用用户提示词覆盖默认提示词。 |
| 意图匹配 SPI | 上游提交的待匹配语义 | 使用初始化后的意图项和已配置的意图结果生成 SPI，返回最终意图结果、“意图未匹配”或失败结果。 |
| 意图结果生成 SPI | 待匹配语义、选中的意图项或 fallback 配置、用户自定义 `kwargs` | 最终意图结果。 |
| Agent 意图工具 | LLM 提交的用户请求或子任务 | 意图匹配 SPI 返回的最终意图结果。 |
| Workflow 意图组件 | Workflow 提交的待匹配语义 | 意图匹配 SPI 返回的最终意图结果。 |

具体接口类型和字段由 L2 设计定义。

## 4. 场景与用户旅程

### 4.1 默认方式初始化意图套件

1. 开发者向初始化 SPI 提供一个或多个 Agent Card，并通过 `kwargs` 提供若干组 `description` 和 `tool func`。
2. 默认初始化 SPI 将每条 Agent Card Skill 转换为意图项，其结果工具函数返回对应的 Agent Card 和 Skill。
3. 默认初始化 SPI 将每组 `description + tool func` 转换为用户自定义意图项。
4. 开发者可以配置 fallback 意图项或 fallback 工具；fallback 被单独保存，不加入可匹配意图项。
5. Agent 意图套件选择默认提示词或开发者提供的覆盖提示词；Workflow 不使用该提示词。意图套件同时将本次初始化的所有意图描述固定下来。
6. 初始化结果提供给意图匹配 SPI 使用；描述发生变化时需要重新初始化。

### 4.2 使用自定义 SPI

1. 开发者可以替换初始化 SPI，自行解释 Agent Card 列表和 `kwargs` 并生成意图项与 fallback 配置。
2. 开发者可以替换意图匹配 SPI，使用业务规则或其他方式选择意图项；选择后仍须调用意图结果生成 SPI。
3. 开发者可以替换意图结果生成 SPI，控制结果工具函数的调用方式并生成最终意图结果。
4. 三个 SPI 被分别替换后，仍遵守匹配后生成结果、未匹配进入 fallback 或返回“意图未匹配”的流程。

### 4.3 DeepAgent 匹配并调用远端 Agent

1. 用户向 DeepAgent 提交请求，LLM 将用户请求或已经生成的子任务传给意图工具。
2. 意图工具调用意图匹配 SPI，意图匹配 SPI 选择由某个 Agent Card Skill 初始化出的意图项。
3. 意图匹配 SPI 调用意图结果生成 SPI，执行该意图项的结果工具函数，得到对应 Agent Card、Skill 及 FEAT-008 远端代理调用所需的信息。
4. 意图匹配 SPI 将生成结果作为最终意图结果返回意图工具，再由意图工具返回给 LLM。
5. LLM 根据结果准确调用 runtime 注入的对应远端 Agent 工具，该调用由 runtime 进入 `a2a_delegate` 代理路径。
6. runtime 按 FEAT-008 完成远端调用并将结果交回 DeepAgent，LLM 完成本轮答复，本轮会话结束。

ReAct Agent 使用相同的意图工具调用过程，但不依赖 DeepAgent 特有的任务规划能力。

### 4.4 Agent 命中用户自定义意图项

1. LLM 将用户请求或子任务传给意图工具，意图工具调用意图匹配 SPI。
2. 意图匹配 SPI 选择用户自定义意图项，并调用意图结果生成 SPI。
3. 意图结果生成 SPI 执行该意图项关联的 `tool func`，将执行结果返回意图匹配 SPI。
4. 意图匹配 SPI 将该执行结果作为最终意图结果返回给 LLM。
5. LLM 根据结果调用下一个工具或直接答复用户；下游工具正常完成后，本轮会话结束。

### 4.5 Agent 未匹配与 fallback

1. 意图匹配 SPI 未选择出意图项。
2. 已配置 fallback 时，意图匹配 SPI 调用意图结果生成 SPI，执行 fallback 的结果工具函数，并将结果返回给 LLM。
3. 未配置 fallback 时，意图匹配 SPI 直接返回“意图未匹配”。
4. LLM 根据最终结果答复用户，本轮会话结束。

### 4.6 Agent 在工具中断后处理意图跳变

1. Agent 根据意图结果调用远端 Agent 工具或本地工具。
2. 工具执行过程中需要用户补充信息并产生用户交互中断，本轮工具调用等待用户输入。
3. 用户提交新的输入，并在输入中表达了不同于原任务的最新意图。
4. 工具接收该输入后结束当前调用，向 Agent 返回明确的意图跳变语义，其中包含需要重新匹配的最新用户意图。
5. LLM 根据生效提示词从意图跳变语义中取得最新意图，再次调用意图工具。
6. 意图匹配 SPI 使用最新意图重新执行匹配和意图结果生成，LLM 根据新结果进入新的工具调用或答复流程。

### 4.7 Workflow 匹配并调用远端 Agent

1. Workflow 意图组件将用户请求或任务内容传给意图匹配 SPI。
2. 意图匹配 SPI 选择 Agent Card Skill 意图项，并调用意图结果生成 SPI，得到对应 Agent Card、Skill 及 FEAT-008 远端代理调用所需的信息。
3. Workflow 意图组件取得最终意图结果并交给下一个节点，不增加一次 LLM 决策。
4. Workflow 根据 Agent Card 和 Skill 进入远端 Agent 调用流程。
5. runtime 按 FEAT-008 调用对应远端 Agent，并将远端结果作为本次 Workflow 服务调用结果返回。

### 4.8 Workflow 执行用户自定义意图或 fallback

1. 意图匹配 SPI 选择用户自定义意图项时，调用意图结果生成 SPI 执行对应的 `tool func`。
2. 意图匹配 SPI 未选择出意图项且配置了 fallback 时，调用同一意图结果生成 SPI 执行 fallback 的结果工具函数。
3. Workflow 意图组件将意图匹配 SPI 返回的最终意图结果交给下一个节点。
4. 未配置 fallback 时，Workflow 意图组件将“意图未匹配”交给下一个节点。

## 5. 行为语义与边界

- 主流程固定为：上游提交待匹配语义，意图匹配 SPI 选择意图项，意图匹配 SPI 调用意图结果生成 SPI，最终结果返回上游。
- Agent Card 是协议标准输入。Agent Card Skill 与用户自定义配置初始化后使用同一意图项、意图匹配 SPI 和意图结果生成 SPI。
- 每个可匹配意图项都必须关联一个可执行的结果工具函数。
- Agent Card Skill 的结果工具函数返回 Agent Card、Skill 及识别 runtime 注入远端 Agent 工具所需的信息，使工具调用进入 FEAT-008 `a2a_delegate` 代理路径；用户自定义意图项的结果工具函数执行用户提供的 `tool func`。
- fallback 也必须关联结果工具函数，但不属于可匹配意图项，只在意图匹配未命中后执行。
- 未配置 fallback 时，意图匹配未命中必须返回“意图未匹配”。
- Agent 默认提示词必须引导 LLM 调用意图工具、根据 Agent Card Skill 结果调用 runtime 注入的对应远端 Agent 工具，并在下游工具返回意图跳变语义时使用最新用户意图重新执行意图工具；开发者可以覆盖该提示词。
- 意图跳变语义是远端 Agent 工具或本地工具在用户交互中断后返回的明确结果，必须包含需要重新匹配的最新用户意图。
- 提示词注入和基于该提示词的意图跳变重匹配对 Workflow 不生效，Workflow 直接使用流程传入的语义调用意图匹配 SPI。
- 下游远端 Agent 工具或本地工具正常完成时，本轮会话结束；只有工具在用户交互中断后返回意图跳变语义时，Agent 才重新执行意图工具。
- 当前版本不支持动态更新意图描述。Agent Card Skill 描述或用户自定义 `description` 发生变化时，必须重新初始化意图套件。
- fallback 不处理初始化失败、意图匹配 SPI 执行失败或意图结果生成失败。
- 意图套件不负责把一段输入自动拆分成多个子任务；DeepAgent 或业务流程可以先生成子任务，再分别进行匹配。

## 6. 需求归属与验收要求

- 本特性的 Agent 意图工具、Workflow 意图组件、初始化 SPI、意图匹配 SPI、意图结果生成 SPI 和默认实现由 `agent-solution/common/agent-core-ext-java` 提供。
- 与远端 Agent Card 获取、Skill 和远端目标关联以及 A2A 调用相关的 runtime 能力遵守 FEAT-008。
- 必须验证默认初始化能够处理多个 Agent Card、一个 Agent Card 的多条 Skill 以及多组自定义 `description` 和 `tool func`。
- 必须验证 Agent Card Skill 和用户自定义意图项使用同一个意图匹配 SPI 和意图结果生成 SPI。
- 必须验证意图匹配 SPI 选择意图项后调用意图结果生成 SPI，并将其输出作为最终意图结果。
- 必须分别验证初始化 SPI、意图匹配 SPI 和意图结果生成 SPI 可以被替换。
- 必须验证默认提示词、用户覆盖提示词以及远端 Agent 工具调用能够进入 `a2a_delegate` 路径。
- 必须验证提示词注入能力对 Workflow 不生效。
- 必须验证远端 Agent 工具和本地工具正常完成后不重新执行意图，并验证两类工具在用户交互中断后返回意图跳变语义时，LLM 使用最新用户意图重新调用意图工具。
- 必须验证 DeepAgent、ReAct Agent 和 Workflow 的 Agent Card Skill、自定义意图项、fallback 和无 fallback 场景。
- 必须验证初始化完成后不能动态更新 Agent Card Skill 描述或用户自定义 `description`，重新初始化后新描述才生效。
- 必须验证 fallback 不参与匹配，且仅在正常未匹配后执行。
- 必须验证初始化失败、匹配失败和结果生成失败不会触发 fallback 或改为执行其他目标。

## 7. 关联文档

- `version-scope/FEAT-008-user-interaction-interrupt-and-response.md`
- `architecture/L2-Low-Level-Design/agent-core/Feat-Func-020-agent-intent-recognition-and-downstream-task-matching.md`

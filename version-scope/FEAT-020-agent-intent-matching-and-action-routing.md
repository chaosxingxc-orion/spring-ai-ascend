---
scope: v0730
module: agent-core
feature_type: functional
feature_id: FEAT-020
status: active
updated: 2026-08-01
---

# Agent 与 Workflow 意图匹配及结果生成

## 1. 特性定位

本特性为 Agent 和 Workflow 提供统一的意图处理套件，并将套件内部流程拆分为四个可替换 SPI：

1. **初始化 SPI**：把 Agent Card Skill 和用户自定义配置初始化为可匹配意图项；每个意图项由“匹配描述 + 意图结果函数”组成。
2. **输入归一化 SPI**：仅对入口适配器已经组装的业务匹配输入执行确定性语义整理，不负责 Tool / Workflow 参数映射，也不负责注入 Runtime 事实。
3. **意图匹配 SPI**：使用归一化后的业务语义和初始化后的意图项进行候选排序和选择。
4. **意图结果生成 SPI**：匹配成功或选中 fallback 后，执行对应的意图结果函数，并按统一 `IntentResult` 契约得到本次意图匹配结果。

这里的“执行意图结果函数”不等于“执行用户意图”。意图结果函数只负责生成或转换意图匹配结果，不调用下游 Agent、不调用普通业务 Tool / API、不创建或推进 Task，也不产生其他业务副作用。真正的下游 Agent 调用、普通 Tool 调用或直接答复，发生在结构化意图结果返回 AgentLoop / Workflow 之后。

意图结果函数是开发者扩展意图返回内容的关键机制。开发者可以为一句自定义意图描述配置一个结果函数；该意图命中后，结果生成 SPI 调用此函数，并把函数返回的 `IntentAction` 放入统一 `IntentResult`。标准 Action 用于表达委派远端 Agent、调用本地 Tool 或直接答复；开发者仍可通过 `CUSTOM` Action 的 `data` 承载自定义结果。

Agent Card 是初始化 SPI 支持的标准能力描述资产来源，不是 Tool / Node 运行时的意图匹配请求。默认初始化把每条 Agent Card Skill 建立为意图项，并为其关联默认意图结果函数；该函数返回 Agent Card、Skill 和稳定远端目标标识，使 AgentLoop 或 Workflow 后续能够调用对应远端 Agent。用户自定义意图项则由开发者提供描述和意图结果函数。两类意图项使用相同匹配 SPI 与结果生成 SPI。

fallback 是正常未匹配后的独立结果路径，不加入候选匹配。配置 fallback 时，结果生成 SPI执行其意图结果函数；未配置时返回“意图未匹配”。fallback结果同样只生成结果，不执行下游业务动作。

```mermaid
flowchart LR
    U["Agent Tool / Workflow Node 业务输入"] --> A["固定入口适配"]
    A --> N["输入归一化 SPI"]
    X["可信 Runtime 执行上下文"] --> RF["固定 Runtime Facts 注入"]
    N --> Q["固定匹配请求组装"]
    RF --> Q
    Q --> M["意图匹配 SPI"]
    M --> S["选中意图项或 fallback"]
    S --> G["意图结果生成 SPI"]
    G --> FN["执行意图结果函数"]
    FN --> R["统一 IntentResult + IntentAction"]
    R --> UP["AgentLoop / Workflow 后续处理"]
    UP --> D["可选：调用远端 Agent、普通 Tool 或直接答复"]
```

## 2. 当前版本能力要求

### 2.1 初始化 SPI

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| 初始化 SPI | MUST | 意图套件必须提供可替换的初始化 SPI。SPI 在 Agent 或 Workflow 初始化阶段接收 Agent Card及其初始化上下文、用户自定义 `kwargs`，返回可匹配意图项及可选 fallback 配置。 |
| 统一意图项 | MUST | 每个可匹配意图项必须包含匹配所需的描述信息和一个意图结果函数。意图项来源不得改变后续匹配和结果生成流程。 |
| 默认 Agent Card 初始化 | MUST | 默认初始化必须将 Agent Card 中每条 Skill 建立为独立意图项。每个意图项保留对应 Agent Card、Skill和初始化上下文，并关联返回这些信息的默认意图结果函数。 |
| 远端目标上下文 | MUST | runtime 提供 Agent Card 时，初始化上下文必须包含对应的稳定远端目标标识；同一 Agent Card 的所有 Skill 共享该标识，使默认结果能够关联到正确的远端 Agent 工具。 |
| 默认自定义意图项初始化 | MUST | 默认初始化必须支持从 `kwargs` 读取开发者提供的 `description` 和意图结果函数，并将其建立为意图项的匹配描述和结果生成逻辑。 |
| 用户自定义结果 | MUST | 开发者提供的意图结果函数必须返回统一 `IntentAction`。需要框架标准后续处理时使用 `DELEGATE_AGENT`、`CALL_TOOL` 或 `DIRECT_RESPONSE`；其他开发者自定义结果使用 `CUSTOM`，其 `data` 由开发者定义并由对应 Agent / Workflow 按约定消费。 |
| fallback 配置 | MUST | 初始化必须支持配置 fallback 意图项或 fallback 意图结果函数。fallback 不加入可匹配意图项，只在正常未匹配后进入结果生成流程。 |
| 自定义初始化 | MUST | 开发者必须能够替换默认初始化 SPI，自行解释 Agent Card、对应初始化上下文和 `kwargs`，但输出必须满足统一意图项、意图结果函数和 fallback 语义。 |
| 意图描述固定 | MUST | 初始化完成后，本次意图套件使用的 Agent Card Skill描述和用户自定义 `description` 不支持动态更新。需要变更时必须重新初始化意图套件。 |
| `kwargs` 生命周期 | MUST | 初始化 SPI 的 `kwargs` 在 Agent 或 Workflow 初始化时由开发者提供，并作为本次意图套件的初始化配置保存；它不等同于 Agent loop 调用工具时框架传入的 `kwargs`。 |

### 2.2 输入处理链与输入归一化 SPI

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| 固定输入处理链 | MUST | 输入处理必须依次经过场景入口适配、输入归一化 SPI、可信 Runtime Facts 注入后的固定请求组装。输入归一化 SPI 不得承担 Tool / Workflow 参数映射或 Runtime Facts 注入。 |
| Agent 输入适配 | MUST | Agent 场景由 LLM 或上游 Agent 通过 Tool schema 提供业务匹配输入；Agent Tool 的固定入口适配器负责校验并映射为统一业务输入。 |
| Workflow 输入适配 | MUST | Workflow 场景由流程变量通过 Node 端口映射提供业务匹配输入；Workflow 组件的固定入口适配器负责校验并映射为与 Agent 场景语义一致的统一业务输入。 |
| 输入归一化 SPI | MUST | 意图套件必须提供可替换的输入归一化 SPI。SPI 只接收已经适配的业务匹配输入，输出归一化业务语义，不读取 Runtime、Session、TaskStore、Memory 或其他未通过输入契约提供的状态。 |
| 默认归一化 | MUST | 默认实现必须校验 `query`，清理无意义空白，按稳定键顺序模板化 `confirmedFacts`，并按固定分段规则加入可选 `contextSummary`；`originalQuery` 单独保留，存在已澄清或已拆解的 `query` 时不得重复拼入匹配文本。默认实现不得额外调用 LLM。 |
| 自定义归一化 | MUST | 开发者可以替换默认输入归一化 SPI，增加领域词映射、别名转换或自主选择的模型处理，但输出必须满足统一归一化结果契约，且不得生成、修改或覆盖可信 Runtime Facts。 |
| 固定 Runtime Facts 注入 | MUST | 当前执行环境能够提供且已授权的 `tenantId`、`conversationId`、`taskId`、`traceId` 由固定入口逻辑从可信执行上下文注入，不要求用户、LLM 或 Workflow 业务节点生成，也不得被同名业务输入覆盖。 |
| 固定匹配请求组装 | MUST | 输入归一化完成后，固定组装逻辑将归一化业务语义与可信 Runtime Facts 合并为匹配请求。自定义归一化 SPI 不能替换该组装逻辑。 |
| 原始输入保留 | MUST | 当 `query` 来自澄清、子任务拆解或上游改写时，应在可获得且已授权的范围内通过 `originalQuery` 保留原始用户输入或其引用，供匹配证据、纠偏和可观测关联使用。 |
| 输入使用边界 | MUST | `contextSummary` 只有在上游或 Runtime 通过既定契约显式提供时才能参与归一化；020 不得自行读取未授权的完整会话、Memory 内部状态或 Task 存储。 |
| 归一化失败 | MUST | 业务输入无效或输入归一化 SPI 执行失败时必须返回 `FAILED`，失败阶段为 `INPUT_NORMALIZATION`；不得调用意图匹配 SPI、意图结果生成 SPI 或 fallback。 |

统一业务匹配输入至少表达以下信息：

| 字段 | 要求 | 定义与来源 |
|---|---|---|
| `query` | MUST | 本次需要匹配的完整请求，可以是用户本轮自然语言，也可以是 DeepAgent、ReAct Agent 或 Workflow 已澄清、改写或拆解出的明确子任务描述。 |
| `originalQuery` | MAY | 用户原始输入或其可追踪引用。它用于证据与纠偏，不因存在已澄清 `query` 而默认重复加入匹配文本。 |
| `confirmedFacts` | MAY | 上游 Agent / Workflow 已经确认的业务事实键值集合，例如 `businessType=理财`、`amount=10000`。它取代含义不明确的“槽位”称呼，不代表 Runtime 自动抽取的数据。 |
| `contextSummary` | MAY | 上游显式提供或 Runtime 已授权暴露的有限上下文摘要，只包含与本次匹配有关的信息。 |

可信 Runtime Facts 与业务匹配输入分离：

| 字段 | 要求 | 定义与来源 |
|---|---|---|
| `tenantId` | 条件可选 | 当前调用所属的可信租户标识，只表示稳定 ID，不封装用户输入或其他租户资料；由可信入口解析或透传。 |
| `conversationId` | 条件可选 | 当前会话关联标识，用于关联同一会话内的调用，不代表完整会话历史。 |
| `taskId` | 条件可选 | 当前调用所属的 Runtime Task 标识，不携带完整 Task 对象、状态历史或子任务树。 |
| `traceId` | 条件可选 | 当前调用链追踪标识，用于日志、Span 和审计关联，不携带完整轨迹内容。 |

上述 Runtime Facts 仅在当前执行环境能够提供且调用方获授权时注入；缺少可选事实时不得由 020 自行推断。它们默认不参与 Reranker 语义打分，也不得无差别拼接进归一化文本。

### 2.3 意图匹配 SPI

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| 意图匹配 SPI | MUST | 意图套件必须提供可替换的意图匹配 SPI。SPI使用经过归一化的匹配输入和初始化后的可匹配意图项完成选择。 |
| 统一匹配 | MUST | 意图匹配 SPI 不区分意图项来自 Agent Card还是用户自定义配置，所有可匹配意图项使用同一匹配流程。 |
| 默认匹配方式 | MUST | 默认实现必须使用 Reranker 对全部可匹配意图项进行排序，并对排序首项执行匹配阈值判断。只有首项达到初始化时配置的匹配阈值才能被选择，否则得到未匹配结果。 |
| 匹配阈值 | MUST | 初始化结果中存在可匹配意图项时，默认匹配实现的匹配阈值必须在初始化时配置；未配置有效阈值时初始化失败，不得在执行阶段无条件选择排序首项。 |
| 无可匹配项 | MUST | 初始化结果中没有可匹配意图项时，不得调用 Reranker；意图匹配必须直接得到未匹配结果，并按 fallback配置继续处理。 |
| 调用结果生成 SPI | MUST | 选择出意图项后，意图匹配 SPI必须调用意图结果生成 SPI，由后者执行选中项的意图结果函数并返回统一 `IntentResult`。匹配 SPI不得绕过结果生成 SPI直接构造 `IntentAction`。 |
| fallback 处理 | MUST | 没有选择出意图项且配置了 fallback时，意图匹配 SPI必须进入 fallback路径并调用意图结果生成 SPI。 |
| 默认未匹配 | MUST | 没有选择出意图项且未配置 fallback时，必须返回“意图未匹配”的统一结果。 |
| 单次匹配 | MUST | 一次意图匹配最多选择一个意图项，不在意图套件内部拆分和匹配多个子任务。 |
| 匹配失败 | MUST | 意图匹配 SPI执行失败时必须返回明确失败结果，不得执行任何意图结果函数、改选其他意图项或进入 fallback。 |
| 业务无副作用 | MUST | 匹配 SPI只选择意图项和组织结果生成流程，不调用下游 Agent、普通业务Tool / API，也不创建或推进 Task。 |

### 2.4 意图结果生成 SPI

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| 意图结果生成 SPI | MUST | 意图套件必须提供可替换的意图结果生成 SPI。SPI 接收本次结构化匹配上下文、选中的意图项或 fallback 配置以及初始化时保存的用户自定义 `kwargs`，执行对应意图结果函数并返回统一 `IntentResult`。结构化输入不得在匹配完成后退化为仅包含一句话的参数。 |
| 统一结果生成 | MUST | Agent Card Skill意图项、用户自定义意图项和 fallback都必须通过同一个意图结果生成 SPI，不得各自建立独立结果生成接口。 |
| 默认 Agent Card 结果函数 | MUST | Agent Card Skill意图项的默认结果函数必须返回 `DELEGATE_AGENT` Action，其中包含对应 Agent Card、Skill、稳定远端目标标识及后续调用所需参数，使 LLM或Workflow能在结果返回后调用 runtime注入的对应远端 Agent工具。该函数不发起远端调用。 |
| 用户自定义结果函数 | MUST | 用户自定义意图项和 fallback的结果函数由开发者提供，并返回统一 `IntentAction`。需要自定义结果时使用 `CUSTOM` Action，其 `data` 由开发者定义。 |
| 统一 `IntentResult` | MUST | 意图套件面向 Tool / Node 上游的输出必须使用本节定义的稳定 `IntentResult`。匹配成功、fallback、未匹配和处理失败使用相同外层契约，不得由不同 SPI 或场景返回互不兼容的自由文本。 |
| 标准 Action | MUST | 框架必须定义 `DELEGATE_AGENT`、`CALL_TOOL`、`DIRECT_RESPONSE`、`CUSTOM` 四类 Action 及其基本语义。结果函数只生成 Action，不执行 Action 所表达的业务动作。 |
| 自定义数据透明传递 | MUST | 对 `CUSTOM` Action 的 `data`，框架不得擅自解释、改写或执行。Agent 开发者或 Workflow 开发者负责定义和消费其业务结构。 |
| 自定义数据消费责任 | MUST | 框架不要求每个 `CUSTOM.data` 在运行时重复携带统一的 type、version 和 schema。需要程序化消费时，自定义意图提供者必须与对应 Workflow 或其他消费者约定稳定数据结构；该约定不是所有意图结果的统一框架字段。 |
| 结果函数行为边界 | MUST | 意图结果函数只允许生成、转换或封装本次意图结果，不得调用下游 Agent、普通业务Tool / API、外部业务动作，不得创建或推进 Task，也不得修改业务状态。 |
| 同步回调边界 | MUST | 意图结果函数是意图 Tool或Workflow意图组件内部执行的同步、不可中断回调。它必须在本次调用内返回结果或失败，不得产生用户交互中断或建立独立续接流程。 |
| 结果返回 | MUST | 意图结果生成 SPI的统一结果必须返回意图匹配 SPI，再由意图匹配 SPI原样返回上游。 |
| 结果生成失败 | MUST | 意图结果函数或结果封装失败时必须返回明确失败结果，不得执行其他意图结果函数、改选其他意图项或进入 fallback。 |

`IntentResult` 至少表达以下信息：

| 字段 | 要求 | 说明 |
|---|---|---|
| `type` | MUST | 固定为 `intent_result`，供 Agent、Workflow 和适配器识别结果类型。 |
| `status` | MUST | 取值至少包括 `MATCHED`、`FALLBACK`、`UNMATCHED`、`FAILED`。 |
| `intentId` | 条件必需 | `MATCHED` 时关联选中的意图项；`FALLBACK` 时关联 fallback 配置。 |
| `sourceType` | 条件必需 | `MATCHED` 时标识意图项来自 Agent Card Skill 或用户自定义配置。 |
| `action` | 条件必需 | `MATCHED` 或 `FALLBACK` 时包含意图结果函数生成的 `IntentAction`。 |
| `message` | MAY | 面向 Agent / Workflow 的安全说明；`UNMATCHED` 时至少提供明确的未匹配信息。 |
| `failure` | 条件必需 | `FAILED` 时包含失败阶段、稳定失败码和安全错误信息。失败阶段至少区分 `INPUT_NORMALIZATION`、`MATCH`、`RESULT_GENERATION` 和 `RESULT_FUNCTION`。 |
| `matchMetadata` | MAY | 允许携带 Reranker 分数、阈值判断和受限追踪关联；不得暴露未授权输入或完整内部状态。 |

`IntentAction` 至少表达以下信息：

| 字段 | 要求 | 说明 |
|---|---|---|
| `type` | MUST | 取值为 `DELEGATE_AGENT`、`CALL_TOOL`、`DIRECT_RESPONSE` 或 `CUSTOM`。 |
| `target` | 条件必需 | `DELEGATE_AGENT` 时标识对应远端 Agent Tool；`CALL_TOOL` 时标识主体 Agent / Workflow 已装配的普通 Tool。 |
| `instruction` | MAY | 提供给 Agent 或 Workflow 的下一步处理说明。 |
| `data` | MAY | Action 所需参数或开发者自定义结果。标准 Action 的必要数据由框架定义；`CUSTOM.data` 由开发者定义。 |

各 Action 的处理语义如下：

| Action 类型 | 框架定义的结果语义 | 结果返回后的执行方 |
|---|---|---|
| `DELEGATE_AGENT` | 指向命中的 Agent Card Skill及对应远端 Agent Tool，携带委派所需参数。 | AgentLoop 或 Workflow 后续远端 Agent 调用路径。 |
| `CALL_TOOL` | 指向主体 Agent / Workflow 已静态装配的普通 Tool，可携带调用建议参数。 | AgentLoop 的标准 Tool 机制或 Workflow 后续 Tool 节点。 |
| `DIRECT_RESPONSE` | 提供可直接返回或用于组织答复的结果。 | AgentLoop 或 Workflow 响应节点。 |
| `CUSTOM` | 携带开发者定义的 `data`；框架只透明传递。 | 与该自定义意图约定好的 Agent / Workflow 消费逻辑。 |

### 2.5 提示词

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| Agent默认提示词 | MUST | 意图套件必须为 Agent提供默认提示词，使 LLM能识别需要进行意图匹配的场景，并调用意图 Tool。 |
| 用户覆盖 | MUST | 开发者必须能够在初始化意图套件时提供自定义提示词，完整覆盖默认提示词。 |
| Agent Card结果提示 | MUST | 默认提示词必须说明：意图结果的 `action.type=DELEGATE_AGENT` 时，LLM应使用 `action.target` 和 `action.data` 调用 runtime注入的对应远端 Agent工具，由FEAT-004完成代理调用。 |
| 自定义结果提示 | MUST | 对 `CUSTOM` Action，开发者必须能够通过自定义提示词或等价配置说明其 `data` 含义和后续处理方式；框架不得替开发者推断其业务含义。 |
| 远端中断提示 | MUST | 远端 Agent调用需要用户输入时按FEAT-008返回和续接中断。 |
| 意图跳变提示 | MUST | 本次意图已经匹配并进入后续 Agent loop后，下游执行在用户交互续接后返回意图跳变语义，且上下文中携带最新用户意图时，LLM应再次调用意图 Tool并传入该意图。 |
| Workflow不适用 | MUST | 提示词注入能力仅用于Agent场景，对Workflow意图组件不生效。Workflow按流程输入直接调用意图处理链，并显式消费 `IntentResult` 和 `IntentAction`。 |

### 2.6 未匹配与 fallback

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| fallback独立路径 | MUST | fallback不参与Reranker或自定义匹配，只在意图匹配SPI正常得到未匹配结果后进入意图结果生成SPI。 |
| fallback结果函数 | MUST | fallback可以配置意图结果函数；该函数仅生成 `IntentAction`，不执行 Action 所表达的下游业务动作。 |
| 未配置fallback | MUST | 未配置fallback时，正常未匹配必须返回`status=UNMATCHED`的`IntentResult`，不执行任何结果函数或下游动作。 |
| fallback触发边界 | MUST | 初始化失败、输入归一化SPI执行失败、意图匹配SPI执行失败或意图结果生成失败不得触发fallback。 |

### 2.7 Agent场景

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| 意图Tool | MUST | 意图套件必须能够作为LLM可调用的Tool加入Agent，并接收LLM提交的用户请求或子任务。 |
| DeepAgent支持 | MUST | DeepAgent必须能够使用意图Tool，并与已有普通Tool、runtime注入的远端Agent工具和任务处理流程配合。 |
| ReAct Agent支持 | MUST | ReAct Agent必须能够使用同一意图能力，不要求提供另一套匹配逻辑。 |
| 调用入口 | MUST | Agent意图Tool必须依次调用固定入口适配、输入归一化SPI、固定匹配请求组装和意图匹配SPI；匹配SPI调用意图结果生成SPI并执行选中项的意图结果函数，最终把统一 `IntentResult` 返回LLM。 |
| 结果函数边界 | MUST | Agent意图Tool内部执行意图结果函数只用于生成 `IntentAction`，不得在该函数中执行 Action 所描述的下游业务动作。 |
| Agent Card结果 | MUST | 默认 `DELEGATE_AGENT` Action必须使LLM能准确调用runtime注入的对应远端Agent工具，并使调用进入FEAT-004路径。 |
| 自定义结果 | MUST | LLM根据 `IntentAction` 及开发者对 `CUSTOM.data` 的提示说明决定后续行为，可以调用下一个普通Tool、调用其他已暴露能力或直接答复用户。 |
| 控制权返回 | MUST | 意图Tool返回 `IntentResult` 后，控制权交回LLM；后续动作与本次意图结果函数执行是两个独立步骤。 |
| 正常结束 | MUST | 后续Agent或普通Tool正常完成后，LLM根据执行结果完成本轮答复，不自动重新执行意图匹配。 |
| 意图跳变后重新匹配 | MUST | 后续执行在用户交互续接后返回明确意图跳变语义，且上下文中存在最新用户意图时，LLM必须按生效提示词重新调用意图Tool。 |

### 2.8 Workflow场景

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| 独立Workflow意图组件 | MUST | 必须提供面向Workflow的意图组件，不使用Agent意图Tool代替Workflow组件。 |
| 调用入口 | MUST | Workflow意图组件必须依次调用固定入口适配、输入归一化SPI、固定匹配请求组装和意图匹配SPI；匹配SPI调用意图结果生成SPI并执行选中项的意图结果函数，最终把统一 `IntentResult` 交给后续节点。 |
| 结果函数边界 | MUST | Workflow意图组件内部执行意图结果函数只用于生成 `IntentAction`，不得在该函数中执行 Action 所描述的下游业务动作。 |
| Agent Card结果 | MUST | 默认 `DELEGATE_AGENT` Action必须能够被后续远端Agent调用节点消费并进入FEAT-004路径。 |
| 自定义结果 | MUST | `CUSTOM.data` 必须原样交给后续节点；Workflow开发者根据该自定义意图约定的数据结构配置分支、普通Tool调用、直接输出或其他业务处理。 |
| 未匹配结果 | MUST | 未配置fallback且没有匹配到意图项时，Workflow意图组件必须将统一未匹配结果交给后续节点。 |
| 场景差异 | MUST | Agent与Workflow使用各自固定入口适配和流程控制方式，但必须遵守相同的输入归一化、匹配、结果函数执行、`IntentResult`、`IntentAction` 和fallback语义。 |

## 3. 接口与入口要求

| 入口 | 输入 | 输出 |
|---|---|---|
| 初始化SPI | Agent Card及其初始化上下文、用户自定义`kwargs` | 可匹配意图项和可选fallback配置；每项关联匹配描述和意图结果函数。 |
| Agent提示词配置 | 可选用户自定义提示词 | 仅对Agent生效；未配置时使用默认提示词，已配置时使用用户提示词覆盖默认提示词。 |
| Agent Tool固定入口适配 | Tool schema中的`query`及可选业务输入 | 统一业务匹配输入；不接受调用方提供的同名Runtime Facts覆盖可信上下文。 |
| Workflow Node固定入口适配 | Node端口映射的`query`及可选业务输入 | 与Agent场景语义一致的统一业务匹配输入。 |
| 输入归一化SPI | 统一业务匹配输入 | 包含`normalizedQuery`并保留可用原始输入、已确认业务事实和有限上下文的归一化业务语义。 |
| Runtime Facts固定注入 | 当前执行环境能够提供且已授权的可信调用上下文 | 与业务输入分离的`tenantId`、`conversationId`、`taskId`、`traceId`等可选关联标识。 |
| 意图匹配SPI | 固定组装后的匹配请求、初始化后的意图项 | 调用意图结果生成SPI后返回`IntentResult`、未匹配结果或失败结果。 |
| 意图结果生成SPI | 本次结构化匹配上下文、选中意图项或fallback配置、初始化时保存的`kwargs` | 执行对应意图结果函数后产生统一`IntentResult`。 |
| 意图结果函数 | 本次结构化匹配上下文、当前意图项或fallback上下文、允许使用的初始化配置 | `IntentAction`；只生成或转换结果，不执行Action所表达的下游业务动作。 |
| Agent意图Tool | LLM提交的`query`及可选业务输入 | 返回给LLM的`IntentResult`。 |
| Workflow意图组件 | Workflow通过流程变量映射提供的`query`及可选业务输入 | 交给后续节点的`IntentResult`。 |

具体Java类型、序列化方式和异常映射由L2设计定义，但不得改变本特性规定的四个SPI、固定输入处理链、可信Runtime Facts边界、`IntentResult` / `IntentAction`字段语义和结果函数无业务副作用等外部契约。

### 3.1 输入组装与输出消费责任

| 场景 | 业务输入来源 | 入口适配职责 | 输出形态 | 下游消费方式 |
|---|---|---|---|---|
| Agent 意图 Tool | LLM 或上游 Agent 提供`query`及可选`originalQuery`、`confirmedFacts`、`contextSummary` | 固定适配业务输入；固定注入当前可获得的可信Runtime Facts；调用归一化SPI后组装匹配请求 | 标准Tool Result承载`IntentResult` | LLM先读取`status`与`action.type`，再按标准Action或开发者对`CUSTOM.data`的提示说明决定后续调用或答复 |
| Workflow 意图组件 | Workflow开发者通过流程变量映射提供`query`及可选业务输入 | 固定适配业务输入；固定注入当前可获得的可信Runtime Facts；调用归一化SPI后组装匹配请求 | Workflow Node结构化输出承载`IntentResult` | 后续节点先按`status`和`action.type`分支；标准Action按框架字段消费，`CUSTOM.data`按该自定义意图约定的数据结构消费 |

L2设计必须把本特性定义的业务输入字段、可信Runtime Facts、`IntentResult`和`IntentAction`映射为Tool schema、Node端口及Java类型。L2可以决定具体类型组织和序列化方式，不得删减必填字段、合并可信与非可信输入，或把标准Action重新退化为自由文本。

## 4. 场景与用户旅程

### 4.1 默认方式初始化意图套件

1. 开发者向初始化SPI提供一个或多个Agent Card及其初始化上下文，并通过`kwargs`提供若干组自定义`description + intent result function`。
2. 默认初始化SPI将每条Agent Card Skill转换为意图项，并关联返回Agent Card、Skill和稳定远端目标标识的默认结果函数。
3. 默认初始化SPI将每组自定义描述和结果函数转换为用户自定义意图项。
4. 开发者可以配置fallback意图项或fallback结果函数；fallback被单独保存，不加入可匹配意图项。
5. 存在可匹配意图项时，开发者为默认匹配实现配置匹配阈值；未提供有效阈值时初始化失败。
6. Agent意图套件选择默认提示词或开发者提供的覆盖提示词；Workflow不使用该提示词。
7. 初始化结果提供给意图匹配SPI使用；描述、结果函数或初始化配置发生变化时需要重新初始化。

### 4.2 自定义一句话并生成意图结果

1. 开发者配置`description = "查询账户余额"`，并提供一个意图结果函数。
2. 用户输入“帮我查一下余额”；Agent Tool或Workflow Node固定入口适配器形成`query`及可选业务输入，可信Runtime Facts由固定入口逻辑单独注入。
3. 输入归一化SPI生成`normalizedQuery`，固定组装逻辑形成匹配请求；意图匹配SPI选中“查询账户余额”意图项并调用意图结果生成SPI。
4. 意图结果生成SPI把本次结构化匹配上下文交给开发者提供的结果函数；该函数可以返回`IntentAction(type=CALL_TOOL, target=query-balance-api, data={intent: query_balance})`。
5. 结果生成SPI把该Action放入`IntentResult`并返回上游。结果函数本身不调用`query-balance-api`。
6. AgentLoop或Workflow根据`status`和`action.type`决定后续处理。

### 4.3 DeepAgent / ReAct Agent匹配并调用远端Agent

1. LLM将用户请求或已经生成的子任务作为`query`传给意图Tool；固定入口适配、输入归一化和可信Runtime Facts注入共同形成匹配请求。
2. 意图匹配SPI选中由某个Agent Card Skill初始化出的意图项，并调用意图结果生成SPI。
3. 默认Agent Card结果函数返回`DELEGATE_AGENT` Action，其中包含对应Agent Card、Skill、稳定远端目标标识和调用参数；结果生成SPI将其封装为`IntentResult`。
4. 意图Tool把`IntentResult`返回LLM；结果函数不调用远端Agent。
5. LLM根据`action.target`和`action.data`调用runtime注入的对应远端Agent工具，该调用由FEAT-004完成远端代理。
6. 远端调用正常完成时，FEAT-004将结果交回主体Agent；需要用户输入时按FEAT-008返回和续接中断。

### 4.4 Agent根据自定义结果调用普通Tool

1. 开发者已经把`query-balance-api`装配为主体Agent可调用的普通Tool，并通过默认或自定义提示词说明`CALL_TOOL` Action的消费方式。
2. 意图套件按4.2节得到`IntentAction(type=CALL_TOOL, target=query-balance-api)`。
3. 意图Tool把`IntentResult`返回LLM，控制权回到Agent loop。
4. LLM根据`action.target`和可选`action.data`调用主体Agent已经装配的`query-balance-api` Tool。
5. 普通Tool结果返回Agent，LLM完成本轮答复。Runtime继续管理主体Task，但意图结果函数和普通Tool调用是两个不同步骤。

### 4.5 Workflow消费用户自定义结果

1. Workflow通过流程变量映射向意图组件提供`query`及可选业务输入；固定入口适配、输入归一化和可信Runtime Facts注入共同形成匹配请求，意图匹配SPI选中用户自定义意图项。
2. 意图结果生成SPI执行对应结果函数，并把`CUSTOM`或其他标准Action放入`IntentResult`。
3. Workflow意图组件将`IntentResult`原样交给后续节点。
4. 后续节点先按`status`和`action.type`分支；标准Action按框架字段处理，`CUSTOM.data`按该自定义意图约定的数据结构处理。

### 4.6 未匹配与fallback

1. 意图匹配SPI正常完成但未选择出意图项。
2. 已配置fallback时，意图匹配SPI调用意图结果生成SPI，执行fallback结果函数并返回统一fallback结果；结果函数不执行下游业务动作。
3. 未配置fallback时，返回`status=UNMATCHED`的`IntentResult`。
4. 初始化、输入归一化、匹配或结果生成失败时返回失败，不进入fallback，也不执行其他结果函数。

### 4.7 用户交互后的意图跳变

1. Agent已经完成本次意图匹配，并根据结果进入后续Agent或普通Tool执行阶段。
2. 后续执行需要用户补充输入，并通过对应执行机制进入用户交互等待。
3. 用户续接输入表达新的意图，后续执行返回明确意图跳变语义，Agent上下文同时保留最新用户意图。
4. LLM根据生效提示词再次调用意图Tool，并以最新用户意图执行新的匹配和结果生成。
5. Workflow不使用该提示词机制；是否重新经过意图组件由Workflow显式流程定义。

## 5. 行为语义与边界

### 5.1 固定处理主线

```text
初始化 SPI
  -> 形成“匹配描述 + 意图结果函数”
  -> Agent Tool / Workflow Node固定入口适配业务输入
  -> 输入归一化 SPI生成归一化业务语义
  -> 固定入口注入可信Runtime Facts
  -> 固定组装逻辑形成匹配请求
  -> 意图匹配 SPI选择意图项或fallback
  -> 意图结果生成 SPI携带结构化匹配上下文执行对应结果函数
  -> 统一IntentResult封装IntentAction
  -> 返回AgentLoop / Workflow
  -> 上游自行决定后续业务动作
```

- 默认意图匹配先由Reranker对全部意图项排序，再通过初始化时配置的匹配阈值决定命中或未匹配；没有可匹配项时直接按未匹配处理。
- Agent Card Skill与用户自定义配置使用同一意图项抽象、意图匹配SPI和意图结果生成SPI。
- 每个可匹配意图项都必须关联一个意图结果函数；该函数负责结果生成，不负责用户意图执行。
- fallback也可以关联意图结果函数，但不属于可匹配意图项，只在正常未匹配后执行。
- 意图套件不负责把一段输入自动拆分成多个子任务；DeepAgent或业务流程可以先生成子任务，再分别进行匹配。
- 当前版本不支持动态更新意图描述。Agent Card Skill描述或用户自定义`description`变化时，必须重新初始化意图套件。
- 初始化SPI的`kwargs`属于Agent或Workflow初始化配置；Agent loop调用意图Tool时框架传入的`kwargs`不得覆盖该配置。

### 5.2 两种“执行”的边界

| 行为 | 是否属于020 | 是否属于下游意图执行 | Runtime Task语义 |
|---|---|---|---|
| 执行意图结果函数 | 是 | 否；只生成或转换`IntentAction` | 不创建、不推进Task，也不建立独立等待点 |
| 根据`DELEGATE_AGENT` Action调用远端Agent | 否；发生在020返回之后 | 是 | 由FEAT-004和runtime管理远端调用、Task关联及结果回填 |
| 根据`CALL_TOOL` Action调用普通Tool / API | 否；发生在020返回之后 | 是 | 使用主体Agent / Workflow既有Tool执行机制，不因020命中自动建立远端Agent子Task |
| 根据`DIRECT_RESPONSE`或`CUSTOM` Action答复用户 | 否；由AgentLoop或Workflow决定 | 是上游后续处理 | 使用主体Task的正常输出语义 |

### 5.3 意图结果函数与Action边界

- 文档和接口应优先使用“意图结果函数”或`intent result function`命名。若实现暂时保留`tool func`名称，必须明确它不是下游业务Tool，避免与普通Tool执行混淆。
- 意图结果函数可以根据本次结构化匹配上下文、选中意图上下文和初始化配置生成`IntentAction`；匹配后的结果生成阶段不得丢弃已经进入匹配请求的有效结构化事实。
- 标准Action的类型和基本字段语义由框架定义；`CUSTOM.data`由开发者定义，框架只保证透明传递。
- 框架不对所有`CUSTOM.data`强制统一type、version或schema字段。需要程序化消费时，自定义意图提供者必须与对应消费者约定稳定的数据结构。
- 意图结果函数不得通过网络、MCP、本地业务函数或其他方式执行下游业务动作；违反该约束的自定义实现不属于020承诺的兼容行为。
- 意图结果函数不得产生用户交互中断。需要交互的下游Agent或普通Tool必须在020返回后的真实执行阶段按对应契约处理。

### 5.4 后续执行边界

- 默认Agent Card结果函数返回标准远端Agent关联信息，AgentLoop或Workflow据此进入FEAT-004远端调用路径。
- 用户自定义结果函数可以返回用于提示下一步处理的`IntentAction`，但实际普通Tool调用必须由主体Agent的标准Tool机制或Workflow后续Tool节点完成。
- runtime负责远端Agent调用和主体Task生命周期，不解释`CUSTOM.data`，也不把每个普通Tool调用自动建模为远端Agent子Task。
- 020不负责注册、发现或治理用户自定义普通Tool。当前普通Tool由开发者静态装配到主体Agent或Workflow执行环境。

## 6. 需求归属与验收要求

- 本特性的Agent意图Tool、Workflow意图组件、初始化SPI、输入归一化SPI、意图匹配SPI、意图结果生成SPI和默认实现由`agent-solution/common/agent-core-ext-java`提供。
- runtime向意图套件提供Agent Card、Skill和稳定远端目标标识；远端Agent接入、代理调用、Task关联和正常结果回填遵守FEAT-004。
- 用户交互中断的客户端呈现、续接和Task状态语义遵守FEAT-008；020只在重新匹配时消费上游提交的最新语义。
- 必须验证四个SPI可以分别被替换，并保持“初始化意图项、归一化业务语义、匹配选择、结果函数执行”四段职责。
- 必须验证默认初始化能够处理多个Agent Card、一个Agent Card的多条Skill以及多组用户自定义`description + intent result function`。
- 必须验证Agent Card Skill和用户自定义意图项使用同一个意图匹配SPI和意图结果生成SPI。
- 必须验证Agent Tool和Workflow Node通过各自固定入口适配形成统一业务匹配输入，输入归一化SPI不承担Tool / Node参数映射。
- 必须验证默认输入归一化SPI按照固定规则处理`query`、`originalQuery`、`confirmedFacts`和`contextSummary`，不额外调用LLM；自定义归一化SPI能够替换默认实现。
- 必须验证`confirmedFacts`只表示上游已确认的业务事实，不被实现为Runtime自动抽取的隐式“槽位”。
- 必须验证当前可获得且已授权的`tenantId`、`conversationId`、`taskId`、`traceId`由固定入口逻辑注入，不接受用户、LLM或Workflow业务输入覆盖；每个字段只携带其标识语义，不携带完整租户资料、会话历史、Task对象或轨迹内容。
- 必须验证可信Runtime Facts与业务输入分离，默认不参与Reranker语义打分，也不会被无差别拼接进归一化文本。
- 必须验证输入归一化失败返回`FAILED / INPUT_NORMALIZATION`，且不会调用匹配SPI、结果生成SPI或fallback。
- 必须验证匹配SPI选择意图项后，把本次结构化匹配上下文传给意图结果生成SPI，并由结果生成SPI准确调用被选中项的意图结果函数；结构化事实不得退化为仅包含一句话的参数。
- 必须验证用户自定义结果函数返回`IntentAction`，结果生成SPI将其放入统一`IntentResult`。
- 必须验证`IntentResult`按照本特性定义的字段区分`MATCHED`、`FALLBACK`、`UNMATCHED`和`FAILED`，并正确关联选中意图、Action或失败信息。
- 必须验证处理失败时能够通过`failure`区分输入归一化、匹配、结果生成和结果函数等阶段；L2定义具体Java类型、失败码和异常映射。
- 必须验证意图结果函数只生成或转换结果，不调用远端Agent、普通业务Tool / API，不修改业务状态，不创建或推进Task，也不产生用户交互中断。
- 必须验证默认Agent Card结果函数返回正确的`DELEGATE_AGENT` Action，其中包含Agent Card、Skill、稳定远端目标标识和调用参数，但不直接调用远端Agent。
- 必须验证AgentLoop收到`DELEGATE_AGENT` Action后能够调用runtime注入的正确远端Agent工具并进入FEAT-004路径。
- 必须验证`CALL_TOOL`、`DIRECT_RESPONSE`和`CUSTOM` Action能够分别被AgentLoop或Workflow按本特性语义消费；`CUSTOM.data`由开发者与对应消费者约定，框架透明传递且不要求每次结果携带统一schema。
- 必须验证包含`target=query-balance-api`的`CALL_TOOL` Action由AgentLoop或Workflow后续执行路径消费，结果函数本身不调用该Tool。
- 必须验证fallback不参与匹配，仅在正常未匹配后调用其结果函数；初始化失败、输入归一化失败、匹配失败和结果生成失败均不会触发fallback。
- 必须验证默认匹配只有在排序首项达到配置阈值时才命中，低于阈值以及没有可匹配项时均进入fallback或默认未匹配路径。
- 必须验证默认匹配以`normalizedQuery`为依据，并正确处理默认归一化已纳入的`confirmedFacts`和`contextSummary`。
- 必须验证默认提示词和用户覆盖提示词能够指导LLM调用意图Tool并消费标准Action和`CUSTOM.data`；提示词注入对Workflow不生效。
- 必须验证下游Agent和普通Tool正常完成后不重新执行意图；Agent在用户交互续接后收到明确意图跳变语义时，使用最新用户意图重新调用意图Tool。
- 必须验证初始化完成后不能动态更新Agent Card Skill描述或用户自定义`description`，重新初始化后新描述才生效。
- 必须验证初始化`kwargs`在Agent或Workflow初始化时固定，并且不被Agent loop的工具调用`kwargs`覆盖。

## 7. 关联文档

- `version-scope/FEAT-008-user-interaction-interrupt-and-response.md`
- `version-scope/FEAT-004-task-driven-remote-agent-communication.md`
- `architecture/L2-Low-Level-Design/agent-core/Feat-Func-020-agent-intent-recognition-and-downstream-task-matching.md`

---
scope: v0730
module: agent-runtime
feature_type: functional
feature_id: FEAT-008
status: active
updated: 2026-07-31
---

# 意图驱动工具调用的中断与响应

## 1. 特性定位

本特性负责把 runtime 已接入的 Agent Card 及其远端目标关联提供给 FEAT-020；当 Agent 在完成意图匹配后调用的远端 Agent 工具或本地工具需要用户补充输入时，本特性负责通过智能体服务调用响应返回中断，并将用户输入续接回当前工具调用。

FEAT-020 负责意图初始化、匹配、结果生成、提示词和重新匹配；FEAT-004 负责远端 Agent 接入、代理调用和正常结果回填。本特性不重复定义上述能力，不判断用户意图，也不负责用户自定义意图项、fallback 或本地工具的实际执行。

## 2. 当前版本能力要求

### 2.1 Agent Card 与远端目标关联提供

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| Agent Card 提供 | MUST | runtime 必须将当前可调用远端 Agent 的 Agent Card 及其远端目标关联信息提供给 FEAT-020 意图套件初始化。 |
| Skill 完整提供 | MUST | runtime 提供 Agent Card 时必须保留其中的全部 Skill、所属 Agent Card 和远端目标关联信息，使 FEAT-020 能够将每条 Skill 初始化为独立意图项。 |
| 远端目标关联 | MUST | runtime 必须为每个 Agent Card 提供稳定的远端目标标识，使 FEAT-020 返回 Agent Card Skill 结果后能够确定对应的远端调用目标。 |
| 远端工具识别 | MUST | runtime 提供的关联信息必须使 Agent Card Skill 结果能够准确识别 runtime 注入的对应远端 Agent 工具。 |
| 多 Skill 关联 | MUST | 同一 Agent Card 包含多条 Skill 时，每条 Skill 分别参与匹配；任一 Skill 命中后均关联到该 Agent Card 对应的同一个远端 Agent。 |
| 初始化周期固定 | MUST | runtime 提供给意图套件的 Agent Card Skill 及其远端目标关联在一次初始化周期内保持不变。Agent Card 或 Skill 发生变化时，必须重新初始化意图套件后才参与匹配。 |

### 2.2 Agent 场景工具中断响应

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| 用户交互中断返回 | MUST | 远端 Agent 工具或本地工具需要用户补充输入时，runtime 必须中断当前执行，并通过当前智能体服务调用响应向客户端返回 A2A `input-required` 状态和等待用户输入的信息，不得将本次调用标记为正常完成。 |
| 用户输入续接 | MUST | 客户端针对当前中断提交用户输入后，runtime 必须将输入交回对应的等待中工具调用进行续接处理。 |
| 续接结果返回 | MUST | 工具调用在续接后正常完成、再次请求用户输入、返回失败或返回意图跳变语义时，runtime 必须将对应结果交回当前 Agent。 |
| 意图跳变透传 | MUST | 完成意图匹配后调用的下游工具在中断续接后返回意图跳变语义，且 Agent 上下文中已携带最新用户意图时，runtime 必须将工具结果和上下文交回 LLM，使 LLM 按 FEAT-020 再次调用意图工具，而不是结束本轮会话。runtime 不得自行判断用户是否改变意图。 |

### 2.3 Workflow 场景远端调用中断响应

| 能力 | 要求级别 | 需求描述 |
|---|---|---|
| Workflow 中断响应 | MUST | 远端 Agent 调用需要用户补充输入时，runtime 必须通过当前 Workflow 服务调用响应向客户端返回 A2A `input-required` 状态和等待用户输入的信息，并在客户端提交输入后续接对应的远端调用。 |
| Workflow 重匹配边界 | MUST | Agent 场景中基于提示词处理意图跳变并重新调用意图工具的机制不适用于 Workflow。runtime 不得在 Workflow 远端调用完成或中断续接后自动执行 FEAT-020 意图匹配。 |

## 3. 交互边界

本特性复用现有智能体服务入口和 A2A 远端调用能力，不新增客户端服务入口。

| 交互边界 | 输入 | 输出 |
|---|---|---|
| runtime 向 FEAT-020 提供远端能力 | 当前可调用远端 Agent 的 Agent Card | Agent Card、其中的全部 Skill，以及对应的稳定远端目标标识。 |
| FEAT-020 与 FEAT-004 衔接 | Agent Card、命中的 Skill 及远端目标标识 | 由 FEAT-004 处理的对应远端 Agent 调用。 |
| Agent 工具中断响应 | 远端 Agent 工具或本地工具的等待用户输入结果 | 通过当前智能体服务调用响应返回给客户端的 A2A `input-required` 状态和等待信息。 |
| 用户输入续接 | 客户端针对当前中断提交的用户输入 | 交回对应的等待中工具调用进行续接处理。 |

具体关联信息、调用参数和中断续接形式由 L2 设计定义。

## 4. 场景与用户旅程

### 4.1 初始化远端 Agent 意图项

1. runtime 将 FEAT-004 已接入的一个或多个远端 Agent 的 Agent Card、全部 Skill 和稳定远端目标标识提供给 FEAT-020 初始化 SPI。
2. FEAT-020 将每条 Agent Card Skill 初始化为独立意图项，并保留 Agent Card、Skill 和稳定远端目标标识。
3. 同一 Agent Card 中的多条 Skill 分别参与匹配，但均关联到该 Agent Card 对应的远端 Agent。
4. Agent Card 或 Skill 发生变化时，当前意图项不动态变化；重新初始化后才使用新的内容和关联。

### 4.2 Agent 在工具调用中补充原任务信息

1. Agent 已完成本次意图匹配，并根据意图结果进入后续 loop，调用远端 Agent 工具或其他本地工具。
2. 该下游工具需要用户补充信息，runtime 中断当前执行，并通过当前智能体服务调用响应将 A2A `input-required` 状态和等待信息返回客户端。
3. 用户针对原任务提交所需信息，客户端使用当前中断的续接入口提交该输入。
4. runtime 将用户输入交回等待中的工具调用，工具调用使用补充信息继续原任务。
5. 工具调用正常完成后，runtime 将工具调用结果交回 LLM。
6. LLM 完成本轮答复，本轮会话结束，不再次执行意图匹配。

### 4.3 Agent 在工具调用中发生意图跳变

1. Agent 已完成本次意图匹配，并根据意图结果进入后续 loop，调用远端 Agent 工具或其他本地工具。
2. 该下游工具需要用户补充信息，runtime 通过当前智能体服务调用响应向客户端返回 A2A `input-required` 状态和等待信息。
3. 用户提交新的输入，并表达了不同于原任务的最新意图。
4. runtime 将输入交回等待中的工具调用；该工具调用结束并返回意图跳变语义，Agent 上下文中同时携带可供后续匹配使用的最新用户意图。
5. runtime 将工具结果和最新上下文交回 LLM，不自行判断或重新匹配意图。
6. LLM 按 FEAT-020 的生效提示词再次调用意图工具，传入最新意图并进入新的意图处理循环，而不是结束本轮会话。

### 4.4 Workflow 远端调用需要用户输入

1. Workflow 调用的远端 Agent 需要用户补充信息，runtime 通过当前 Workflow 服务调用响应向客户端返回 A2A `input-required` 状态和等待信息。
2. 客户端针对当前中断提交用户输入，runtime 将输入交回对应远端 Agent 调用。
3. 远端调用后续结果按 Workflow 流程处理；runtime 不注入 Agent 提示词，也不自动重新调用 FEAT-020 意图组件。

## 5. 行为语义与边界

- FEAT-020 负责选择 Agent Card Skill；FEAT-004 负责按选择结果调用对应远端 Agent。本特性只提供二者衔接所需的 Agent Card 与远端目标关联信息。
- runtime 不负责意图初始化、匹配、结果生成、fallback、提示词或意图重新匹配。
- runtime 不分析客户端续接输入是否表达了新意图；意图跳变发生在意图匹配已经完成后的下游工具调用中，必须由被续接的远端 Agent 工具或本地工具返回，并在 Agent 上下文中携带最新用户意图。
- 下游工具正常完成后不得自动重新执行意图匹配。只有下游工具在中断续接后返回意图跳变语义时，Agent 才按 FEAT-020 提示词重新调用意图工具，而不是结束本轮会话。
- 本地工具的选择和实际执行不属于本特性；本地工具产生的用户交互中断、用户输入续接和结果回传属于本特性的 runtime 响应链路。FEAT-020 在意图工具内部调用的同步结果工具函数不属于此处的可中断本地工具。
- Agent 的提示词和意图跳变重匹配机制不适用于 Workflow；Workflow 只在流程经过意图组件时执行匹配。
- 当前版本不支持动态更新意图描述。Agent Card Skill 变化后，必须重新初始化意图套件。

## 6. 需求归属与验收要求

- 本特性的 Agent Card 与稳定远端目标标识提供、用户交互中断响应和续接属于 runtime 能力；远端 Agent 接入、代理调用和正常结果回填属于 FEAT-004。
- 必须验证 runtime 能够向 FEAT-020 提供多个 Agent Card，并正确处理一个 Agent Card 中的多条 Skill。
- 必须验证 runtime 为每个 Agent Card 提供稳定远端目标标识，并使每条 Skill 的意图结果能够识别正确的远端 Agent 工具。
- 必须分别验证远端 Agent 工具和本地工具需要用户输入时，中断信息通过当前智能体服务调用响应返回客户端，客户端输入能够续接对应工具调用。
- 必须分别验证用户补充原任务信息后正常完成，以及完成意图匹配后的下游工具在中断续接后返回意图跳变语义时，Agent 上下文携带最新用户意图并由 LLM 按 FEAT-020 重新调用意图工具。
- 必须验证 runtime 不判断用户意图，也不在未收到明确意图跳变语义时触发重新匹配。
- 必须验证 Workflow 的用户交互中断能够返回客户端并续接远端调用，且不会触发 Agent 提示词或自动意图重匹配。
- 必须验证 Agent Card 或 Skill 变化不会动态修改已初始化意图项，重新初始化后新内容才生效。

## 7. 关联文档

- `version-scope/FEAT-001-standardized-agent-service-entrypoint.md`
- `version-scope/FEAT-004-task-driven-remote-agent-communication.md`
- `version-scope/FEAT-020-agent-intent-matching-and-action-routing.md`

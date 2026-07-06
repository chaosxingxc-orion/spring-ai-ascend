# EDPAgent 重构方案与当前实现差异报告

> 生成时间：2026-06-20  
> 对比对象：`agent-store/edp-agent-trae/docs/EDAgent简化重构方案.md` 与 `agent-store/edp-agent-trae/spike` 当前实现  
> 结论状态：本报告仅用于差异审视，未修改代码。

## 1. 总体结论

当前 `edp-agent-trae/spike` 实现仍处于 P17/P6 穿刺验证阶段，已经具备：

- DeepAgent / OpenJiuwen 直接集成。
- 5 个 EDPAgent 业务工具名。
- 9 个 EDPAgent 自建 Rail 类。
- `ask_user` 基础中断能力。
- P17 工具注册测试和 P6 AskUser 测试基础。

但与最新重构方案相比，当前实现仍存在明显差异：

1. 工具尚未拆分为 5 个独立 Tool 类。
2. `call_mcp`、`call_versatile`、`cancel_task` 尚未完全落实“薄工具 + Rail 承载业务逻辑”原则。
3. `ToolDataChannel`、`utterance/`、`stream/` 目录尚未实现。
4. `ask_user`、`lite_todo_write` 的可选降级策略尚未实现。
5. 多数 Rail 当前仍是日志/占位实现，未达到方案中的业务闭环职责。
6. P6 AskUser 真实模型链路仍可能不进入 `TASK_STATE_INPUT_REQUIRED`，目前依赖 Mock 兜底验证。

---

## 2. 已匹配内容

### 2.1 DeepAgent 直接集成已匹配

当前运行时通过 `OpenJiuwenAgentRuntimeHandler` 接入 DeepAgent：

- [EdpaRuntimeHandler.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/handler/EdpaRuntimeHandler.java)

关键实现包括：

- `EdpaRuntimeHandler extends OpenJiuwenAgentRuntimeHandler`
- `HarnessFactory.createDeepAgent(...)`
- `createOpenJiuwenAgent(...)` 返回 `deepAgent.getAgent()`

结论：符合“不重建 DeepAgentPlus，直接使用 DeepAgent/OpenJiuwen”的方案原则。

---

### 2.2 5 个业务工具名已匹配

当前工具名集中定义在：

- [EdpaBusinessTools.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/tools/EdpaBusinessTools.java#L28-L52)

已包含：

```text
lite_todo_write
call_mcp
call_versatile
ask_user
cancel_task
```

结论：工具数量和工具名称已匹配方案。

---

### 2.3 9 个 Rail 类已存在

当前 `rail/` 目录已有以下 9 个类：

```text
CancelRail
LiteTodoRail
IterationLimitRail
ExecutionLimitRail
McpInterruptRail
VersatileInterruptRail
AskUserTemplateRail
MemoryRail
LogRail
```

注册位置：

- [EdpaAgentEnhancer.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/enhancer/EdpaAgentEnhancer.java#L114-L139)

结论：Rail 数量已匹配方案中的“9 个 EDPAgent 自建 Rail”。

---

### 2.4 未自建 `read_file` / `execute_cmd`

当前源码中未发现 EDPAgent 自定义 `read_file` / `execute_cmd` 工具实现。

`execute_cmd` 仅出现在配置限制中：

- [edp-config.yaml](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/resources/edp-config.yaml#L31-L37)

结论：符合方案中“直接复用 DeepAgent 系统工具，不再自建”的原则。

---

### 2.5 P17 / P6 测试已有基础覆盖

P17 工具注册测试：

- [P17BusinessToolRegistrationSpikeTest.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/test/java/com/huawei/ascend/edp/spike/P17BusinessToolRegistrationSpikeTest.java)

P6 AskUser 中断/恢复测试：

- [P6AskUserInterruptResumeSpikeTest.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/test/java/com/huawei/ascend/edp/spike/P6AskUserInterruptResumeSpikeTest.java)

结论：已有测试基础，但后续结构和 schema 对齐后需要同步更新断言。

---

## 3. 主要差异点

### 3.1 工具尚未拆分为 5 个独立类

方案要求：

- [EDAgent简化重构方案.md](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/docs/EDAgent简化重构方案.md#L1404-L1410)

```text
tools/
  LiteTodoWriteTool.java
  CallVersatileTool.java
  CallMcpTool.java
  EnhancedAskUserTool.java
  CancelTaskTool.java
```

当前实际只有：

- [EdpaBusinessTools.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/tools/EdpaBusinessTools.java)

```text
tools/
  EdpaBusinessTools.java
```

影响：

- 工具实现集中在一个聚合类中。
- 不利于按工具独立测试和独立演进。
- 与新 Skill 中固化的包结构标准不完全一致。

建议：

```text
tools/
  EdpaBusinessTools.java      # 聚合入口
  LiteTodoWriteTool.java
  CallMcpTool.java
  CallVersatileTool.java
  EnhancedAskUserTool.java
  CancelTaskTool.java
  ToolSchemas.java            # 可选，Schema 工具类
```

---

### 3.2 `lite_todo_write` 入参仍是 `items`，方案要求 `todos`

方案要求：

- [EDAgent简化重构方案.md](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/docs/EDAgent简化重构方案.md#L1585-L1585)

```text
todos: [{step_id, status}]
```

当前实现：

- [EdpaBusinessTools.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/tools/EdpaBusinessTools.java#L63-L69)
- [EdpaBusinessTools.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/tools/EdpaBusinessTools.java#L153-L162)

当前使用：

```text
items
```

测试中也使用 `items`：

- [P17BusinessToolRegistrationSpikeTest.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/test/java/com/huawei/ascend/edp/spike/P17BusinessToolRegistrationSpikeTest.java#L82-L82)

影响：

- 与 Python EDPAgent 和方案不同构。
- 后续模型按 `todos` 调用时可能与 Java 工具 schema 不一致。

建议：

- `items` 改为 `todos`。
- P17 测试同步更新。

---

### 3.3 `call_mcp` 入参仍是 `script/input`，方案要求 `script_command/script_params`

方案要求：

- [EDAgent简化重构方案.md](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/docs/EDAgent简化重构方案.md#L1581-L1581)

```text
script_command
script_params
```

当前实现：

- [EdpaBusinessTools.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/tools/EdpaBusinessTools.java#L75-L80)

当前使用：

```text
script
input
```

影响：

- 与 Python `call_mcp` 工具入参不同构。
- 后续 `McpInterruptRail` 按方案实现时需要再次迁移。

建议：

```text
script -> script_command
input  -> script_params
```

---

### 3.4 `call_versatile` 入参仍是 `workflow/payload`，方案要求 6 个真实参数

方案要求：

- [EDAgent简化重构方案.md](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/docs/EDAgent简化重构方案.md#L1582-L1582)

```text
query_description
query_intent
query_response_analysis_scripts
response_template_keys
notice_context
input_key
```

当前实现：

- [EdpaBusinessTools.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/tools/EdpaBusinessTools.java#L84-L89)

当前使用：

```text
workflow
payload
```

影响：

- 与 Python EDPAgent 不同构。
- `VersatileInterruptRail` 无法读取 `input_key` / `notice_context` / `response_template_keys` 等关键参数。

建议：

- 将 schema 调整为方案参数。
- Tool 返回只保留 `delegate intent`。
- 复杂逻辑放入 `VersatileInterruptRail`。

---

### 3.5 `ask_user` schema 缺少方案要求的话术参数

方案要求：

- [EDAgent简化重构方案.md](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/docs/EDAgent简化重构方案.md#L1583-L1583)

```text
question
response_template_keys
response_template_status
response_template_vars
```

当前实现：

- [EdpaBusinessTools.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/tools/EdpaBusinessTools.java#L92-L96)

当前使用：

```text
question
missing_fields
```

影响：

- 不能承接 Python EDPAgent 的话术模板闭环。
- P6 当前只能证明基础 interrupt，不能证明话术参数和恢复模板同构。

建议：

- 保留 `question`。
- 增加：
  - `response_template_keys`
  - `response_template_status`
  - `response_template_vars`
- `missing_fields` 可作为 spike 扩展字段保留，但不是 Python 同构核心字段。

---

### 3.6 三个复杂工具尚未完全落实“薄工具 + Rail”原则

方案确认原则：

- [EDAgent简化重构方案.md](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/docs/EDAgent简化重构方案.md#L1591-L1595)

```text
Tool 层只保留 Schema + intent
业务逻辑放 Rail
```

当前状态：

- `call_mcp` 返回 `pending_sandbox`：
  - [EdpaBusinessTools.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/tools/EdpaBusinessTools.java#L80-L80)
- `call_versatile` 返回 `pending_delegate`：
  - [EdpaBusinessTools.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/tools/EdpaBusinessTools.java#L89-L89)
- `cancel_task` 返回 `cancel_requested`：
  - [EdpaBusinessTools.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/tools/EdpaBusinessTools.java#L101-L101)

其中 `cancel_task` 已被 `CancelRail` 在工具调用前强制结束：

- [CancelRail.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/rail/CancelRail.java#L52-L68)

影响：

- MCP/VA 尚未形成方案中的 Rail 业务闭环。
- Tool 与 Rail 的职责边界仍需进一步拆清。

建议：

- Tool 只返回 intent payload。
- `McpInterruptRail` 承担 MCP 真实执行/状态写入。
- `VersatileInterruptRail` 承担 VA 委托和级联恢复。

---

### 3.7 `ToolDataChannel` 尚未实现

方案要求：

- [EDAgent简化重构方案.md](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/docs/EDAgent简化重构方案.md#L1600-L1604)

```text
channel/ToolDataChannel.java
```

当前不存在：

```text
spike/src/main/java/com/huawei/ascend/edp/channel/
```

影响：

- MCP → VA 的数据传递还没有承载对象。
- `call_mcp` 与 `call_versatile` 目前只能各自占位。

建议：

- 新增 `com.huawei.ascend.edp.channel.ToolDataChannel`。
- 初期只实现最小方法：`put/get/getAll/getKeys/remove/clear`。

---

### 3.8 `utterance/` 目录尚未实现

方案要求：

- [EDAgent简化重构方案.md](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/docs/EDAgent简化重构方案.md#L1601-L1603)

```text
utterance/ScriptsConfigManager
utterance/ResponseTemplateManager
```

当前不存在：

```text
spike/src/main/java/com/huawei/ascend/edp/utterance/
```

当前 `AskUserTemplateRail` 只是记录 `ScriptsConfig.md` 路径，并返回固定话术：

- [AskUserTemplateRail.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/rail/AskUserTemplateRail.java#L87-L97)

影响：

- `ScriptsConfig.md` 未被真正解析。
- `response_template_keys` / `response_template_vars` 暂无法工作。
- P6 只能走默认追问，不能做到 Python 同构话术。

建议：

```text
utterance/ScriptsConfigManager.java
utterance/ResponseTemplateManager.java
```

---

### 3.9 `stream/FixedScriptFeeder` 尚未实现

方案要求：

- [EDAgent简化重构方案.md](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/docs/EDAgent简化重构方案.md#L1603-L1604)

```text
stream/FixedScriptFeeder.java
```

当前不存在：

```text
spike/src/main/java/com/huawei/ascend/edp/stream/
```

影响：

- `think_chunk.fixed_script` 配置未被 Java 侧真正消费。
- 当前流式展示仍更多依赖模型输出和 runtime adapter。

建议：

- P6 可暂不做。
- 建议放到 P15/P16 或后续流式同构穿刺中处理。

---

### 3.10 `MemoryRail` 当前始终注册，方案要求按配置可选

方案要求：

- [EDAgent简化重构方案.md](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/docs/EDAgent简化重构方案.md#L1619-L1637)

```text
MemoryRail 可选
```

当前始终注册：

- [EdpaAgentEnhancer.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/enhancer/EdpaAgentEnhancer.java#L135-L136)

虽然 `MemoryRail` 内部会判断 `memory.enabled`：

- [MemoryRail.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/rail/MemoryRail.java#L50-L59)

但注册数量仍固定为 9：

- [EdpaAgentEnhancer.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/enhancer/EdpaAgentEnhancer.java#L184-L186)

建议：

- `memory.enabled=true` 时注册 `MemoryRail`。
- 否则不注册。
- `countRegisteredRails` 根据配置计算。

---

### 3.11 `ExecutionLimitRail` 缺少事件补齐和内部工具事件抑制

方案要求：

- [EDAgent简化重构方案.md](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/docs/EDAgent简化重构方案.md#L1648-L1648)

```text
tool_start / tool_end / todo_start / todo_end 事件补齐 + skill 追踪；抑制内部工具事件
```

当前只做工具调用次数限制：

- [ExecutionLimitRail.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/rail/ExecutionLimitRail.java#L53-L69)

影响：

- UI 流式事件与 Python 版不同构。
- `call_versatile` 中断/恢复的 todo 事件顺序尚未实现。

建议：

- 后续补充事件发射。
- P6 阶段可暂不作为首要阻塞项。

---

### 3.12 `McpInterruptRail` 当前只是观测日志

方案要求：

- [EDAgent简化重构方案.md](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/docs/EDAgent简化重构方案.md#L1645-L1645)

```text
沙箱执行脚本，注入 mcp_required_params，写入 mcp_products_data / mcp_to_versatile_information ...
```

当前只打日志：

- [McpInterruptRail.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/rail/McpInterruptRail.java#L53-L80)

影响：

- P9 MCP 先行架构尚未实现。
- `ToolDataChannel` 即使新增，也暂时没有真实写入点。

建议：

- P6 完成后再进入 P9 时实现。

---

### 3.13 `VersatileInterruptRail` 当前是远程调用占位，不是完整级联恢复

方案要求：

- [EDAgent简化重构方案.md](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/docs/EDAgent简化重构方案.md#L1646-L1646)

```text
pending_delegate / pending_tool_context / cascade_result / input_key / ui_notice / todo_start / todo_end
```

当前实现：

- [VersatileInterruptRail.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/main/java/com/huawei/ascend/edp/rail/VersatileInterruptRail.java#L57-L80)

当前只做：

- `_skip_tool`
- `runtime.remote.*`
- 设置简单 `toolResult`

影响：

- VA 级联恢复与 Python 同构差距较大。
- `call_versatile` 仍不能消费 MCP 数据。

建议：

- P9 / VA 级联穿刺时实现。
- 不建议混入 P6 首轮修改。

---

### 3.14 P6 AskUser 仍缺少真实模型稳定中断的确定性前置能力

当前 P6 测试使用“真实模型优先，失败 Mock 兜底”：

- [P6AskUserInterruptResumeSpikeTest.java](file:///d:/JW/spring-ai-ascend/agent-store/edp-agent-trae/spike/src/test/java/com/huawei/ascend/edp/spike/P6AskUserInterruptResumeSpikeTest.java#L139-L229)

问题：

- 真实模型可能不调用 `ask_user`。
- 真实链路可能返回 `TASK_STATE_COMPLETED` 而非 `TASK_STATE_INPUT_REQUIRED`。

影响：

- P6 当前不能宣称真实链路稳定闭环。
- 需要确定性缺参拦截或更强的 AskUser Rail / Runtime 前置判断。

建议：

- P6 编码时优先补确定性缺参中断策略。
- 测试继续保留“真实优先，Mock 兜底，并记录报告”的策略。

---

## 4. 建议修改优先级

### 4.1 优先级 A：结构和 Schema 对齐

建议优先处理：

1. 拆分 `tools/EdpaBusinessTools.java`。
2. 新增 5 个独立工具类。
3. 保留 `EdpaBusinessTools` 作为聚合入口。
4. 更新工具 schema：
   - `lite_todo_write`: `items` → `todos`
   - `call_mcp`: `script/input` → `script_command/script_params`
   - `call_versatile`: `workflow/payload` → 方案 6 个参数
   - `ask_user`: 增加 `response_template_*`
5. 更新 P17 / P6 测试。

---

### 4.2 优先级 B：P6 AskUser 闭环

建议 P6 重点处理：

1. `ask_user` schema 和 interrupt payload 对齐方案。
2. `AskUserTemplateRail` 读取 `ScriptsConfig.md` 中的 `interrupt_start`。
3. 增加真实模型不调用工具时的确定性缺参中断策略。
4. 保持真实模型优先、Mock 兜底的测试报告策略。

---

### 4.3 优先级 C：P9 / VA 级联

建议 P9 或 VA 穿刺阶段处理：

1. 新增 `channel/ToolDataChannel.java`。
2. 实现 `McpInterruptRail` 写入 channel。
3. 实现 `VersatileInterruptRail` 读取 channel。
4. 实现 `cascade_result` / `pending_delegate` / `pending_tool_context`。

---

### 4.4 优先级 D：流式体验同构

建议后续处理：

1. 新增 `utterance/`。
2. 新增 `stream/FixedScriptFeeder`。
3. 补齐 `ExecutionLimitRail` 的事件发射和内部工具抑制。

---

## 5. 待确认修改方案

### 方案 1：结构和 Schema 对齐优先

修改内容：

- 拆分 5 个工具类。
- 保留 `EdpaBusinessTools` 聚合入口。
- 更新工具 schema 到方案字段。
- 更新 P17/P6 测试。

不做：

- `ToolDataChannel`
- `ScriptsConfigManager`
- `FixedScriptFeeder`
- 真实 MCP/VA 级联

适用场景：先降低结构差异和工具 schema 差异。

---

### 方案 2：结构 + P6 AskUser 优先

包含方案 1，并额外：

- `EnhancedAskUserTool` 支持 `response_template_*`。
- `AskUserTemplateRail` 读取 `ScriptsConfig.md` 的 `interrupt_start`。
- 增加确定性缺参中断策略，使真实模型链路更稳定返回 `TASK_STATE_INPUT_REQUIRED`。

适用场景：当前继续推进 P6 AskUser 中断/恢复闭环。

---

### 方案 3：结构 + Channel 基础

包含方案 1，并额外：

- 新增 `channel/ToolDataChannel.java`。
- `call_mcp` / `call_versatile` Rail 接入最小 channel 读写。
- 不接真实 MCP/VA。

适用场景：为 P9 MCP 先行架构提前打基础。

---

## 6. 建议结论

建议下一步优先选择 **方案 2：结构 + P6 AskUser 优先**。

原因：

1. 可以先把工具结构和 schema 对齐方案。
2. 可以直接服务当前正在推进的 P6 AskUser 中断/恢复闭环。
3. 避免过早引入 MCP/VA 级联复杂度。
4. 可以继续沿用现有 P6/P17 测试，并逐步把 Mock 兜底推进为真实链路通过。

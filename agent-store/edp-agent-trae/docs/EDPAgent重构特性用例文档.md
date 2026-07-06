# EDPAgent 重构特性用例说明文档

## 文档元数据

- 特性编号：FEAT-2026-001~022
- SA 负责人：[姓名]
- 目标版本：v1.0.0 (2026-06-30)
- 目标开源项目：agent-store / edp-agent
- 特性面向对象：开发者 / 业务分析师
- 文档状态：草稿

---

## 1. 特性概述

- **特性描述**：基于 Java DeepAgent 框架对 Python 版 EDPAgent 进行全面重构，输出 `DeepAgentPlus` 类。重构涵盖类架构继承、配置解析、业务工具/Rail 体系、话术系统、Skill 管理、流事件、记忆系统、开发模式等 22 个特性域，确保 Python EDPAgent 全部能力零丢失继承，同时新增 DeepAgent 46+ 工具、26+ Rail、TaskLoop 等高级能力。
- **业务价值**：
  1. 统一技术栈至 Java，降低维护成本与人员复用门槛
  2. 支持装配式（零代码配置驱动）与挂载式（继承+钩子）双模式开发，覆盖业务分析师与开发者两类角色
  3. 配置按需精简、章节独立可选，解决 AgentRule.md 配置项过多且必填的痛点
  4. 话术配置集中管理，解决话术模板分散问题
  5. 工具/Rail 扩展机制低侵入，一行注入即可增删，解决开放性不足问题

---

## 2. 核心参与者与触发条件

- **参与角色**：
  - 业务分析师：编写 AgentRule.md + ScriptsConfig.md + Skills，使用装配式开发模式
  - Java 开发者：继承 DeepAgentPlus，覆写钩子方法，使用挂载式开发模式
  - 系统网关（a2a_service）：接收外部请求，调用 EDPAgent 的 stream/invoke 入口
  - VersatileAdapter：处理 VA 工作流级联中断，返回编排结果
  - 终端用户：与 Agent 交互，触发 ask_user 中断、cancel_task 等操作

- **触发条件**：
  - 用户通过 A2A 协议发送消息请求，触发 Agent 推理循环
  - 业务分析师编写/修改 AgentRule.md 配置文件，触发配置解析与 Agent 重建
  - 开发者继承 DeepAgentPlus 并覆写钩子，触发自定义逻辑注入
  - Agent 执行过程中工具调用被 Rail 拦截，触发中断/恢复流程
  - 系统达到执行限制阈值，触发强制完成

---

## 3. 标准流程用例 (Happy Path)

### 用例 3.1：DeepAgentPlus 类架构继承与初始化

- **前置条件**：DeepAgent 框架已部署，openjiuwen SDK 可用
- **输入参数**：DeepAgentPlusConfig 配置对象（含 AgentRule.md 路径、Skill 目录、话术配置路径等）
- **执行步骤**：
  1. 创建 `DeepAgentPlus` 实例，传入 `DeepAgentPlusConfig`
  2. `DeepAgentPlus` 继承 `DeepAgent`，复用 ReAct 推理引擎、TaskLoop、46+ 内置工具、26+ 内置 Rail
  3. `initialize()` 方法执行初始化：解析 AgentRule.md → 注册业务工具 → 注册业务 Rail → 加载 Skill → 加载话术配置
  4. 内置 5 个业务工具（LiteTodoWriteTool / CallVersatileTool / CallMcpTool / EnhancedAskUserTool / CancelTaskTool）自动注册
  5. 内置 5 个业务 Rail（VersatileInterruptRail / McpInterruptRail / AskUserTemplateRail / CancelRail / ExecutionLimitRail）按优先级顺序注册
  6. AgentRule.md 中配置的参数映射到 DeepAgentConfig 对应字段
- **预期输出/结果**：DeepAgentPlus 实例初始化完成，具备 DeepAgent 全部能力 + EDPAgent 业务能力，可接收 stream/invoke 调用

### 用例 3.2：配置文件解析（AgentRule.md Parser）

- **前置条件**：AgentRule.md 文件已编写，符合 YAML Frontmatter + Markdown Body 格式
- **输入参数**：AgentRule.md 文件路径
- **执行步骤**：
  1. `AgentRuleParser.parse(file)` 读取 AgentRule.md 文件
  2. 分离 YAML Frontmatter 与 Markdown Body
  3. YAML Frontmatter 解析为 `AgentRuleSchema` POJO（含 scope / capabilities / limits / skills / utterances / memory / completion 等章节）
  4. Markdown Body 作为 System Prompt 基础文本保留
  5. `AgentRuleToConfigMapper` 将 AgentRuleSchema 各章节映射到 DeepAgentConfig：
     - scope → AskUserTemplateRail 范围校验配置
     - capabilities.tools → 工具白名单
     - capabilities.todolist_steps → LiteTodoWriteTool 动态 Schema
     - limits.max_iterations → DeepAgentConfig.maxIterations
     - limits.tasks → ExecutionLimitRail 参数
     - skills → SkillLoader 加载规则
     - utterances → ScriptsConfigManager 话术配置
  6. System Prompt 组装：AgentRule Body + todolist_steps 生成的步骤目录表格 + task_dependencies 生成的依赖规则 Markdown + Skill 提示词
- **预期输出/结果**：AgentRule.md 全部章节解析完成，各配置项正确映射到 DeepAgentConfig 和业务组件，System Prompt 包含完整业务上下文

### 用例 3.3：配置章节独立可选（渐进式配置）

- **前置条件**：DeepAgentPlus 实例已创建
- **输入参数**：AgentRule.md 文件（部分章节填写，其余章节缺失）
- **执行步骤**：
  1. `AgentRuleParser` 解析 AgentRule.md，识别已填写和缺失的章节
  2. 缺失章节使用默认值兜底：
     - capabilities 未配 → 全部 46+ DeepAgent 内置工具可用
     - todolist_steps 未配 → 不注入 LiteTodoWriteTool，保留 DeepAgent Todo 工具组
     - skills 未配 → 不加载 Skill
     - utterances 未配 → ask_user 使用 DeepAgent 原生通用中断行为
     - limits 未配 → 使用 DeepAgent 默认迭代限制
     - scope 未配 → 不启用范围校验
  3. 已填写章节按规则生效，未填写章节零影响
  4. Agent 正常启动运行
- **预期输出/结果**：部分配置的 AgentRule.md 可正常驱动 Agent 运行，缺失章节不影响已配置章节的功能

### 用例 3.4：零配置最小启动

- **前置条件**：DeepAgent 框架已部署，无任何配置文件
- **输入参数**：仅 DeepAgentPlusConfig.builder().build()（空配置）
- **执行步骤**：
  1. 创建 `DeepAgentPlus` 实例，传入空配置
  2. 无 AgentRule.md → 不解析配置，全部使用 DeepAgent 默认值
  3. 无 Skill → 不加载领域知识
  4. 无话术 → ask_user 使用 DeepAgent 原生行为
  5. 工具集 = DeepAgent 全部 46+ 默认工具
  6. Rail 集 = DeepAgent 全部 26+ 默认 Rail
  7. Agent.stream() 正常接收请求并执行推理
- **预期输出/结果**：零配置 Agent 可在 10 秒内启动运行，具备 DeepAgent 全部基础能力

### 用例 3.5：装配式开发模式（配置驱动）

- **前置条件**：AgentRule.md + ScriptsConfig.md + Skills 目录已准备
- **输入参数**：配置文件路径列表
- **执行步骤**：
  1. 业务分析师编写 AgentRule.md（定义 scope / capabilities / limits / skills / utterances）
  2. 业务分析师编写 ScriptsConfig.md（定义话术模板）
  3. 领域专家编写 Skills 目录（SKILL.md + scripts）
  4. 调用 `DeepAgentPlus.assemble(agentRulePath, scriptsConfigPath, skillDirectory)` 一行代码创建 Agent
  5. assemble 内部：解析 AgentRule.md → 解析 ScriptsConfig.md → 加载 Skills → 映射配置到 DeepAgentConfig → 注册工具/Rail → 构建 System Prompt
  6. Agent 实例创建完成，可直接调用 stream/invoke
- **预期输出/结果**：零 Java 代码即可创建完整业务 Agent，配置文件热更新即可调整行为

### 用例 3.6：挂载式开发模式（继承+钩子）

- **前置条件**：DeepAgentPlus 类可用，开发者具备 Java 开发能力
- **输入参数**：自定义 Agent 类定义
- **执行步骤**：
  1. 开发者创建 `class MyAgent extends DeepAgentPlus`
  2. 覆写 `customTools()` 钩子 → 返回追加的自定义工具列表
  3. 覆写 `customRails()` 钩子 → 返回追加的自定义 Rail 列表
  4. 可选覆写 `buildSystemPrompt()` → 修改/追加 System Prompt
  5. 可选覆写 `buildSkillPrompt(SkillDef)` → 修改 Skill 提示词
  6. 可选覆写 `resolveToolOverride()` → 决定工具名冲突时使用哪个实现
  7. 可选覆写 `onBeforeStream(Session)` → 注入租户上下文、权限校验
  8. 可选覆写 `onBeforeToolCall(ToolCall)` → 参数校验、调用计数
  9. 可选覆写 `onAfterToolCall(ToolCall, ToolOutput)` → 审计留痕
  10. 可选覆写 `onEvent(AgentEvent)` → 事件监控/埋点
  11. 可选覆写 `onAfterStream(Session)` → 保存审计日志、释放资源
  12. AgentRule.md 可选提供，不提供则使用 DeepAgent 默认行为
  13. `new MyAgent(config)` 创建实例，钩子逻辑在初始化时自动注入
- **预期输出/结果**：开发者可通过继承+钩子实现任意深度定制，AgentRule.md 可选，无需完整配置即可运行

### 用例 3.7：混合式开发模式（装配+挂载叠加）

- **前置条件**：AgentRule.md 已编写，开发者需要追加自定义逻辑
- **输入参数**：AgentRule.md 配置 + 自定义钩子覆写
- **执行步骤**：
  1. 业务分析师编写 AgentRule.md（配置驱动基础能力）
  2. 开发者继承 DeepAgentPlus，覆写部分钩子（追加自定义工具/Rail/逻辑）
  3. AgentRule.md 配置生效 + 钩子覆写逻辑叠加
  4. 配置与钩子不冲突时：两者同时生效
  5. 配置与钩子冲突时：`resolveToolOverride()` 决定优先级（默认自定义优先）
- **预期输出/结果**：装配式配置与挂载式钩子可叠加使用，实现配置驱动基础能力 + 代码驱动深度定制的混合模式

### 用例 3.8：业务范围约束（Scope / Out-of-Scope）

- **前置条件**：AgentRule.md 中 `scope` 章节已配置 `allowed` 和 `out_of_scope_message`
- **输入参数**：用户请求超出 scope.allowed 范围
- **执行步骤**：
  1. 用户发送请求，Agent 进入推理循环
  2. LLM 生成工具调用意图
  3. `AskUserTemplateRail` 读取 `AgentRuleSchema.scope`
  4. 提取 LLM 调用意图，判断是否在 `scope.allowed` 范围内
  5. 判断结果：超出范围
  6. 注入 `scope.out_of_scope_message` 话术模板
  7. 调用 `interrupt()` 中断当前流程，向用户返回超范围提示
- **预期输出/结果**：超出业务范围的请求被拦截，用户收到配置的超范围话术提示，Agent 不执行超范围操作

### 用例 3.9：规划步骤模板（Planning Steps）

- **前置条件**：AgentRule.md 中 `planning_steps` 已配置
- **输入参数**：用户请求触发 Agent 规划
- **执行步骤**：
  1. `AgentRuleToConfigMapper` 将 `planning_steps` 注入到 System Prompt 的"规划与输出规约"节
  2. LLM 参考规划步骤进行任务规划
  3. `TaskPlanningRail` 检测规划合理性
  4. `TaskLoop` 执行规划步骤
  5. `CompletionPromiseEvaluator` 检测任务完成
- **预期输出/结果**：Agent 按配置的规划步骤执行任务，TaskLoop 自动循环直至完成

### 用例 3.10：任务依赖关系校验（Task Dependencies）

- **前置条件**：AgentRule.md 中 `task_dependencies` 已配置（如 step_5 依赖 step_2/3/4）
- **输入参数**：LiteTodoWriteTool 调用，尝试将 step_5 标记为 done
- **执行步骤**：
  1. `task_dependencies` 解析为依赖规则 Map
  2. 依赖规则注入 System Prompt（生成 Markdown 表格：step_id / 绑定 Skill / 前置依赖）
  3. 依赖规则注入 LiteTodoWriteTool 的校验逻辑
  4. LLM 调用 LiteTodoWriteTool，尝试将 step_5 标记为 done
  5. LiteTodoWriteTool 校验：step_2/3/4 是否全部 done
  6. 校验结果：step_3 未 done → 拒绝写入
  7. 返回错误提示："step_5 的前置依赖 step_3 未完成，请先完成 step_3"
- **预期输出/结果**：任务依赖关系被强制校验，未满足前置依赖的步骤无法标记为 done，Agent 按依赖顺序执行

### 用例 3.11：Todo 步骤管理（LiteTodoWriteTool 动态 Schema）

- **前置条件**：AgentRule.md 中 `todolist_steps` 已配置步骤列表
- **输入参数**：todolist_steps 配置（含 step_id / name / skill 字段）
- **执行步骤**：
  1. `LiteTodoWriteTool.buildSchema(steps)` 运行时构建 JSON Schema
  2. 从 steps 中提取 step_id 列表，生成 `step_id` 的 enum 约束
  3. Schema 定义：todos 数组，每项含 step_id（enum）+ status（pending/done）
  4. LiteTodoWriteTool 替换 DeepAgent 的 Todo 工具组（todo_create/update/complete）
  5. LLM 调用 LiteTodoWriteTool，传入完整 todos 列表（覆盖式更新）
  6. 单次调用完成全部步骤状态更新，无需多次调用
- **预期输出/结果**：Todo 步骤受配置约束，LLM 只能使用配置定义的 step_id，覆盖式一次调用更新全部状态

### 用例 3.12：执行限制（全局迭代 + 单工具计数 + 中断超时 + 输入重试）

- **前置条件**：AgentRule.md 中 `limits` 章节已配置
- **输入参数**：limits 配置（max_iterations / tasks / interrupt_timeout_seconds / max_input_attempts）
- **执行步骤**：
  1. `limits.max_iterations` → 映射到 `DeepAgentConfig.maxIterations = 100`
  2. `limits.tasks.call_versatile: 100` → `ExecutionLimitRail` 按 tool 维度计数
  3. `limits.interrupt_timeout_seconds: 300` → `AskUserTemplateRail` 内部计时器
  4. `limits.max_input_attempts: 3` → `AskUserTemplateRail` 内部重试计数器
  5. Agent 执行过程中：
     - 全局迭代超过 max_iterations → IterationLimitRail 强制完成
     - 单工具调用超过配置次数 → ExecutionLimitRail 强制完成
     - 中断等待超过 timeout → AskUserTemplateRail 超时终止
     - 用户输入错误超过 max_input_attempts → AskUserTemplateRail 拒绝继续
- **预期输出/结果**：四类执行限制全部生效，Agent 在安全边界内运行，超限自动终止并返回对应话术提示

### 用例 3.13：话术系统（ScriptsConfig 解析 + 模板匹配 + JSON 容错）

- **前置条件**：ScriptsConfig.md 文件已编写，或 AgentRule.md 中 `utterances` 章节已配置
- **输入参数**：ScriptsConfig.md 文件路径 / utterances 内联配置
- **执行步骤**：
  1. `ScriptsConfigManager.parse(configPath)` 解析 YAML → UtteranceDef POJO
  2. 话术模板分类加载：tools（工具执行话术） / task（任务状态话术） / interrupt（中断话术） / due_diligence（尽调专用话术）
  3. `ResponseTemplateManager` 初始化：
     - `safeFormat(template, variables)` → 缺失变量替换为空串，不抛异常
     - `resolveTemplate(contextKey)` → 根据上下文匹配话术模板
     - `jsonRecover(rawString)` → 4 层容错解析（中文引号→外引号剥离→全角冒号→正则兜底）
     - `handleConfirm(template, session)` → confirm 模板持久化 selected_product 到 session state
  4. Agent 执行过程中，话术模板按场景自动匹配和注入
- **预期输出/结果**：话术模板集中管理，变量安全替换，JSON 容错解析，confirm 特殊逻辑正确持久化

### 用例 3.14：AskUser 话术模板中断与恢复

- **前置条件**：AskUserTemplateRail 已注册，话术模板已加载
- **输入参数**：LLM 调用 ask_user 工具
- **执行步骤**：
  1. LLM 生成 ask_user 工具调用
  2. `AskUserTemplateRail.before_invoke()` 拦截，注入话术参数到工具调用
  3. 话术模板匹配：根据上下文（如 confirm / missing_product / missing_amount）选择对应模板
  4. `safeFormat()` 格式化话术模板，替换变量
  5. 写入 `response_template` 到 session state
  6. 调用 `interrupt()` 中断，向用户推送格式化后的话术提示
  7. 用户输入响应
  8. `AskUserTemplateRail` 恢复：`reject(tool_result=user_input)`
  9. 如果是 confirm 模板 → `handleConfirm()` 持久化 `selected_product` 到 session state
  10. 如果输入不匹配预期 → 重试计数器递增，未超限则重新中断
- **预期输出/结果**：ask_user 中断时用户收到话术模板格式的提示，恢复时用户输入正确传递给 LLM，confirm 场景产品信息持久化

### 用例 3.15：Versatile 级联中断与恢复

- **前置条件**：VersatileInterruptRail 已注册，VersatileAdapter 服务可用
- **输入参数**：LLM 调用 call_versatile 工具
- **执行步骤**：
  1. LLM 生成 call_versatile 工具调用（含工作流参数）
  2. `VersatileInterruptRail` 拦截 call_versatile
  3. 写入 `pending_delegate` 到 session state（含工作流描述、参数）
  4. 调用 `interrupt()` 中断，向编排器推送 DelegateRequest 事件
  5. 编排器（executor.py）消费 DelegateRequest，通过 A2A 协议委托给 VersatileAdapter
  6. VersatileAdapter 执行工作流，返回 NDJSON/SSE 流式结果
  7. 编排器将结果通过 `reject(cascade_result)` 恢复 Agent
  8. Agent 继续推理循环，使用工作流返回结果
- **预期输出/结果**：call_versatile 调用触发级联中断，VA 工作流异步执行完成后恢复 Agent，结果正确传递

### 用例 3.16：MCP 沙箱执行中断

- **前置条件**：McpInterruptRail 已注册，MCP 脚本可用
- **输入参数**：LLM 调用 call_mcp 工具
- **执行步骤**：
  1. LLM 生成 call_mcp 工具调用（含脚本名称和参数）
  2. `McpInterruptRail` 拦截 call_mcp
  3. 在沙箱环境中执行 MCP 脚本
  4. 获取脚本执行结果
  5. `reject(result)` 将结果返回给 Agent
  6. Agent 继续推理循环，使用 MCP 脚本结果
- **预期输出/结果**：MCP 脚本在沙箱中安全执行，结果正确返回给 Agent

### 用例 3.17：取消任务（CancelTask + CancelRail）

- **前置条件**：CancelRail 已注册，话术模板已加载
- **输入参数**：LLM 调用 cancel_task 工具
- **执行步骤**：
  1. LLM 生成 cancel_task 工具调用
  2. `CancelRail` 拦截 cancel_task
  3. 注入 `task_cancelled` 话术模板
  4. 调用 `requestForceFinish()` 强制完成当前任务
  5. 向用户推送取消话术提示
- **预期输出/结果**：任务被取消，用户收到取消话术提示，Agent 流程终止

### 用例 3.18：Skill 加载与管理

- **前置条件**：Skills 目录已准备，AgentRule.md 中 `skills` 章节已配置
- **输入参数**：skills 配置（directory / mode / whitelist）
- **执行步骤**：
  1. `SkillLoader` 扫描 `skills.directory` 下所有子目录
  2. 读取每个子目录的 SKILL.md（YAML Frontmatter + Markdown Body）
  3. 解析 YAML 前置元数据（name / description / tools / input_params / output）
  4. 按 `mode` 过滤：
     - mode=all → 加载全部扫描到的 Skill
     - mode=whitelist → 仅加载 whitelist 中指定的 Skill
  5. 每个 Skill 的 Markdown 正文追加到 System Prompt
  6. SKILL.md 中 `tools` 列表的工具自动加入 `capabilities.tools` 白名单
  7. `todolist_steps.skill` 字段与 Skill 目录自动关联
- **预期输出/结果**：Skill 按配置规则加载，领域知识注入 System Prompt，关联工具自动启用

### 用例 3.19：固定脚本帧推送（FixedScriptFeeder）

- **前置条件**：AgentRule.md 中 `capabilities.think_chunk` 已配置
- **输入参数**：think_chunk 配置（模式：fixed_script / real_stream）
- **执行步骤**：
  1. `FixedScriptFeeder` 初始化，读取固定脚本配置
  2. Agent 推理过程中，LLM 思考阶段触发 ThinkStart 事件
  3. FixedScriptFeeder 按帧推送固定脚本内容（模拟思考过程）
  4. 逐帧推送 ThinkChunk 事件
  5. 推送完成触发 ThinkEnd 事件
  6. 如果配置为 real_stream 模式 → 使用 DeepAgent 原生流式输出
- **预期输出/结果**：think_chunk 模式下，思考过程按固定脚本帧推送，用户看到结构化的思考内容

### 用例 3.20：流事件转换（StreamEventAdapter — 17 种 AgentEvent）

- **前置条件**：DeepAgentPlus 实例已初始化，StreamEventAdapter 已注册
- **输入参数**：Agent 执行过程中产生的原始事件流
- **执行步骤**：
  1. Agent.stream() 启动，产生原始事件流
  2. `StreamEventAdapter` 将 DeepAgent 原始事件转换为 17 种 AgentEvent：
     - ConversationStartEvent / ConversationEndEvent
     - ThinkStartEvent / ThinkChunkEvent / ThinkEndEvent
     - TodoListStartEvent / TodoListItemEvent / TodoListEndEvent
     - TodoStartEvent / TodoStatusEvent / TodoEndEvent
     - ToolStartEvent / ToolStatusEvent / ToolEndEvent
     - InterruptStartEvent / InterruptEndEvent
     - FinalAnswerEvent
  3. 新增 DeepAgent 特有事件类型（DelegateRequest 等）
  4. 事件流通过 SSE 推送到前端
- **预期输出/结果**：前端收到完整的 17+ 种 AgentEvent 流，事件类型与 Python EDPAgent 完全兼容

### 用例 3.21：记忆系统（MemoryRail — Redis + GaussDB + ES）

- **前置条件**：DeepAgent 内置 MemoryRail 可用，Redis/GaussDB/ES 连接池已配置
- **输入参数**：记忆配置（存储类型 / 向量检索参数）
- **执行步骤**：
  1. 复用 DeepAgent 内置 `MemoryRail`，无需自建
  2. `MemoryRail.before_invoke()` → 加载记忆变量注入 System Prompt
  3. `MemoryRail.after_invoke()` → 异步写入新记忆
  4. Redis KV 存储：会话级短期记忆
  5. GaussDB 关系存储：结构化业务数据
  6. ES 向量检索：语义相似度召回
  7. 复用 DeepAgent 内置连接池，性能与 Python 版持平
- **预期输出/结果**：记忆功能完整继承，三存储引擎协同工作，记忆变量正确注入 Prompt

### 用例 3.22：TaskLoop 多轮循环与完成检测

- **前置条件**：DeepAgentPlus 实例已初始化，`enableTaskLoop=true`
- **输入参数**：用户复杂请求，需多轮推理完成
- **执行步骤**：
  1. Agent 接收用户请求，进入 TaskLoop
  2. `LoopCoordinator` 协调多轮循环
  3. 每轮：LLM 推理 → 工具调用 → Rail 拦截 → 结果收集
  4. `CompletionPromiseEvaluator` 检测是否达到完成条件（promise_tag 匹配 + confirmation_count 达标）
  5. `MaxRoundsEvaluator` 检测是否超过最大轮次
  6. `TimeoutEvaluator` 检测是否超时
  7. 完成条件满足 → TaskLoop 结束，输出 FinalAnswer
  8. 面客/强规则模式下 TaskLoop 自动禁用（isTaskLoopEnabled=false），避免与 EDPA Cascade 续轮冲突
- **预期输出/结果**：复杂任务通过多轮循环自动完成，完成检测准确，超限/超时自动终止

### 用例 3.23：Steering 引导注入与 FollowUp 追问

- **前置条件**：DeepAgent 内置 Steering/FollowUp 机制可用
- **输入参数**：外部引导指令 / 追问队列
- **执行步骤**：
  1. 外部系统调用 `agent.steer(instruction)` 注入引导指令
  2. 引导指令注入到下一轮 LLM 推理上下文
  3. Agent 按引导方向调整推理路径
  4. `agent.isFollowUp()` 检测是否有追问队列
  5. 追问队列中的问题逐个推送给用户
  6. 用户回答后继续推理
- **预期输出/结果**：外部可动态引导 Agent 方向，追问机制确保信息完整性

### 用例 3.24：AgentMode 切换（Plan / Normal）

- **前置条件**：DeepAgent 内置 AgentModeRail 可用
- **输入参数**：LLM 调用 switch_mode 工具
- **执行步骤**：
  1. LLM 判断当前任务需要规划模式
  2. LLM 调用 `switch_mode` 工具，切换到 Plan 模式
  3. `AgentModeRail` 检测模式切换，调整推理策略
  4. Plan 模式下：LLM 先输出完整规划，再逐步执行
  5. 任务规划完成后，LLM 调用 `switch_mode` 切换回 Normal 模式
  6. Normal 模式下：LLM 逐步推理执行
- **预期输出/结果**：Agent 可动态切换规划/执行模式，复杂任务先规划后执行

### 用例 3.25：权限系统（PermissionEngine）

- **前置条件**：DeepAgent 内置 SecurityRail + PermissionEngine 可用
- **输入参数**：工具调用请求
- **执行步骤**：
  1. LLM 生成工具调用请求
  2. `SecurityRail` 拦截，调用 `PermissionEngine` 校验权限
  3. 权限校验通过 → 允许工具调用
  4. 权限校验失败 → 拒绝工具调用，返回权限不足提示
- **预期输出/结果**：工具调用受权限控制，未授权操作被拦截

### 用例 3.26：子 Agent 委派（TaskTool）

- **前置条件**：DeepAgent 内置 TaskTool + SubagentRail 可用
- **输入参数**：LLM 调用 task 工具委派子任务
- **执行步骤**：
  1. LLM 判断当前任务需要委派给子 Agent
  2. LLM 调用 `task` 工具，指定子 Agent 类型和任务描述
  3. `SubagentRail` 拦截，创建子 Agent 实例
  4. 子 Agent 执行任务，返回结果
  5. 结果注入主 Agent 推理上下文
  6. 主 Agent 继续推理
- **预期输出/结果**：复杂任务可委派给子 Agent 执行，结果正确回传主 Agent

### 用例 3.27：工具体系（业务工具 + DeepAgent 内置工具）

- **前置条件**：DeepAgentPlus 实例已初始化
- **输入参数**：AgentRule.md 中 `capabilities.tools` 白名单配置
- **执行步骤**：
  1. DeepAgent 内置 46+ 工具自动可用（Filesystem / Bash / WebSearch / WebFetch / Task / SwitchMode / Todo / Session / MCP 等）
  2. 5 个业务工具自动注册：
     - LiteTodoWriteTool — 覆盖式 Todo（条件性替换 DeepAgent Todo 工具组）
     - CallVersatileTool — VA 工作流调用
     - CallMcpTool — MCP 脚本调用
     - EnhancedAskUserTool — 增强 ask_user（话术参数）
     - CancelTaskTool — 任务取消
  3. `capabilities.tools` 白名单配置 → 仅启用白名单中的工具
  4. 白名单未配置 → 全部工具可用
  5. 工具名冲突时 → `resolveToolOverride()` 决定使用哪个实现（默认自定义优先）
  6. 新增 DeepAgent 工具（EDPAgent 原本没有）：edit_file / WebSearch / Task（子Agent委派）
- **预期输出/结果**：工具体系完整，业务工具与内置工具协同，白名单控制工具可见性

### 用例 3.28：Rail 体系（注册顺序与优先级）

- **前置条件**：DeepAgentPlus 实例已初始化
- **输入参数**：无（Rail 自动注册）
- **执行步骤**：
  1. DeepAgentPlus 初始化时自动注册全部 Rail，按优先级排序：
     - CancelRail（priority=10）
     - IterationLimitRail（priority=40）
     - ExecutionLimitRail（priority=40）
     - McpInterruptRail（priority=50）
     - VersatileInterruptRail（priority=50）
     - AskUserTemplateRail（priority=50）
     - SubagentRail（自动）
     - SecurityRail（自动）
     - AgentModeRail（自动）
     - SessionRail（自动）
     - ProgressiveToolRail（自动）
     - MemoryRail（priority=100）
     - LogRail（priority=1000）
  2. DeepAgent 内置 26+ Rail 自动注册（TaskCompletion / TaskPlanning / ContextProcessor / ContextAssemble / SkillUse / SkillCreate / Verification / VerificationContract / SysOperation / Evolution / CodingMemory / Heartbeat / Lsp 等）
  3. Rail 拦截链按优先级顺序执行
  4. 业务 Rail 拦截业务工具调用，内置 Rail 拦截通用行为
- **预期输出/结果**：Rail 体系完整，业务 Rail 与内置 Rail 按优先级协同拦截

### 用例 3.29：配置渐进示例（Level 0 → Level 4）

- **前置条件**：DeepAgentPlus 框架可用
- **输入参数**：不同复杂度的配置文件
- **执行步骤**：
  1. **Level 0（零配置）**：无任何配置文件，全部 46+ 工具可用，最简上手
  2. **Level 1（仅限制工具）**：AgentRule.md 仅配置 `capabilities.tools` 白名单，其余默认
  3. **Level 2（加 Todo）**：添加 `capabilities.todolist_steps` 启用步骤管理，LiteTodoWriteTool 替换 DeepAgent Todo 工具组
  4. **Level 3（加 Skills + 话术）**：添加 `skills` 目录配置 + `utterances` 话术配置，领域知识和合规话术生效
  5. **Level 4（全部定制）**：完整 AgentRule.md 全章节 + 挂载钩子覆写，任意深度定制
- **预期输出/结果**：配置复杂度渐进递增，每级仅增加关注的部分，未配置部分零影响

### 用例 3.30：新增 DeepAgent 能力继承（EDPAgent 原本没有的功能）

- **前置条件**：DeepAgent 框架完整可用
- **输入参数**：无（自动继承）
- **执行步骤**：
  1. DeepAgentPlus 继承 DeepAgent，自动获得以下新增能力：
     - TaskLoop 多轮自动循环
     - CompletionPromise 完成检测
     - Steering 引导注入
     - FollowUp 追问队列
     - AgentMode Plan/Normal 切换
     - PermissionEngine 权限系统
     - LSP 代码智能
     - 浏览器自动化
     - MCP 资源管理
     - ProgressiveToolRail 渐进式工具发现
     - 多模态（视觉/音频）
     - 文件编辑（edit_file）
     - Web 搜索（WebSearchTool）
     - 子 Agent 委派（TaskTool）
  2. 新增能力无需额外配置，DeepAgent 默认启用
  3. 业务场景不需要的新增能力可通过配置禁用
- **预期输出/结果**：EDPAgent 重构后不仅继承原有全部能力，还获得 DeepAgent 14+ 新增能力

---

## 4. 异常与扩展流程用例 (Exception / Alternate Path)

### 用例 4.1：AgentRule.md 文件缺失

- **前置条件**：DeepAgentPlus 实例创建时未提供 AgentRule.md 路径
- **输入参数**：DeepAgentPlusConfig.builder().build()（无 agentRulePath）
- **执行步骤**：
  1. DeepAgentPlus 初始化，检测无 AgentRule.md 路径
  2. 跳过 AgentRuleParser 解析
  3. 全部使用 DeepAgent 默认配置
  4. 不注册业务工具（LiteTodoWrite 等），保留 DeepAgent 内置工具
  5. 不注册业务 Rail（VersatileInterrupt 等），保留 DeepAgent 内置 Rail
  6. Agent 以 DeepAgent 原生行为运行
- **预期输出/结果**：Agent 正常启动，以 DeepAgent 原生行为运行，无报错

### 用例 4.2：AgentRule.md 章节格式错误

- **前置条件**：AgentRule.md 文件存在但 YAML Frontmatter 格式错误
- **输入参数**：格式错误的 AgentRule.md 文件
- **执行步骤**：
  1. `AgentRuleParser.parse()` 读取文件
  2. YAML Frontmatter 解析失败
  3. 抛出 `AgentRuleParseException`，包含具体错误位置和原因
  4. Agent 初始化失败，日志记录错误详情
- **预期输出/结果**：配置解析失败时明确报错，不静默忽略，错误信息包含定位信息

### 用例 4.3：AgentRule.md 部分章节缺失（默认值兜底）

- **前置条件**：AgentRule.md 仅配置了部分章节（如仅 capabilities.tools）
- **输入参数**：部分配置的 AgentRule.md
- **执行步骤**：
  1. `AgentRuleParser` 解析成功，识别已填写和缺失章节
  2. 缺失章节使用默认值：
     - scope 缺失 → 不启用范围校验
     - todolist_steps 缺失 → 不注入 LiteTodoWriteTool
     - limits 缺失 → 使用 DeepAgent 默认迭代限制
     - skills 缺失 → 不加载 Skill
     - utterances 缺失 → 通用 ask_user 中断
  3. 已填写章节正常生效
  4. Agent 正常运行
- **预期输出/结果**：部分配置不影响 Agent 运行，缺失章节使用合理默认值

### 用例 4.4：LiteTodoWriteTool 依赖校验失败

- **前置条件**：todolist_steps 配置了任务依赖关系
- **输入参数**：LLM 尝试标记 step_5 为 done，但 step_3 未完成
- **执行步骤**：
  1. LiteTodoWriteTool 接收写入请求
  2. `_validate_deps()` 校验 step_5 的前置依赖
  3. 检测 step_3 状态为 pending → 校验失败
  4. 拒绝写入，返回错误提示："step_5 的前置依赖 step_3 未完成"
  5. LLM 收到错误提示，调整执行顺序
- **预期输出/结果**：依赖校验失败时拒绝写入并返回明确提示，Agent 自动调整执行顺序

### 用例 4.5：LiteTodoWriteTool 未配置（todolist_steps 缺失）

- **前置条件**：AgentRule.md 中未配置 todolist_steps
- **输入参数**：无 todolist_steps 配置
- **执行步骤**：
  1. DeepAgentPlus 初始化，检测 todolist_steps 缺失
  2. 不注册 LiteTodoWriteTool
  3. 保留 DeepAgent 内置 Todo 工具组（todo_create / todo_update / todo_complete）
  4. Agent 使用 DeepAgent 原生多工具 Todo 体系
- **预期输出/结果**：无 Todo 配置时 Agent 使用 DeepAgent 原生 Todo，不报错

### 用例 4.6：全局迭代超限

- **前置条件**：limits.max_iterations 配置为 100
- **输入参数**：Agent 执行迭代次数达到 100
- **执行步骤**：
  1. `IterationLimitRail` 每个 tool_call 后计数
  2. 计数达到 max_iterations = 100
  3. 强制完成当前任务
  4. 注入 `task.max_iterations` 话术："已达到最大执行次数，任务终止"
  5. 输出 FinalAnswer 事件
- **预期输出/结果**：迭代超限自动终止，用户收到超限话术提示

### 用例 4.7：单工具调用次数超限

- **前置条件**：limits.tasks.call_versatile 配置为 100
- **输入参数**：call_versatile 工具调用次数达到 100
- **执行步骤**：
  1. `ExecutionLimitRail` 按 tool 维度计数
  2. call_versatile 调用次数达到 100
  3. 强制完成当前任务
  4. 返回超限提示
- **预期输出/结果**：单工具调用超限自动终止，防止工具滥用

### 用例 4.8：中断超时

- **前置条件**：limits.interrupt_timeout_seconds 配置为 300
- **输入参数**：ask_user 中断等待超过 300 秒
- **执行步骤**：
  1. `AskUserTemplateRail` 内部计时器启动
  2. 中断等待时间超过 300 秒
  3. 超时终止中断
  4. 返回超时提示，Agent 继续推理或强制完成
- **预期输出/结果**：中断超时自动终止，防止无限等待

### 用例 4.9：用户输入重试次数超限

- **前置条件**：limits.max_input_attempts 配置为 3
- **输入参数**：用户连续 3 次输入不符合预期
- **执行步骤**：
  1. `AskUserTemplateRail` 内部重试计数器递增
  2. 用户第 3 次输入仍不符合预期
  3. 计数器达到 max_input_attempts = 3
  4. 拒绝继续中断，返回提示
  5. Agent 继续推理或强制完成
- **预期输出/结果**：输入重试超限自动终止，防止无限重试

### 用例 4.10：话术模板变量缺失

- **前置条件**：话术模板含 `{productName}` 变量，但 session state 中无该变量
- **输入参数**：话术模板匹配时变量缺失
- **执行步骤**：
  1. `ResponseTemplateManager.safeFormat()` 格式化模板
  2. 检测 `{productName}` 变量缺失
  3. 缺失变量替换为空串（""），不抛异常
  4. 格式化结果："请确认：产品，金额100元，是否继续？"
  5. 正常推送给用户
- **预期输出/结果**：变量缺失时不报错，安全替换为空串，话术仍可正常推送

### 用例 4.11：JSON 容错 4 层解析

- **前置条件**：LLM 返回的 ask_user 参数包含非标准 JSON
- **输入参数**：含中文引号/全角冒号/外引号的 JSON 字符串
- **执行步骤**：
  1. `ResponseTemplateManager.jsonRecover()` 尝试标准 JSON 解析 → 失败
  2. 第 1 层容错：中文引号（""）→ 标准引号（""）→ 重新解析
  3. 第 2 层容错：外引号剥离 → 重新解析
  4. 第 3 层容错：全角冒号（：）→ 标准冒号（:）→ 重新解析
  5. 第 4 层容错：正则兜底提取关键字段 → 重新解析
  6. 某层容错成功 → 返回解析结果
  7. 全部容错失败 → 返回原始字符串，不抛异常
- **预期输出/结果**：非标准 JSON 通过 4 层容错机制尽可能解析，全部失败时安全降级

### 用例 4.12：Skill 白名单过滤

- **前置条件**：skills.mode 配置为 whitelist，whitelist 仅包含 2 个 Skill
- **输入参数**：skills 目录下有 5 个 Skill
- **执行步骤**：
  1. `SkillLoader` 扫描目录，发现 5 个 Skill
  2. 按 whitelist 过滤，仅加载 2 个指定 Skill
  3. 其余 3 个 Skill 不加载，不注入 System Prompt
  4. 白名单 Skill 的关联工具自动启用
  5. 非 whitelist Skill 的关联工具不启用
- **预期输出/结果**：仅白名单 Skill 生效，减少无关领域知识干扰

### 用例 4.13：Skill 目录不存在

- **前置条件**：skills.directory 配置路径不存在
- **输入参数**：无效的 Skill 目录路径
- **执行步骤**：
  1. `SkillLoader` 扫描目录，路径不存在
  2. 记录警告日志："Skill directory not found: {path}"
  3. 不加载任何 Skill
  4. Agent 正常运行（无领域知识增强）
- **预期输出/结果**：Skill 目录缺失时不报错，Agent 以无 Skill 模式运行

### 用例 4.14：Versatile 级联中断超时/失败

- **前置条件**：VersatileInterruptRail 已拦截 call_versatile
- **输入参数**：VersatileAdapter 工作流执行超时或失败
- **执行步骤**：
  1. call_versatile 被拦截，interrupt() 中断
  2. 编排器委托给 VersatileAdapter
  3. VersatileAdapter 执行超时或返回错误
  4. 编排器将错误结果通过 `reject(error_result)` 恢复 Agent
  5. Agent 收到错误结果，调整推理策略或向用户报告
- **预期输出/结果**：VA 工作流失败时错误结果正确传递给 Agent，Agent 可自适应处理

### 用例 4.15：MCP 沙箱执行失败

- **前置条件**：McpInterruptRail 已拦截 call_mcp
- **输入参数**：MCP 脚本执行异常
- **执行步骤**：
  1. call_mcp 被拦截，沙箱执行脚本
  2. 脚本执行异常（超时/错误/资源不足）
  3. 沙箱捕获异常，返回错误结果
  4. `reject(error_result)` 将错误返回给 Agent
  5. Agent 收到错误结果，调整推理策略
- **预期输出/结果**：MCP 脚本执行失败时错误安全返回，沙箱隔离确保不影响 Agent 主进程

### 用例 4.16：工具名冲突处理

- **前置条件**：业务工具与 DeepAgent 内置工具同名（如自定义 ask_user 与内置 ask_user）
- **输入参数**：同名工具注册请求
- **执行步骤**：
  1. DeepAgentPlus 检测工具名冲突
  2. 调用 `resolveToolOverride(toolName, customTool, builtInTool)`
  3. 默认行为：返回自定义工具（自定义优先）
  4. 开发者可覆写 `resolveToolOverride()` 决定使用哪个实现
  5. 最终选择的工具注册到 AbilityManager
- **预期输出/结果**：工具名冲突有明确解决机制，默认自定义优先，开发者可自定义冲突策略

### 用例 4.17：DeepAgent API 变动适配

- **前置条件**：DeepAgent 框架版本升级，API 发生变动
- **输入参数**：新版本 DeepAgent SDK
- **执行步骤**：
  1. DeepAgentPlus 作为隔离层，检测 API 变动
  2. 在 DeepAgentPlus 内部适配新 API
  3. 业务层代码（AgentRule.md / 钩子覆写）无需修改
  4. 锁定 DeepAgent 版本，通过 DeepAgentPlus 隔离层适配
- **预期输出/结果**：DeepAgent API 变动不影响业务层，隔离层确保向上兼容

### 用例 4.18：DeepAgent 子类化不可行（备选方案）

- **前置条件**：穿刺验证 P2 结论为 DeepAgent 不支持子类化
- **输入参数**：子类化方案不可行
- **执行步骤**：
  1. 切换到备选方案：委托模式（DeepAgentPlus 包装 DeepAgent）
  2. DeepAgentPlus 内部持有 DeepAgent 实例（组合而非继承）
  3. DeepAgentPlus 通过委托调用 DeepAgent 的方法
  4. 钩子机制通过包装层实现
  5. 功能完整性不受影响
- **预期输出/结果**：委托模式下功能完整性不变，架构调整为组合模式

### 用例 4.19：AskUser 中断机制不兼容话术模板（备选方案）

- **前置条件**：穿刺验证 P6 结论为 DeepAgent 中断机制不兼容话术模板
- **输入参数**：中断恢复机制无法注入话术参数
- **执行步骤**：
  1. 切换到备选方案：前端层做话术匹配
  2. Rail 仅负责任务中断/恢复，不注入话术
  3. 前端根据 AgentEvent 类型匹配话术模板
  4. 话术渲染在前端完成，Agent 侧保持通用中断行为
- **预期输出/结果**：话术功能通过前端层实现，Agent 侧中断机制保持通用

### 用例 4.20：Versatile 级联中断不支持（备选方案）

- **前置条件**：穿刺验证 P7 结论为级联中断机制不可行
- **输入参数**：级联中断无法实现
- **执行步骤**：
  1. 切换到备选方案：CallVersatileTool 直接发起同步 HTTP 请求
  2. 不使用级联中断模式
  3. CallVersatileTool 内部发起 HTTP 请求到 Versatile 低代码平台
  4. 同步等待结果返回
  5. 结果直接作为工具输出返回给 LLM
- **预期输出/结果**：VA 工作流调用降级为同步模式，功能可用但失去异步级联优势

### 用例 4.21：AgentRule.md 旧版兼容迁移

- **前置条件**：存在 Python EDPAgent 版本的 AgentRule.md 旧配置文件
- **输入参数**：旧版 AgentRule.md 文件
- **执行步骤**：
  1. 使用迁移工具 `agentrule-migrate` 自动转换
  2. 旧版必填章节转换为新版可选章节
  3. 旧版字段名映射到新版字段名
  4. 旧版 ScriptsConfig.md 合并到新版格式
  5. 生成新版 AgentRule.md + ScriptsConfig.md
- **预期输出/结果**：旧版配置自动迁移到新版格式，无需手动重写

### 用例 4.22：记忆存储连接失败

- **前置条件**：Redis/GaussDB/ES 服务不可用
- **输入参数**：记忆存储连接异常
- **执行步骤**：
  1. MemoryRail 检测存储连接失败
  2. 记录错误日志
  3. 降级为无记忆模式（不注入记忆变量到 Prompt）
  4. Agent 继续运行（无记忆增强）
  5. 不抛异常，不终止 Agent 流程
- **预期输出/结果**：记忆存储失败时安全降级，Agent 以无记忆模式继续运行

### 用例 4.23：面客/强规则模式 TaskLoop 禁用

- **前置条件**：Agent 运行在面客/强规则模式下
- **输入参数**：isTaskLoopEnabled=false 配置
- **执行步骤**：
  1. DeepAgentPlus 检测面客/强规则模式
  2. 自动设置 `isTaskLoopEnabled=false`
  3. TaskLoop 不启用，避免与 EDPA Cascade 续轮冲突
  4. Agent 使用单轮推理 + 中断恢复模式
- **预期输出/结果**：面客模式下 TaskLoop 自动禁用，与 EDPA Cascade 续轮机制兼容

---

## 5. 依赖项

- **DeepAgent 框架**（com.openjiuwen.harness.deep_agent）：提供 ReAct 推理引擎、46+ 工具、26+ Rail、TaskLoop、MemoryRail、SecurityRail 等基础能力。版本需锁定，通过 DeepAgentPlus 隔离层适配 API 变动
- **openjiuwen SDK**（core.runner / core.single_agent / core.session / core.foundation / core.sys_operation / core.memory / core.context_engine / harness.rails.interrupt）：提供 Agent 运行时基础设施
- **a2a_service Runtime**（agent-runtime/applications/）：提供 HTTP 服务、A2A 协议栈、请求编排、SSE 流式推送。属于外部依赖，不在 EDPAgent 交付范围
- **VersatileAdapter Runtime**（agent-runtime/applications/）：提供 VA 工作流执行、A2A JSON-RPC 协议调用。属于外部依赖，不在 EDPAgent 交付范围
- **Redis / GaussDB / Elasticsearch**：记忆存储引擎，复用 DeepAgent 内置连接池
- **AgentRule.md 迁移工具**（agentrule-migrate）：旧版配置自动转换工具

---

## 附录 A：特性用例审查意见

> 审查依据：《EDAgent重构需求文档》+ 《edpagent-feature-checklist - 全量特征》（224 条特性）
> 审查重点：完整性（文档结构要素是否齐全）+ 完备性（测试覆盖是否充分）

---

## 一、完整性评价

### 缺失项

| 结构要素 | 状态 | 说明 |
|---------|------|------|
| **用例 ID** | ❌ 缺失 | 每个用例缺少唯一标识符（如 TC-INIT-001、TC-SCOPE-001 等），无法与测试执行结果、缺陷追踪系统关联 |
| **关联需求 ID** | ❌ 缺失 | 用例未关联到 feature-checklist 的特性编号（如 3.1.1、6.2.1 等），无法建立需求→用例的可追溯性矩阵 |
| **后置清理** | ❌ 缺失 | 所有用例均无后置清理步骤（teardown），如会话状态清理、Redis 数据清除、沙箱容器回收等 |
| **优先级/严重度** | ❌ 缺失 | 用例未标注优先级（P0/P1/P2）和严重度，无法指导测试执行顺序 |
| **用例间依赖关系** | ❌ 缺失 | 未声明用例之间的前置依赖（如 3.15 级联中断依赖 3.1 初始化完成），无法编排执行顺序 |
| **测试数据/样本** | ❌ 缺失 | 用例未提供具体的测试数据样本（如 AgentRule.md 示例文件、ScriptsConfig.md 示例内容），执行时需自行构造 |
| **通过/失败判定标准** | ❌ 缺失 | 预期结果为描述性文本，缺少可量化/可自动判定的通过标准（如"事件类型数量 = 17"、"中断恢复耗时 < 5s"） |

### 整体结论：**不完整**

文档具备前置条件、输入参数、执行步骤、预期结果等核心要素，但缺失用例 ID、关联需求 ID、后置清理、优先级、通过标准等关键结构要素，无法直接用于测试执行管理。

---

## 二、完备性评价

### 已覆盖的测试类型

- **正向/快乐路径**：30 个标准流程用例，覆盖了类架构继承、配置解析、开发模式、业务范围、规划步骤、任务依赖、Todo 管理、执行限制、话术系统、中断恢复、级联中断、MCP 沙箱、取消任务、Skill 加载、固定脚本、流事件、记忆系统、TaskLoop、Steering/FollowUp、AgentMode、权限、子 Agent 委派、工具体系、Rail 体系、配置渐进、新增能力等核心特性域
- **异常/负面路径**：23 个异常用例，覆盖了配置缺失/错误、依赖校验失败、迭代/工具/中断/输入超限、变量缺失、JSON 容错、Skill 过滤/目录缺失、VA/MCP 失败、工具冲突、API 变动、备选方案、旧版迁移、记忆存储失败、面客模式等异常场景

### 明显缺失的测试场景

以下按 feature-checklist 的 17 个特性域逐项对照，标注 **未覆盖或覆盖不足** 的特性：

#### 1. 隔离执行环境（34 条特性，覆盖度 ≈ 5%）— **严重缺失**

| 缺失场景 | 对应 checklist | 说明 |
|---------|---------------|------|
| 开发/生产双模式切换 | 5.1.1 | 本地直接执行 vs 远程沙箱隔离执行，配置一键切换 |
| 沙箱自动创建与多实例隔离 | 5.1.4, 5.1.5 | 服务启动时自动创建沙箱容器，多实例互不干扰 |
| 技能包自动打包/上传/解压 | 5.2.1~5.2.4 | Skills 目录打包为压缩包 → 上传到远程容器 → 自动解压 |
| 脚本执行超时保护 | 5.3.4 | 默认 60 秒超时，防止异常脚本无限挂起 |
| 工作目录自动切换 | 5.3.2 | 脚本执行前自动切换到技能目录 |
| 业务参数环境变量注入 | 5.3.3 | 业务参数、认证信息通过环境变量注入到脚本进程 |
| 多路结果解析 | 5.3.5 | 标准输出→业务数据，标准错误→日志，退出码→状态 |
| 空结果友好处理 | 5.3.6 | 脚本无输出时触发空结果提示话术 |
| 认证信息自动注入（不经过大模型） | 5.4.2 | 用户身份、Token 从会话上下文自动注入 |
| 敏感配置安全传递 | 5.4.3 | 大模型只传递变量名，实际值由系统从安全存储读取 |
| 默认敏感配置兜底 | 5.4.4 | 大模型未传递变量名时自动注入默认安全配置 |
| 跨轮次查询上下文保持 | 5.4.5 | 分页查询等场景下上次查询条件自动注入 |
| 归一化脚本按需配置 | 5.5.1 | 每个业务工具可配置专属归一化脚本 |
| 多源数据合并 | 5.5.2 | 推荐数据与银行系统数据合并为统一视图 |
| 执行结果状态判定 | 5.5.3 | 归一化脚本输出含成功/失败状态，自动选择话术 |
| 进度提示动态注入 | 5.5.4 | 归一化脚本可在结果中注入进度提示 |
| 进度提示与话术互斥 | 5.5.5 | 有进度提示时不再展示标准话术 |
| 无脚本数据透传 | 5.5.6 | 未配置归一化脚本时原始数据直接传递 |
| 内部字段自动清理 | 5.5.7 | 归一化结果中的内部字段在传给大模型前自动清除 |
| 脚本异常统一兜底 | 5.6.1 | 脚本执行失败时返回统一格式空结果 |
| 输出格式异常降级 | 5.6.3 | 归一化脚本输出格式异常时直接使用原始数据 |
| 沙箱不可用时降级 | 5.6.4 | 沙箱环境不可用时跳过脚本执行 |
| 错误上下文自动重置 | 5.6.5 | 脚本执行失败时自动清除上次查询条件 |

#### 2. Versatile 工作流调用（48 条特性，覆盖度 ≈ 10%）— **严重缺失**

| 缺失场景 | 对应 checklist | 说明 |
|---------|---------------|------|
| 统一业务操作入口 + 意图差异化路由 | 6.1.1, 6.1.3 | 余额查询/转账/购买通过同一工具调用，按意图参数路由 |
| 自然语言任务描述 | 6.1.2 | 智能体用自然语言描述任务 |
| 结果处理脚本配置 | 6.1.4 | 每次调用可指定归一化脚本 |
| 结果话术预声明 | 6.1.5 | 调用时声明成功/失败两种话术 |
| 进度提示上下文 | 6.1.6 | 为非中断场景传递进度提示所需上下文 |
| 任务描述智能补全 | 6.1.7 | 未提供任务描述时自动使用缓存描述 |
| 一级控制器调度工作流 | 6.1.8 | 通过 Orchestrator 调度 VA 工作流 |
| 直接调用工作流（跳过控制器） | 6.1.9 🚀 | 智能体跳过一级控制器直接调用 VA |
| 委托信息兜底恢复 | 6.2.6 | 框架状态回滚导致委托信息丢失时自动从备用存储重建 |
| 任务描述自动注入 | 6.3.1 | 将智能体生成的任务描述注入到工作流请求体 |
| 用户输入提取 | 6.3.2 | 从工作流请求体中提取用户原始输入 |
| 原始请求保留 | 6.3.3 | 首轮请求完整数据保存在会话中 |
| 工作流结果自动提取 | 6.4.1 | 从返回数据中自动提取核心业务数据 |
| 框架字段自动过滤 | 6.4.2 | 自动过滤工作流框架字段 |
| 多格式兼容解析 | 6.4.3 | 字符串自动尝试 JSON 解析，失败则原样包装 |
| 脚本输入自动构造 | 6.4.4 | 合并操作意图、任务描述、业务数据、进度上下文、历史查询条件 |
| 查询条件跨步传递 | 6.4.5 | 上一步查询条件自动传递给下一步 |
| 查询条件结果持久 | 6.4.6 | 归一化脚本输出的查询条件写回会话状态 |
| 隔离环境归一化 | 6.5.1 | 在沙箱中执行归一化脚本 |
| 双格式结果解析 | 6.5.2 | 支持"状态+数据"二元组与纯字典两种格式 |
| 推荐数据自动合并 | 6.5.6 | 上一步推荐结果自动注入到脚本输入 |
| 成功/失败自动路由 | 6.6.1 | 根据归一化结果状态自动选择话术 |
| 进度提示与话术互斥 | 6.6.2 | 有动态进度提示时跳过标准话术 |
| 进度提示实时推送 | 6.6.3 | 归一化脚本注入的进度提示直接推送到前端 |
| 话术解析异常容错 | 6.6.6 | 话术匹配失败时记录警告但不中断流程 |
| 委派前规则检查 | 6.7.1 | 在发起银行操作前检查调用次数是否超限 |
| 安全静态解析 | 6.7.2 | 只读取脚本中的次数限制配置，不执行脚本 |
| 脚本路径安全校验 | 6.7.3 | 只允许读取技能目录内的脚本配置 |
| 按意图精确匹配 | 6.7.4 | 每条次数限制规则可指定只对特定操作类型生效 |
| 操作次数计数 + 接近上限提醒 | 6.7.5 | 对同一操作调用次数计数，接近上限时提醒 |
| 规则独立计数 | 6.7.7 | 每条次数限制规则独立计数 |
| 进度提示前置推送 | 6.8.1 | 暂停等待前先推送"正在执行..."提示 |
| 完成提示延迟推送 | 6.8.2 | 工作流返回结果后再推送"操作完成"提示 |
| 事件时序严格保证 | 6.8.3 | 确保提示顺序始终为：开始→执行中→完成 |
| 意图与话术映射 | 6.8.4 | 不同操作类型对应不同的进度话术 |

#### 3. 长期记忆能力（15 条特性，覆盖度 ≈ 20%）— **显著缺失**

| 缺失场景 | 对应 checklist | 说明 |
|---------|---------------|------|
| 记忆能力开关 | 8.2 | `DPA_MEMORY_ENABLED=true` 启用，默认关闭 |
| 用户画像记忆 | 8.3 | 提取用户偏好、风险承受能力等结构化变量 |
| 语义记忆 | 8.4 | 提取事实性知识，支持语义相似度召回 |
| 情景记忆 | 8.5 | 记录具体交互事件，支持按时间回溯 |
| 会话摘要记忆 | 8.6 | 对历史对话生成压缩摘要 |
| 记忆变量白名单 | 8.9 | 配置允许注入的记忆变量，避免敏感信息泄露 |
| 独立记忆 LLM | 8.10 | 支持配置独立的记忆提取/总结模型 |
| 独立向量化模型 | 8.11 | 支持配置独立的 Embedding 模型 |
| 记忆作用域隔离 | 8.12 | `memory_scope_id` 实现多实例间记忆隔离 |
| 初始化失败降级 | 8.13 | 记忆引擎初始化失败时转为无记忆模式 |
| 记忆召回容错 | 8.14 | 记忆加载异常时注入空值而非阻断流程 |
| 异步写入不阻塞 | 8.15 | 记忆写入以异步任务执行，不阻塞主对话流程 |

#### 4. 人机交互 HITL（8 条特性，覆盖度 ≈ 50%）— **部分缺失**

| 缺失场景 | 对应 checklist | 说明 |
|---------|---------------|------|
| 敏感操作强制确认 | 9.6 | 购买理财、转账等必须经用户明确确认 |
| 取消确认流程 | 9.7 | 用户提出取消时先确认取消意图 |

#### 5. 实时交互体验（15 条特性，覆盖度 ≈ 50%）— **部分缺失**

| 缺失场景 | 对应 checklist | 说明 |
|---------|---------------|------|
| 工作流委派通知事件 | 10.9 | 触发外部工作流时推送委派事件 |
| 事件状态机 | 10.10 | 通过状态机精确控制事件推送时机 |
| 内部操作事件屏蔽 | 10.11 | 待办清单更新、文档读取等内部操作不推送到前端 |
| 大模型空响应检测 | 10.12 | 检测到大模型无输出时自动标记 |
| 主/子 Agent 动态规划过程展示 | 10.13 🚀 | 展示主智能体与子智能体的动态规划过程 |
| 多工作流并行运行状态展示 | 10.14 🚀 | 展示多个 VA 工作流的并行运行状态 |
| 动态思维链聚合展示 | 10.15 🚀 | 三层思维链聚合后统一输出 |

#### 6. 安全合规（9 条特性，覆盖度 ≈ 30%）— **显著缺失**

| 缺失场景 | 对应 checklist | 说明 |
|---------|---------------|------|
| 认证信息不经过大模型 | 11.3 | 用户身份、Token 由系统自动注入，大模型无法接触 |
| 敏感配置不经过大模型 | 11.4 | API 地址、密钥等大模型只传递变量名 |
| 密钥加密存储 | 11.7 | 配置文件中的 API Key 等支持加密存储，运行时自动解密 |
| 全链路操作审计 | 11.8 | 每次工具调用、大模型调用均有日志记录 |

#### 7. 服务化部署 + 大模型接入（7 条特性，覆盖度 = 0%）— **完全缺失**

| 缺失场景 | 对应 checklist | 说明 |
|---------|---------------|------|
| 容器化独立服务 | 2.1.1 | Docker 镜像交付，一键启动 |
| 面向企业的网关适配 | 2.1.2 | 支持企业统一 API 网关认证 |
| 流式响应接口 | 2.1.3 | 异步事件流形式返回对话结果 |
| 企业模型服务对接 | 2.4.1 | 通过企业统一模型网关接入国产大模型 |
| 自定义认证头注入 | 2.4.2 | 可配置 Token、用户 ID、额外请求头 |
| 空响应自动防护 | 2.4.3 | 推理模型偶发"只思考不回答"问题自动调整 |
| 敏感配置加密存储 | 2.4.4 | API Key 等敏感配置加密存储 |

#### 8. 会话持久化（3 条特性，覆盖度 ≈ 0%）— **完全缺失**

| 缺失场景 | 对应 checklist | 说明 |
|---------|---------------|------|
| 跨请求会话保持 | 2.3.1 | Redis 持久化对话状态，关闭页面后可恢复 |
| 中断后断点续行 | 2.3.2 | 工作流执行期间暂停，下次进入自动从断点恢复 |
| 任务取消后全量清理 | 2.3.3 | 取消后彻底清除 Redis、内存、上下文三层状态 |

#### 9. 协作与委派新增特性（2 条，覆盖度 = 0%）— **完全缺失**

| 缺失场景 | 对应 checklist | 说明 |
|---------|---------------|------|
| 主/子 Agent 异步执行 | 2.2.5 🚀 | 主智能体委派任务后无需阻塞等待 |
| 工作流与 Agent 异步并行 | 2.2.6 🚀 | VA 工作流调用与智能体调用异步并行执行 |

#### 10. 话术管理系统（10 条特性，覆盖度 ≈ 50%）— **部分缺失**

| 缺失场景 | 对应 checklist | 说明 |
|---------|---------------|------|
| 推荐结果话术 | 3.3.6 | 产品推荐成功时展示产品列表话术，无匹配时展示空结果引导 |
| 购买确认话术 | 3.3.7 | 购买前展示产品详情确认话术 |
| 异常场景话术 | 3.3.8 | 任务取消、会话超时、购买中断等异常场景标准话术 |
| 非中断进度提示 | 3.3.9 | 不中断对话的情况下发送操作进度提示 |
| 空结果友好提示 | 3.3.10 | 产品查询无结果时展示友好引导话术 |

#### 11. 规则与约束（8 条特性，覆盖度 ≈ 60%）— **部分缺失**

| 缺失场景 | 对应 checklist | 说明 |
|---------|---------------|------|
| 总结格式规范 | 3.1.6 | 约束最终回答的格式、最大长度、必含字段 |
| 规则热更新 | 3.1.8 | 修改配置即可调整行为，无需重新部署 |
| 全链路调用日志 | 3.2.7 | 记录每次大模型调用与工具调用的输入、输出、耗时 |

#### 12. 可靠性与容错（6 条特性，覆盖度 ≈ 30%）— **部分缺失**

| 缺失场景 | 对应 checklist | 说明 |
|---------|---------------|------|
| 大模型空响应防护 | 13.4 | 推理模型偶发空输出问题自动调整参数 |
| 脚本执行超时保护 | 13.5 | 脚本执行默认 60 秒超时 |
| 会话状态竞态防护 | 13.6 | 取消操作的状态清理在安全时机执行 |

#### 13. 可观测性（7 条特性，覆盖度 ≈ 15%）— **显著缺失**

| 缺失场景 | 对应 checklist | 说明 |
|---------|---------------|------|
| 工具调用全链路日志 | 14.2 | 每次工具调用的开始、结束、耗时均有日志 |
| 大模型调用记录 | 14.3 | 记录每次大模型调用的输入与输出 |
| 大模型原始输出日志 | 14.4 | 大模型完整输出不截断记录 |
| 空响应快速标记 | 14.5 | 大模型无输出时自动标记 |
| 业务技能调用追踪 | 14.6 | 技能加载、执行、耗时全链路追踪 |
| 模型网关诊断工具 | 14.7 | 独立诊断脚本验证模型网关连通性 |

#### 14. 部署与集成（9 条特性，覆盖度 = 0%）— **完全缺失**

| 缺失场景 | 对应 checklist | 说明 |
|---------|---------------|------|
| Docker 镜像部署 | 15.1 | 标准 Docker 镜像交付 |
| 框架服务集成 | 15.2 | 基于智能体运行时框架，提供标准 A2A 服务接口 |
| 低码平台集成 | 15.3 | 对接 VersatileAdapter 低码平台 |
| 离线打包部署 | 15.4 | Docker 镜像导出为离线包 |
| Linux/Windows 独立安装 | 15.5, 15.6 | 支持 Linux 和 Windows 环境部署 |
| Windows Docker 构建 | 15.7 | 在 Windows 构建机上构建 Linux 镜像 |
| 多环境部署指南 | 15.8 | 4 种部署方式的完整文档 |
| 零外部通信框架依赖 | 15.9 | 核心模块不依赖 A2A 通信 SDK |

#### 15. 边界值测试 — **完全缺失**

| 缺失场景 | 说明 |
|---------|------|
| max_iterations = 0 / 1 | 全局迭代限制的边界值 |
| todolist_steps = 0 步 / 1 步 / 最大步数 | Todo 步骤数量的边界值 |
| interrupt_timeout_seconds = 0 / 极小值 | 中断超时的边界值 |
| max_input_attempts = 0 / 1 | 输入重试次数的边界值 |
| scope.allowed = 空列表 / 单项 | 业务范围列表的边界值 |
| skills whitelist = 空列表 / 全量 | Skill 白名单的边界值 |
| AgentRule.md = 空文件 / 仅 YAML 无 Body / 仅 Body 无 YAML | 配置文件格式的边界值 |

#### 16. 组合与状态转换测试 — **完全缺失**

| 缺失场景 | 说明 |
|---------|------|
| 多 Rail 同时拦截 | CancelRail + ExecutionLimitRail 同时触发时的优先级处理 |
| 中断恢复后再中断 | ask_user 中断恢复后再次触发 ask_user 中断 |
| 级联中断 + ask_user 中断并行 | call_versatile 和 ask_user 在同一轮次先后触发 |
| TaskLoop 多轮 + Todo 步骤推进 | TaskLoop 循环中 Todo 步骤逐步完成的状态转换 |
| 记忆注入 + 话术模板组合 | 记忆变量注入到话术模板中的组合效果 |
| 配置热更新 + Agent 运行中 | Agent 运行过程中修改配置文件的行为 |

#### 17. 并发与依赖服务异常测试 — **覆盖不足**

| 缺失场景 | 说明 |
|---------|------|
| 多用户并发会话 | 多个用户同时与同一 Agent 实例交互 |
| 并发中断恢复 | 多个中断同时等待恢复 |
| Redis 连接池耗尽 | 高并发下 Redis 连接池资源耗尽 |
| GaussDB/ES 连接超时 | 记忆存储连接超时但不影响主流程 |
| VersatileAdapter 服务宕机 | VA 服务完全不可用时的降级处理 |

### 整体结论：**不足**

- 正向路径覆盖了核心架构特性，但 **feature-checklist 224 条特性中约 130 条（58%）未在用例中体现**
- 最大的覆盖缺口集中在：**隔离执行环境（34 条仅覆盖 2 条）、Versatile 工作流调用（48 条仅覆盖 5 条）**，这两个特性域合计 82 条特性，是业务核心但用例几乎空白
- 边界值、组合状态转换、并发测试三个维度完全缺失
- 部署与集成、服务化部署、大模型接入等运维级特性完全未覆盖

---

## 三、改进建议

### 建议 1：补充用例 ID 和关联需求 ID（优先级 P0）

为每个用例添加唯一标识符，并建立与 feature-checklist 特性编号的追溯关系。格式示例：

```
用例 ID：TC-ENV-001
关联需求：5.1.1（开发/生产双模式）、5.1.2（远程沙箱隔离执行）
```

建立需求→用例追溯矩阵，确保 224 条特性每条至少有 1 个用例覆盖。

### 建议 2：补充隔离执行环境全量用例（优先级 P0）

隔离执行环境是 EDPAgent 与 DeepAgent 差异最大的模块之一（34 条特性），当前仅用例 3.16 覆盖了 MCP 沙箱执行的表层逻辑。建议新增以下用例组：

- **TC-ENV-001~005**：运行模式（开发/生产切换、沙箱自动创建、多实例隔离、本地直接执行、沙箱不可用降级）
- **TC-ENV-006~010**：技能包管理（自动打包、自动上传、自动解压、解压目录配置、技能文档按需查阅）
- **TC-ENV-011~016**：脚本执行控制（统一调用接口、工作目录切换、环境变量注入、超时保护、多路结果解析、空结果处理）
- **TC-ENV-017~022**：输入数据安全构造（业务参数传递、认证信息注入、敏感配置安全传递、默认配置兜底、跨轮次上下文保持、统一序列化）
- **TC-ENV-023~029**：多源数据归一化（归一化脚本配置、多源数据合并、结果状态判定、进度提示注入、进度提示与话术互斥、无脚本透传、内部字段清理）
- **TC-ENV-030~034**：异常容错（脚本异常兜底、无输出检测、输出格式异常降级、沙箱不可用降级、错误上下文重置）

### 建议 3：补充 Versatile 工作流调用全量用例（优先级 P0）

Versatile 工作流调用是 EDPAgent 最复杂的业务模块（48 条特性），当前仅用例 3.15 和 4.14 覆盖了级联中断的表层逻辑。建议新增以下用例组：

- **TC-VA-001~009**：通用工作流调用（统一入口+意图路由、自然语言描述、业务意图分类、结果处理脚本、结果话术预声明、进度提示上下文、任务描述补全、一级控制器调度、直接调用）
- **TC-VA-010~016**：委托与中断恢复（业务操作暂停、委托信息记录、委托请求推送、外部系统执行、执行结果恢复、委托信息兜底恢复、无外部依赖设计）
- **TC-VA-017~020**：请求格式适配（任务描述注入、用户输入提取、原始请求保留、请求隔离）
- **TC-VA-021~026**：续轮数据处理（工作流结果提取、框架字段过滤、多格式兼容、脚本输入构造、查询条件跨步传递、查询条件持久）
- **TC-VA-027~032**：归一化脚本执行（隔离环境归一化、双格式解析、无脚本透传、无执行环境降级、解析异常降级、推荐数据合并）
- **TC-VA-033~038**：话术智能路由（成功/失败路由、进度提示与话术互斥、进度提示实时推送、内部字段清理、话术模板统一管理、话术解析异常容错）
- **TC-VA-039~045**：操作次数守护（委派前规则检查、安全静态解析、脚本路径安全校验、按意图精确匹配、操作次数计数、超限自动终止、规则独立计数）
- **TC-VA-046~049**：事件时序控制（进度提示前置推送、完成提示延迟推送、事件时序保证、意图与话术映射）

### 建议 4：补充长期记忆能力用例（优先级 P1）

当前仅用例 3.21 和 4.22 覆盖了记忆系统的表层逻辑。建议新增：

- **TC-MEM-001**：记忆能力开关（启用/关闭切换）
- **TC-MEM-002~004**：三类记忆（用户画像、语义记忆、情景记忆）的写入与召回
- **TC-MEM-005**：会话摘要记忆的生成与注入
- **TC-MEM-006**：记忆变量白名单过滤
- **TC-MEM-007~008**：独立记忆 LLM / 独立向量化模型配置
- **TC-MEM-009**：记忆作用域隔离（多实例间互不干扰）
- **TC-MEM-010~012**：记忆容错（初始化失败降级、召回异常容错、异步写入不阻塞）

### 建议 5：补充边界值测试用例（优先级 P1）

为所有数值型/集合型配置参数添加边界值测试：

- max_iterations：0（不允许任何迭代）、1（仅允许单次迭代）、极大值
- todolist_steps：0 步（无 Todo）、1 步（单步骤）、最大步数
- interrupt_timeout_seconds：0（立即超时）、极小值、极大值
- max_input_attempts：0（不允许任何输入）、1（仅允许单次输入）
- scope.allowed：空列表（拒绝所有）、单项列表
- skills whitelist：空列表、全量列表
- AgentRule.md：空文件、仅 YAML 无 Body、仅 Body 无 YAML

### 建议 6：补充组合与状态转换测试用例（优先级 P1）

- 多 Rail 同时拦截的优先级处理与冲突解决
- 中断→恢复→再中断的状态转换链
- TaskLoop 多轮中 Todo 步骤逐步推进的状态机
- 记忆注入 + 话术模板的组合效果验证
- 配置热更新与 Agent 运行中的动态生效

### 建议 7：补充并发与依赖服务异常测试用例（优先级 P2）

- 多用户并发会话的状态隔离
- 并发中断恢复的竞态条件处理
- Redis/GaussDB/ES 连接池耗尽与超时
- VersatileAdapter 服务宕机的降级处理

### 建议 8：补充安全合规用例（优先级 P1）

- 认证信息不经过大模型的验证（Token/用户 ID 仅在系统层注入）
- 敏感配置安全传递验证（大模型输出中不含实际密钥值）
- 密钥加密存储与运行时解密验证
- 全链路操作审计日志完整性验证

### 建议 9：补充会话持久化用例（优先级 P1）

- 跨请求会话保持（关闭页面后恢复对话进度）
- 中断后断点续行（VA 工作流中断后下次自动恢复）
- 任务取消后全量清理（Redis + 内存 + 上下文三层状态清除）

### 建议 10：补充后置清理步骤（优先级 P0）

为每个用例添加后置清理步骤，确保测试环境可重复使用：

- 清除 Redis 中的测试会话数据
- 回收沙箱容器实例
- 重置 AgentRule.md 配置文件
- 清除 session state 中的测试变量

### 建议 11：补充可量化通过标准（优先级 P1）

将预期结果从描述性文本升级为可量化判定标准：

- "17 种 AgentEvent 全部正确输出，事件类型数量 = 17"
- "中断恢复耗时 < 5s"
- "话术模板变量替换后无 {varName} 残留"
- "记忆召回结果与写入内容语义相似度 > 0.8"
- "全局迭代计数器值 = max_iterations 时自动终止"

### 建议 12：补充部署与集成用例（优先级 P2）

虽然部署与集成属于运维层面，但作为重构交付的完整性验证，建议覆盖：

- Docker 镜像构建与一键启动
- A2A 服务接口标准协议验证
- VersatileAdapter 低码平台对接验证
- 离线打包部署流程验证

---

## 四、特性覆盖度统计

| 特性域 | checklist 特性数 | 用例覆盖数 | 覆盖率 | 评级 |
|--------|:---:|:---:|:---:|:---:|
| 一、产品定位 | 3 | 2 | 67% | ⚠️ |
| 二、运行架构 | 13 | 4 | 31% | ❌ |
| 三、规则与约束 | 26 | 16 | 62% | ⚠️ |
| 四、任务规划与进度管理 | 7 | 5 | 71% | ⚠️ |
| 五、隔离执行环境 | 34 | 2 | 6% | ❌❌ |
| 六、Versatile 工作流调用 | 48 | 5 | 10% | ❌❌ |
| 七、业务工具集 | 10 | 8 | 80% | ✅ |
| 八、长期记忆能力 | 15 | 3 | 20% | ❌ |
| 九、人机交互 | 8 | 4 | 50% | ⚠️ |
| 十、实时交互体验 | 15 | 8 | 53% | ⚠️ |
| 十一、安全合规 | 9 | 3 | 33% | ❌ |
| 十二、灵活配置 | 8 | 6 | 75% | ✅ |
| 十三、可靠性与容错 | 6 | 2 | 33% | ❌ |
| 十四、可观测性 | 7 | 1 | 14% | ❌ |
| 十五、部署与集成 | 9 | 0 | 0% | ❌❌ |
| 十六、测试与质量 | 5 | 0 | 0% | ❌ |
| **合计** | **224** | **~94** | **~42%** | **不足** |

> 评级标准：✅ ≥75% / ⚠️ 50%~74% / ❌ 25%~49% / ❌❌ <25%

**最需优先补充的 3 个特性域**：
1. **Versatile 工作流调用**（48 条，覆盖率 10%）— 业务核心，用例几乎空白
2. **隔离执行环境**（34 条，覆盖率 6%）— 与 DeepAgent 差异最大，用例几乎空白
3. **长期记忆能力**（15 条，覆盖率 20%）— 三存储引擎协同，用例仅覆盖表层

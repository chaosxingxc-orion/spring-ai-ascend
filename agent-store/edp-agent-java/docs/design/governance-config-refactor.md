# 场景 Governance 配置重构方案

## 1. 总体架构

```
框架仓（团队维护）                          场景仓（客户维护）
┌──────────────────────────┐      ┌──────────────────────────┐
│ engine/src/main/resources│      │ scenarios/wealth-demo/   │
│   /governance/           │      │   /governance/           │
│   ├── planrule.yaml   ←─┼──────┼── planrule.yaml          │
│   ├── actrule.yaml    ←─┼──────┼── actrule.yaml           │
│   ├── scriptconfig.yaml←┼──────┼── scriptconfig.yaml      │
│   └── _reference/     ←─┼─参考─┼─ (开发者参考此目录)      │
│       ├── planrule.yaml  │      │   /skills/               │
│       ├── actrule.yaml   │      │   scenario-config.yaml   │
│       └── scriptconfig.  │      └──────────────────────────┘
│           yaml            │
└──────────────────────────┘

运行时加载顺序：
  1. 加载 framework governance → 得到全量默认值
  2. 加载 scenario governance  → 得到增量差异
  3. mergeScenarioConfig()    → 字段级合并（场景优先）
```

## 2. 核心设计决策

| 决策项 | 结论 | 理由 |
|---|---|---|
| **文件名** | 框架和场景三名一致：`planrule.yaml` / `actrule.yaml` / `scriptconfig.yaml` | 解析器统一、场景开发者无认知负担 |
| **解析函数** | 共用 `GovernanceConfigLoader.load(Path)` | 目录无关、schema 一致，已实现 |
| **场景覆盖策略** | 空模板 + 增量 —— 空文件 = 全量继承框架，场景只写差异字段 | 减少冗余、降低漂移风险 |
| **参考来源** | `engine/src/main/resources/governance/_reference/` 下的全量注释 YAML | 随框架仓发布，场景开发者唯一权威参考 |
| **两仓关系** | 独立演进，框架仓不感知场景仓 | 客户自主开发后发布到自己的场景仓 |
| **迁移范围** | 先 wealth-demo，hz-zhidaitong 暂不动 | 逐个场景推进 |
| **发布策略** | 框架仓（团队维护） + 场景仓（客户维护），独立发布 | 二者独立演进 |

## 3. 字段合并策略（三类）

已在 `GovernanceConfig.mergeScenarioConfig()` 中实现：

| 策略 | 行为 | 适用字段 |
|---|---|---|
| **替代式覆盖** | 场景有值 → 完全替换框架值 | `scope`、`supplementaryPrompt`、`generalScripts`、`summary` |
| **继承式覆盖** | 场景有值 → 覆盖；无值 → 继承框架 | `role`、`description`、`maxSubtasks`、`thinkChunkScripts` 等多数字段 |
| **叠加合并** | 场景值追加到框架值后面（去重） | `allowedTools` |

未来可按需引入第四种：

| **框架保护** | 即使场景配置了也忽略，强制使用框架值 | 暂无，预留给安全约束字段（如 `maxSteps` 上限） |

## 4. 全量字段清单

### 4.1 planrule.yaml — PlanRuleConfig

| YAML 路径 | 类型 | 当前框架默认值 | 合并策略 | 场景可配置 |
|---|---|---|---|---|
| `planrule.role` | String | "你的身份是通用动态规划智能体" | 继承式覆盖 | 是 |
| `planrule.description` | String | (见框架文件) | 继承式覆盖 | 是 |
| `planrule.scope.allowed` | String | "" | 替代式覆盖 | 是 |
| `planrule.scope.denied` | String | "" | 替代式覆盖 | 是 |
| `planrule.scope.out_of_scope_message` | String | "当前请求暂不在可处理范围内。" | 替代式覆盖 | 是 |
| `planrule.supplementary_prompt` | String | (ReAct 循环协议等，约 62 行) | 替代式覆盖 | 是 |

### 4.2 actrule.yaml — ActRuleConfig

| YAML 路径 | 类型 | 当前框架默认值 | 合并策略 | 场景可配置 |
|---|---|---|---|---|
| `actrule.max_subtasks` | Integer | 50 | 继承式覆盖 | 是 |
| `actrule.replan_enabled` | Boolean | true | 继承式覆盖 | 是 |
| `actrule.max_replan_count` | Integer | 3 | 继承式覆盖 | 是 |
| `actrule.max_steps` | Integer | 100 | 继承式覆盖 | 是 |
| `actrule.retry_enabled` | Boolean | true | 继承式覆盖 | 是 |
| `actrule.max_retry_count` | Integer | 3 | 继承式覆盖 | 是 |
| `actrule.allowed_tools` | List\<String\> | [bash, skill_tool, call_versatile, call_mcp, ask_user, todo_create, todo_modify, todo_list, todo_get, cancel_task] | 叠加合并 | 是 |
| `actrule.enable_task_loop` | Boolean | true | 继承式覆盖 | 是 |
| `actrule.skill_mode` | String | "all" | 继承式覆盖 | 是 |
| `actrule.tool_limits` | Map\<String,Integer\> | {call_versatile:100, call_mcp:100, ask_user:100, execute_cmd:100} | 继承式覆盖 | 是 |

### 4.3 scriptconfig.yaml — ScriptConfig

| YAML 路径 | 类型 | 当前框架默认值 | 合并策略 | 场景可配置 |
|---|---|---|---|---|
| `scriptconfig.general_scripts.tool_start` | String | "正在调用：{tool_name}" | 替代式覆盖 | 是 |
| `scriptconfig.general_scripts.tool_end` | String | "{tool_name} 执行完成" | 替代式覆盖 | 是 |
| `scriptconfig.general_scripts.todo_start` | String | "开始执行：{title}" | 替代式覆盖 | 是 |
| `scriptconfig.general_scripts.todo_end` | String | "{title} 已完成" | 替代式覆盖 | 是 |
| `scriptconfig.general_scripts.todolist_start` | String | "规划任务清单" | 替代式覆盖 | 是 |
| `scriptconfig.general_scripts.todolist_end` | String | "todolist规划完成" | 替代式覆盖 | 是 |
| `scriptconfig.general_scripts.interrupt_start` | String | "需要您确认以下信息" | 替代式覆盖 | 是 |
| `scriptconfig.general_scripts.request_start` | String | "您的请求已收到。" | 替代式覆盖 | 是 |
| `scriptconfig.general_scripts.planning_start` | String | "我们正在为您进行规划。" | 替代式覆盖 | 是 |
| `scriptconfig.general_scripts.task_cancelled` | String | "好的，已为您取消当前操作。如需其他帮助，请随时告诉我。" | 替代式覆盖 | 是 |
| `scriptconfig.general_scripts.cancel_confirm` | String | "确认要取消当前操作吗？" | 替代式覆盖 | 是 |
| `scriptconfig.general_scripts.out_of_scope` | String | "正在学习中，暂不支持该业务。" | 替代式覆盖 | 是 |
| `scriptconfig.think_chunk_scripts.think_chunk_mode` | String | "fixed_script" | 继承式覆盖 | 是 |
| `scriptconfig.think_chunk_scripts.think_chunk_fixed_scripts.enabled` | Boolean | true | 继承式覆盖 | 是 |
| `scriptconfig.think_chunk_scripts.think_chunk_fixed_scripts.chars_per_frame` | Integer | 4 | 继承式覆盖 | 是 |
| `scriptconfig.think_chunk_scripts.think_chunk_fixed_scripts.tokens_between_frames` | Integer | 2 | 继承式覆盖 | 是 |
| `scriptconfig.think_chunk_scripts.think_chunk_fixed_scripts.min_interval_ms` | Integer | 50 | 继承式覆盖 | 是 |
| `scriptconfig.think_chunk_scripts.think_chunk_fixed_scripts.default_scripts` | List\<String\> | ["正在分析您的需求..."] | 继承式覆盖 | 是 |
| `scriptconfig.think_chunk_scripts.think_chunk_fixed_scripts.execution_scripts` | List\<String\> | ["正在分析执行结果..."] | 继承式覆盖 | 是 |
| `scriptconfig.think_chunk_scripts.think_chunk_fixed_scripts.resume_scripts` | List\<String\> | ["当前业务步骤已为您处理完毕"] | 继承式覆盖 | 是 |
| `scriptconfig.summary.format` | String | "需求概述→规划过程→任务执行情况→结果汇总→异常说明" | 替代式覆盖 | 是 |
| `scriptconfig.summary.max_length` | Integer | 500 | 替代式覆盖 | 是 |
| `scriptconfig.summary.required_fields` | List\<String\> | [用户查询, 执行步骤, 结果状态] | 替代式覆盖 | 是 |

## 5. 参考 YAML 目录

### 5.1 路径

```
engine/src/main/resources/governance/_reference/
├── planrule.yaml
├── actrule.yaml
└── scriptconfig.yaml
```

### 5.2 定位

场景开发者查询"我能配置什么、怎么配、能改成什么值"的**唯一权威来源**。

### 5.3 每个字段的注释格式

```yaml
# @用途: 字段含义
# @策略: 替代式覆盖 | 继承式覆盖 | 叠加合并 | 框架保护
# @类型: string | integer | boolean | list | map
# @约束: 合法取值范围
# @默认: 框架默认值
field_name: value
```

### 5.4 内容

- 全量字段（POJO 定义的所有字段，不只是当前框架 YAML 里有值的）
- 每个字段标注上述注释
- 填真实默认值（不是占位符），场景开发者可直接复制删减使用

## 6. 自动化校验测试（ReferenceYamlConsistencyTest）

### 6.1 目的

防止框架开发者给 POJO 新增字段后忘记同步更新参考 YAML，导致参考 YAML 与代码脱节。

### 6.2 逻辑

```
ReferenceYamlConsistencyTest
  ├── testPlanruleReferenceCoversAllFields()
  │     → 加载 _reference/governance/planrule.yaml
  │     → 反序列化为 PlanRuleConfig
  │     → 逐个断言每个字段非 null
  │     → POJO 新增字段但参考 YAML 未更新 → 测试失败，精确提示缺失字段名
  ├── testActruleReferenceCoversAllFields()  （同上）
  └── testScriptconfigReferenceCoversAllFields() （同上）
```

### 6.3 影响

- 删除此测试：系统功能不受影响，但参考 YAML 会随着时间与 POJO 脱节
- 保留此测试：CI 自动拦截漂移，框架开发者在新增字段时被迫同步更新参考 YAML

## 7. 场景开发者工作流

```
1. 打开 framework-repo/engine/src/main/resources/governance/_reference/
        └── 阅读三个 YAML 文件
        └── 了解所有可配字段、类型、约束、默认值

2. 在自己的场景目录下创建 governance/ ，只写差异：
        scenarios/my-scenario/
            └── governance/
                ├── planrule.yaml       ← 只写需要改的字段
                ├── actrule.yaml        ← 只写需要改的字段
                └── scriptconfig.yaml   ← 只写需要改的字段
                未写的字段 → 自动继承框架默认值

3. 示例（参考 actrule.yaml，场景只覆盖 max_subtasks）：
        参考文件内容:
            # @用途: 限制单层最大子任务数量
            # @策略: 继承式覆盖
            # @类型: integer
            # @约束: 1 ≤ 值 ≤ 200
            # @默认: 50
            max_subtasks: 50
        
        场景实际配置:
            actrule:
              max_subtasks: 30
        其他字段如 max_steps、retry_enabled 等自动继承框架默认值
```

## 8. 执行计划

| 步骤 | 内容 | 状态 |
|---|---|---|
| 3 | wealth-demo 场景 governance 三个空文件确认（已满足空模板 + 增量） | 待确认 |
| 1 | 在 `engine/src/main/resources/governance/_reference/` 下创建三个全量注释 YAML | 待执行 |
| 2 | 实现 `ReferenceYamlConsistencyTest` 自动化校验 | 待执行 |

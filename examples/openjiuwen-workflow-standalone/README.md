# OpenJiuwen Workflow 独立示例

基于 OpenJiuwen 原生 Workflow API 的独立运行示例，展示多步骤 DAG 工作流 + 人工确认中断/恢复能力。

## 场景：智能文章摘要审核

5 步线性工作流：

```
[Start] → [LLM: 分析主题] → [Tool: 搜索相关信息] → [LLM: 生成摘要] → [Questioner: 人工确认] → [LLM: 最终输出] → [End]
```

## 特性展示

| 特性 | 实现 |
|------|------|
| **Multi-step DAG** | 5 个节点按拓扑顺序执行，通过 `addConnection()` 编排 |
| **Tool 节点** | `MockSearchTool` 作为 DAG 节点（`ToolComponent`） |
| **中断/恢复** | `QuestionerComponent` 在执行到人工确认节点时挂起，用户输入后继续 |
| **流式进度** | 每次 `invoke()` 返回当前的 output chunks |
| **编程式构建** | 纯 Java 代码构建 DAG，无需 YAML 配置文件 |

## 快速开始

### 前置条件

- JDK 21+
- 可用的 LLM API 端点（OpenAI 兼容接口）

### 配置

编辑 `src/main/resources/application.properties`，或通过环境变量覆盖：

```bash
export SAA_LLM_API_KEY="your-api-key"
export SAA_LLM_API_BASE="http://localhost:4000/v1"
export SAA_LLM_MODEL="gpt-4"
```

### 编译运行

```bash
# 安装根项目（首次需要）
mvn install -DskipTests -f pom.xml

# 编译
mvn compile -f examples/openjiuwen-workflow-standalone/pom.xml

# 运行
mvn exec:java -f examples/openjiuwen-workflow-standalone/pom.xml
```

### 交互流程

1. 工作流接收一篇预设的 AI 医疗文章
2. LLM 分析文章主题和关键信息 → 输出分析结果
3. Tool 节点返回模拟的搜索结果
4. LLM 结合分析和搜索生成摘要
5. **工作流挂起** → 终端显示确认提示："请审核以上摘要质量..."
6. 用户输入 `yes`（批准）或 `no`（驳回）
7. 工作流恢复 → LLM 生成最终输出
8. 打印最终结果

## 项目结构

```
openjiuwen-workflow-standalone/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/huawei/ascend/examples/openjiuwen/workflow/
    │   ├── ArticleSummarizerWorkflow.java   # 主程序：构建 DAG、执行、中断处理
    │   └── tools/
    │       └── MockSearchTool.java           # 模拟搜索工具
    └── resources/
        └── application.properties            # LLM 模型配置
```

## 关键 API

### 构建 Workflow DAG

```java
Workflow wf = new Workflow(WorkflowCard.builder()...build());

// 添加节点（每个节点有 inputsSchema 声明其输入来源）
wf.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
wf.addWorkflowComp("analyze", new LLMComponent(config), Map.of("article", "${start.query}"), null);
wf.addWorkflowComp("search", toolComp, Map.of("query", "${analyze.text}"), null);
wf.addWorkflowComp("confirm", new QuestionerComponent(qConfig), Map.of(...), null);
wf.setEndComp("end", new End(), Map.of("result", "${finalize.text}"), null);

// 连接节点
wf.addConnection("start", "analyze");
wf.addConnection("analyze", "search");
// ...
wf.addConnection("finalize", "end");
```

### 执行与中断恢复

```java
// 执行（同步阻塞）
WorkflowOutput output = workflow.invoke(inputs, session, null);

// 检测挂起
if (output.getState() == WorkflowExecutionState.INPUT_REQUIRED) {
    // 提取 InteractionOutput，收集用户输入
    InteractiveInput resume = new InteractiveInput();
    resume.update(nodeId, Map.of("answer", userResponse));
    // 用同一 session 重新 invoke
    output = workflow.invoke(resume, session, null);
}
```

### 数据路由

节点间数据通过 `inputsSchema` 中的 `${nodeId.fieldName}` 语法传递：

```java
// analyze 节点的输入 "article" 来自 start 节点的 "query" 输出
Map.of("article", "${start.query}")

// summarize 节点的输入 "analysis" 来自 analyze 节点的 "text" 输出
Map.of("analysis", "${analyze.text}", "search_results", "${search.text}")
```

## 对比：Workflow vs ReActAgent

| | ReActAgent | Workflow |
|------|-----------|----------|
| 执行模型 | LLM 自主循环（think→act→observe） | 显式 DAG 编排 |
| 步骤控制 | 模型决定何时调用工具 | 预定义拓扑，确定性的执行顺序 |
| 人工交互 | 需自定义 InterruptRail | 原生 `QuestionerComponent` |
| 适用场景 | 开放域对话、灵活工具调用 | 结构化流水线、审批流程 |

## 与 agent-runtime 示例的区别

本示例是 **独立运行** 的 Java 程序，直接调用 OpenJiuwen Workflow API。
相比之下，`examples/agent-runtime-openjiuwen-simple/` 基于 Spring Boot + agent-runtime 框架，
通过 A2A 协议暴露 ReActAgent。

此示例不依赖 Spring Boot 或 agent-runtime，专注于展示 OpenJiuwen Workflow 原生能力。

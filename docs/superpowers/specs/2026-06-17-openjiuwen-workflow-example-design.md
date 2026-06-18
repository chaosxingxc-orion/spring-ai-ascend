# OpenJiuwen Workflow Standalone Example — Design Spec

**Date**: 2026-06-17
**Branch**: `feat/workflow`
**Status**: approved

## 1. Goal

Create a standalone Java example (`examples/openjiuwen-workflow-standalone/`) that demonstrates
OpenJiuwen's native **multi-step Workflow DAG** capabilities — showing how to compose LLM calls,
tool invocations, conditional branching, and human-in-the-loop interrupts into a single runnable
workflow. The example calls `Runner.runWorkflow()` directly without the agent-runtime abstraction
layer, keeping the focus on pure OpenJiuwen workflow APIs.

Key characteristics to demonstrate:
- **Multi-step DAG**: explicit node graph with Start/End, not a single ReActAgent loop
- **Tool invocation**: a mock tool as a DAG node
- **Human-in-the-loop interrupt/resume**: `QuestionerComponent` suspends execution, waits for
  terminal input, then resumes
- **Streaming progress**: per-node chunk output so the user observes step-by-step progress

*BranchComponent intentionally excluded* — user wants fast prototype with linear flow.
Branching adds cognitive load without additional demonstration value for the initial prototype.

## 2. Scenario — "Article Summarizer with Human Review" (simplified)

A linear 5-step pipeline: analyze → search → summarize → human confirm → finalize:

```
[Start]
   │
   ▼
[LLM: analyze_topic]    — 提取文章主题和关键信息
   │
   ▼
[Tool: search_related]  — 搜索相关信息（mock）
   │
   ▼
[LLM: generate_summary] — 结合搜索结果生成摘要
   │
   ▼
[Questioner: confirm]   — 人工确认摘要质量（approve/reject）
   │
   ▼
[LLM: finalize]         — 根据确认结果生成最终输出
   │
   ▼
[End]
```

### Workflow components used

| Component | Role |
|-----------|------|
| `LLMComponent` × 3 | analyze, summarize, finalize |
| `ToolComponent` | execute mock search |
| `QuestionerComponent` | suspend for human approve/reject input |
| `Start` / `End` | entry and exit nodes |

### Execution flow (user perspective)

1. Workflow receives article text as input
2. [Chunk] LLM extracts topic and key points
3. [Chunk] Tool returns mock search results
4. [Chunk] LLM generates summary based on analysis + search
5. **[SUSPEND]** Questioner prompts human: "Approve? (yes/no)"
6. User types response in terminal
7. [Chunk] LLM generates final output based on human decision
8. Workflow completes

## 3. Project Structure

```
examples/openjiuwen-workflow-standalone/
├── pom.xml
├── README.md
└── src/
    └── main/
        ├── java/com/huawei/ascend/examples/openjiuwen/workflow/
        │   ├── ArticleSummarizerWorkflow.java  # main() — builds DAG, runs, handles interrupt/resume
        │   └── tools/
        │       └── MockSearchTool.java          # mock search tool
        └── resources/
            └── application.properties         # model config (provider, api-key, base-url, model)
```

## 4. Key Design Decisions

### 4.1 Build DAG programmatically (not YAML)

The workflow graph is constructed in Java code using `Workflow.addWorkflowComp()` /
`Workflow.linkComp()` / `Workflow.setStartComp()` / `Workflow.setEndComp()`. This makes the
topology explicit and readable without requiring a YAML parser. The code itself serves as
documentation of the API.

### 4.2 Interrupt/resume via QuestionerComponent

`QuestionerComponent` is OpenJiuwen's built-in human-in-the-loop node. When the workflow reaches
it, execution suspends with `WorkflowExecutionState.INPUT_REQUIRED`. The example code:
1. Calls `workflow.invoke()` or `workflow.stream()` in a loop
2. Detects `INPUT_REQUIRED` state
3. Reads user input from stdin
4. Passes input back via session/resume mechanism
5. Continues execution

### 4.3 Streaming for step-by-step visibility

Use `StreamingChunkPublisher` or `Workflow.stream()` so each component's output appears as a
chunk. The example prints chunk metadata (component id, state) to the terminal so the user sees
workflow progress.

### 4.4 Mock tool for portability

`WebSearchTool` is a mock that returns canned results. No real API key or network call needed.
Users can replace it with a real search tool by changing the tool implementation.

### 4.5 Model configuration via application.properties

LLM provider, API key, base URL, and model name are read from properties with environment-variable
fallbacks, matching the pattern used in `examples/agent-runtime-openjiuwen-simple/`.

## 5. Dependencies

Same OpenJiuwen Maven dependency (`com.openjiuwen:openjiuwen-agent-core-java`) already used by
existing examples. No additional framework dependencies beyond what `agent-runtime-openjiuwen-simple`
already pulls in.

## 6. Out of Scope

- agent-runtime engine adapter integration (separate future example)
- YAML DSL workflow definition (separate future example)
- Redis-based checkpoint persistence (use in-memory for simplicity)
- Multi-workflow orchestration (`WorkflowAgent`)
- ReActAgent-as-DAG-node (not supported by OpenJiuwen — see research notes)

## 7. Success Criteria

- `mvn exec:java` runs the workflow end-to-end
- LLM nodes produce meaningful output for a sample article
- Tool node returns mock search results
- Questioner suspends and waits for stdin input
- After user input, workflow resumes and completes
- Each step prints its output as a labeled chunk
- README.md explains the scenario, components, and how to run it

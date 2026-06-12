# agent-sdk example

This directory is a standalone runnable Java example project for `agent-sdk`.
It demonstrates the current OpenJiuwen-only SDK path with real model calls:

```text
YAML
  -> AgentFactory.toReactAgent(...) / toDeepAgent(...)
  -> ReActAgent / DeepAgent
  -> custom Java tools + local skills
  -> OpenJiuwen agent invocation
  -> proof verification
```

## Contents

- `openjiuwen/agent.yaml`: OpenJiuwen ReAct YAML.
- `openjiuwen/deepagent.yaml`: OpenJiuwen DeepAgent YAML.
- `OpenJiuwenReactAgentSdkExample`: builds and invokes a `ReActAgent` from `openjiuwen/agent.yaml`.
- `OpenJiuwenDeepAgentSdkExample`: builds a `DeepAgent` from `openjiuwen/deepagent.yaml` and calls `DeepAgent.run(...)` to drive the real internal agent execution.
- `OpenJiuwenExampleSupport`: shared OpenJiuwen execution helper.
- `com.huawei.ascend.agentsdk.example.tools`: Java classpath tools used by both YAML files.
- `skills/`: local OpenJiuwen skill directories used by both YAML files.

Both YAML files register the same custom Java tools:

- `readFile`: `ReadFileTool.read(...)`, reads local `SKILL.md` files and returns `readFile-java-tool-executed`.
- `queryOrder`: `QueryOrderTool.query(...)`, returns `queryOrder-java-tool-executed`.
- `calcDiscount`: `CalcDiscountTool.calculate(...)`, returns `calcDiscount-java-tool-executed`.

Both YAML files also register two local skills:

- `order-analysis`, which requires the final answer to include `ORDER_ANALYSIS_SKILL_USED`.
- `report-writing`, which requires the final answer to include `REPORT_WRITING_SKILL_USED`.

Java tools are referenced by class and method. The YAML tool reference does not use a source `path`:

```yaml
ref:
  type: file
  class: com.huawei.ascend.agentsdk.example.tools.QueryOrderTool
  method: query
```

## Prerequisites

- JDK 21.
- Maven.
- A DeepSeek-compatible API key exported as `DEEPSEEK_API_KEY`.
- `com.openjiuwen:agent-core-java:0.1.12` available to Maven.

The example YAML uses:

```yaml
model:
  provider: OpenAI
  name: deepseek-chat
  baseUrl: https://api.deepseek.com
  apiKey: ${DEEPSEEK_API_KEY}
```

## Install the SDK

Run from the repository root:

```bash
mvn -f agent-sdk/pom.xml -DskipTests install
```

## Run ReAct

```bash
mvn -f examples/agent-sdk-example/pom.xml compile exec:java "-Dexample.mainClass=com.huawei.ascend.agentsdk.example.OpenJiuwenReactAgentSdkExample"
```

## Run DeepAgent

```bash
mvn -f examples/agent-sdk-example/pom.xml compile exec:java "-Dexample.mainClass=com.huawei.ascend.agentsdk.example.OpenJiuwenDeepAgentSdkExample"
```

`DeepAgent.invoke(...)` in OpenJiuwen 0.1.12 returns the normalized task payload unless task loop mode is enabled. This example uses `DeepAgent.run(...)` so the real model/tool execution path is exercised.

## Proof of tool and skill use

The examples fail with `IllegalStateException` if the real model call does not prove all of these:

- `QueryOrderTool.query(...)` was invoked at least once.
- `CalcDiscountTool.calculate(...)` was invoked at least once.
- The model result contains `queryOrder-java-tool-executed`.
- The model result contains `calcDiscount-java-tool-executed`.
- The model result contains `ORDER_ANALYSIS_SKILL_USED`.
- The model result contains `REPORT_WRITING_SKILL_USED`.

ReAct additionally proves skill-file reading through the custom `ReadFileTool.read(...)` counter. DeepAgent uses OpenJiuwen's native `skill_tool` to read `skills/<skill-name>/SKILL.md`, so its custom `readFile` counter can remain `0` while skill use is still proven by the final skill markers and successful tool execution.

Successful output ends with:

```text
=== Proof Verification ===
readFile invocations: 2
queryOrder invocations: 1
calcDiscount invocations: 1
skill markers: ORDER_ANALYSIS_SKILL_USED, REPORT_WRITING_SKILL_USED
verification: PASS
```

For DeepAgent, successful output can show `readFile invocations: 0` because the native `skill_tool` path reads the skill files.

## Custom input

Both main classes accept optional arguments:

```text
<yaml-path> <user-input>
```

If no arguments are provided, the example uses the default YAML file and a prompt that asks the model to call both tools and use both skills.

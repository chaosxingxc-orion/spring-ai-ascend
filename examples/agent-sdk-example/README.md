# agent-sdk example

This directory is a standalone runnable Java example project for `agent-sdk`.
It demonstrates the current OpenJiuwen-only SDK path:

```text
YAML
  -> AgentHandlerFactory.toReactAgent(...) / toDeepAgent(...)
  -> AgentHandlerFactory.toHandler(...)
  -> AgentRuntimeHandler
  -> handler.execute(...)
```

## Contents

- `openjiuwen/agent.yaml`: OpenJiuwen ReAct YAML.
- `openjiuwen/deepagent.yaml`: OpenJiuwen DeepAgent YAML.
- `OpenJiuwenReactAgentSdkExample`: builds a `ReActAgent` from `openjiuwen/agent.yaml`, then converts it to an `AgentRuntimeHandler`.
- `OpenJiuwenDeepAgentSdkExample`: builds a `DeepAgent` from `openjiuwen/deepagent.yaml`, then converts it to an `AgentRuntimeHandler`.
- `OpenJiuwenExampleSupport`: shared runtime execution helper.
- `tools/`: Java classpath tools used by both YAML files.
- `skills/`: local OpenJiuwen skill directories used by both YAML files.

The public YAML tool reference does not use a source `path`. Java tools are referenced by class and method:

```yaml
ref:
  type: file
  class: QueryOrderTool
  method: query
```

## Install the SDK

Install the SDK and its runtime dependencies into the local Maven repository first:

```bash
mvn -pl agent-sdk -am -DskipTests install
```

## Run ReAct

```bash
mvn -f examples/agent-sdk-example/pom.xml compile exec:java "-Dexample.mainClass=com.huawei.ascend.agentsdk.example.OpenJiuwenReactAgentSdkExample"
```

## Run DeepAgent

```bash
mvn -f examples/agent-sdk-example/pom.xml compile exec:java "-Dexample.mainClass=com.huawei.ascend.agentsdk.example.OpenJiuwenDeepAgentSdkExample"
```

## Real calls and proof mode

The YAML files contain an explicit `apiKey` field and default to real OpenJiuwen model calls:

```yaml
framework:
  options:
    executeMode: openjiuwen
```

To avoid remote model calls and only prove the SDK wiring, run either example with:

```bash
mvn -f examples/agent-sdk-example/pom.xml compile exec:java "-Dexample.mainClass=com.huawei.ascend.agentsdk.example.OpenJiuwenReactAgentSdkExample" "-Dopenjiuwen.example.proof=true"
```

Proof mode rewrites the YAML at runtime to `executeMode: sdk-proof`, then verifies that the handler, tools, and skills are wired without calling the remote model.

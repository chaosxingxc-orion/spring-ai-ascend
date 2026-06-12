# agent-runtime Developer Guides

Per-feature reference documentation for developers integrating with agent-runtime.
Each guide is self-contained — read the ones relevant to your task.

## How to use

**For AI agents (Claude, Copilot, etc.):** Read the guide relevant to the task
at hand before writing code. Each guide defines the contracts, patterns, and
configuration surfaces you need.

**For human developers:** Start with the quick-start example
(`examples/agent-runtime-openjiuwen-simple/`), then consult these guides when
you need details on a specific feature.

## Guides

| Guide | When to read |
|---|---|
| [handler-spi.md](handler-spi.md) | Implementing a custom AgentRuntimeHandler |
| [openjiuwen-adapter.md](openjiuwen-adapter.md) | Hosting an openJiuwen ReActAgent or DeepAgent |
| [agent-card-configuration.md](agent-card-configuration.md) | Configuring the A2A agent discovery card |
| [a2a-endpoints.md](a2a-endpoints.md) | Understanding the A2A JSON-RPC surface |
| [configuration-properties.md](configuration-properties.md) | Reference for all application.yaml settings |

## Quick navigation by task

| I want to... | Read |
|---|---|
| Create a new agent adapter | [handler-spi.md](handler-spi.md) → [openjiuwen-adapter.md](openjiuwen-adapter.md) |
| Change the agent card name/description | [agent-card-configuration.md](agent-card-configuration.md) |
| Call my agent via curl/HTTP | [a2a-endpoints.md](a2a-endpoints.md) |
| Find a configuration property | [configuration-properties.md](configuration-properties.md) |
| Set up memory for my agent | [openjiuwen-adapter.md](openjiuwen-adapter.md#memory-integration) |
| Add custom tools to my agent | [openjiuwen-adapter.md](openjiuwen-adapter.md) |

## Related

- Quick-start example: `examples/agent-runtime-openjiuwen-simple/`
- Module README: `agent-runtime/README.md`
- Architecture: `architecture/docs/L0/ARCHITECTURE.md`

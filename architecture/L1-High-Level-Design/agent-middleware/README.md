---
level: L1-HLD
TAG:
  - entry
  - governance
  - reading-path
  - memory-service
status: draft
dependency:
  - overview.md
  - scenarios.md
  - logical.md
  - development.md
  - process.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
  - glossary.md
---

# agent-middleware memory service L1 架构高阶设计

## 目的

本文档集是 `agent-middleware` 模块中 **memory service** 能力的 L1 高阶设计入口。它基于 `outputs/doushuaigong-main.zip` 中 MemOpt / Helix 记忆系统代码包的架构分析，抽取适合 `spring-ai-ascend` 的智能体记忆服务设计。

本文档集只覆盖 memory service，不覆盖 `agent-middleware` 的模型网关、prompt、retrieval、skill、advisor 等其他中间件能力；也不以当前仓库 `agent-middleware/src/main/java` 下已有代码作为设计依据。

## 文档地图

| 文件 | 作用 |
|---|---|
| `README.md` | 入口、目的范围、文档地图和阅读路径。 |
| `overview.md` | 架构概览：memory service 目标、受众、问题领域、边界形态和外部参考来源。 |
| `scenarios.md` | 场景视图：召回、写入沉淀、作用域合成、经验强化、降级和治理场景。 |
| `logical.md` | 逻辑视图：领域对象、分层、状态归属、检索融合、写入路由、作用域和进化资产模型。 |
| `development.md` | 开发视图：建议包结构、构建边界、SPI、适配器、可选依赖和架构约束。 |
| `process.md` | 进程视图：search/save/feedback/learn 主流程、异步边界、缓存、并发和错误处理。 |
| `physical.md` | 物理视图：部署拓扑、存储后端、索引、缓存、资源模型和多租户隔离。 |
| `api-appendix.md` | API 附录：memory service northbound API、错误语义、健康和指标面。 |
| `spi-appendix.md` | SPI 附录：MemoryProvider、MemoryStore、ScopeResolver、EvolutionStore 等扩展面。 |
| `glossary.md` | 术语表：memory service 内部容易混淆的概念和边界。 |

## 阅读路径

1. 阅读 `overview.md`，先建立 memory service 的模块定位、目标和非目标。
2. 阅读 4+1 视图：`scenarios.md` -> `logical.md` -> `development.md` -> `process.md` -> `physical.md`。
3. 阅读 `api-appendix.md`，确认对外 HTTP/API 面、健康面、指标面和错误语义。
4. 阅读 `spi-appendix.md`，确认 Java 侧应暴露的稳定 SPI 和可替换实现边界。
5. 阅读 `glossary.md`，统一 memory、experience、asset、scope、ownership、phase、thermodynamics 等术语。

## 范围声明

本 L1 设计将 doushuaigong 代码包中的以下能力抽象为 `agent-middleware` 的 memory service：

- `core/loops/integration/http_facade.py` 中的 framework-neutral `search` / `save` / `feedback` / `learn` 门面。
- `core/loops/middle` 中的意图嗅探、检索、滑窗学习和事件驱动写入流程。
- `core/helix` 中的向量、标量/FTS、VFS、图谱和时间线存储抽象。
- `agents/retriever` 中的混合召回、重排、压缩为 `<memory_whisper>` 的上下文药丸模式。
- `core/loops/outer/evolution*` 中的经验资产、eligibility trace、热力学强化和异步蒸馏模式。
- `docs/MEMORY_HTTP_CONTRACT.md` 和 `docs/ORG_MEMORY_DESIGN.md` 中的外部契约、作用域、ownership、phase 和组织级治理思路。

本 L1 设计不把 doushuaigong 的 Python Agent、NATS、OpenClaw 插件、具体 prompt、具体模型网关、`mempalace` 命名、三循环叙事或实现缺陷直接搬入 `spring-ai-ascend`。这些只作为设计事实来源，用于提炼 memory service 的稳定职责和可替换机制。

## L1 / L2 边界

L1 保留模块级事实：职责、边界、4+1 视图、稳定 API/SPI、状态归属、作用域规则、物理拓扑、依赖方向和关键流程。

L2 应展开特性级事实：数据库 schema、向量库参数、索引建表、Java 类协作、HTTP DTO 完整字段、异常矩阵、缓存配置、租户 RLS 规则、压测模型、迁移脚本、端到端测试脚本和具体适配器实现。

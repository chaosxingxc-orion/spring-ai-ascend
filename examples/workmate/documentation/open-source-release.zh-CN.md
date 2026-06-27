# 开源发布说明

本文说明 **公开仓库包含什么**、**什么应保留在本地**，以及如何将 WorkMate 作为 **localhost
示例** 安全运行（而非直接暴露到公网）。

日常环境变量见 [configuration.md](./configuration.md)（英文）；架构见 [architecture.md](./architecture.md)。

---

## 定位

WorkMate Workbench 是基于 `spring-ai-ascend` 的 **示例应用**，展示多 Agent 会话、专家团、
Developer Studio、MCP 与审计投影。

适用场景：

- 本地开发与学习（`make dev`）
- `scripts/dogfood/` 下的 smoke 与离线校验
-  fork 后改造控制面模式

**不适用**：在未增加鉴权与网络边界的情况下，对不可信用户开放公网访问。

---

## Git 中会发布的内容

根目录 `.gitignore` 做了白名单裁剪。首次提交大约 **1300+ 个文件**（源码、文档、精选 `office/`
示例），不包含本地缓存与依赖。

| 区域 | 会发布 | 不发布（本地 / gitignore） |
|------|--------|---------------------------|
| **密钥** | `.env.local.example`、`application.yaml` 占位默认值 | `**/.env.local`、真实 API Key |
| **运行时数据** | — | `workmate-api/data/`、`workspaces/`、`logs/`、`target/` |
| **依赖与构建产物** | — | `node_modules/`、`dist/`、`coverage/` |
| **市场拉取缓存** | — | `office/_sources/`、`office/market/`、`office/skills-market/` |
| **专家市场** | `office/experts-market/` 下 12 个精选示例 | 磁盘上其余拉取包 |
| **连接器市场** | 仅 `github`、`notion` | 本地可能存在的其余 ~37 个连接器目录 |
| **内部设计文档** | `documentation/` 对外文档 | `docs/`、`ASCEND-DEPENDENCIES.md` |
| **Dogfood 脚本** | 精简 smoke 子集 + 离线校验 + `mock-oa-mcp/` | `.gitignore` 列出的 24 个内部里程碑脚本 |

`office/experts/` 下手写专家（如 `fund-analyst`）完整发布。

---

## 可选模块

以下目录在示例树中，但 **不是** `make dev` 默认路径所必需：

| 模块 | 说明 |
|------|------|
| `workmate-desktop/` | 可选 Electron 壳，单独 `npm run dev` |
| `member-runtimes/workmate-member-a2a/` | 可选 A2A 成员运行时，Compose `--profile members` |

核心交付物：**`workmate-api` + `workmate-ui` + `office/`**。

---

## 安全模型（摘要）

### 无 API 鉴权

没有全局 Spring Security / Bearer Token。能访问 API 端口的客户端即可调用 `/api/v1/**`。
非本地部署请绑定 localhost、加反向代理鉴权，或自行 fork 实现鉴权。

### Production Profile

本地默认可开 Studio、Cloud stub、OAuth mock。共享/非本地环境：

```bash
SPRING_PROFILES_ACTIVE=production
```

会关闭 cloud、oauth mock、studio（可用 `WORKMATE_*` 单独覆盖）。

### Shell 非沙箱

Bash 工具以 **API 进程 OS 用户** 执行命令；靠 HITL、风险策略与进程超时/输出上限，**不是**
内核级隔离。

### 连接器凭据

保存在 `data/connector-credentials.json`（明文 JSON），请当作本地敏感数据目录。

### Studio 写入

路径穿越有多层校验与集成测试，详见英文 [open-source-release.md](./open-source-release.md#authoring-path-safety)。

---

## qieman MCP 示例

`fund-analyst` 与默认 MCP 目录中的 **qieman** 条目用于演示远程 MCP 接入，仓库内 **不含**
真实密钥。本地使用需在 `.env.local` 配置 `WORKMATE_MCP_QIEMAN_API_KEY` 等变量。

---

## Prompt 与 Dogfood

- **对外 smoke**：`office/prompts/prd-draft.md`、`hitl-probe.txt`（见 [office/prompts/README.md](../office/prompts/README.md)）
- **内部 QA**：`office/prompts/*-dogfood.md` 为里程碑团队拓扑验收用，**不属于** 开源 smoke 集；已通过 `.gitignore` 排除，不会进入公开仓库

---

## 维护者提交前核对

1. 确认 `.env.local` 被 ignore，切勿提交真实密钥  
2. `git ls-files --others --exclude-standard examples/workmate` 检查无 `node_modules`、`target/`、`.env`  
3. 确认 `connectors-market/`、`experts-market/` 仅白名单路径  
4. `make test` 通过  
5. 许可证见仓库根目录  

---

## 相关文档

- [open-source-release.md](./open-source-release.md) — 英文完整版（含 Webhook、CORS、维护命令）  
- [getting-started.md](./getting-started.md)  
- [configuration.md](./configuration.md)  
- [testing.md](./testing.md)  
- [office/README.md](../office/README.md)  
- [release-notes.md](./release-notes.md)  

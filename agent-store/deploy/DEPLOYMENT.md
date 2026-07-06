# agent-store Linux 部署指南

本文档描述在 **Linux 服务器** 上从源码到运行的完整流程：Maven 打包 → 构建两个 Docker 镜像 → 配置外部 Versatile Mock 地址 → Compose 启动 **adapter + edp-agent**。

> **Versatile Mock 不由本仓库 Compose 管理**，默认由同事独立部署；你只需拿到其可达地址并写入 `deploy/.env`。

---

## 配置架构（2026 最新）

远端代码已将配置拆为三层：

| 层级 | 位置 | 内容 |
|------|------|------|
| **运行时连接** | JAR 内 `application.yml` → `edpa.agent.*` | model、versatile（URL / adapter A2A） |
| **框架治理** | `engine/src/main/resources/governance/` | planrule、actrule、scriptconfig |
| **业务场景** | `scenarios/wealth-demo/` | scenario-config、skills |

已删除/废弃：`edp-agent.yaml`（见 `.bak`）、独立 `edp-config.yaml`、`SysScriptsConfig.yaml`（话术合并进 `governance/scriptconfig.yaml`）。

Docker 部署时：

- **governance/** 由 Dockerfile COPY 到 `/app/config/governance/`
- **model / versatile** 通过 Compose 环境变量覆盖（无需单独 deploy YAML）
- **`deploy/config/edp-config.yaml`** 仅为路径锚点，供解析 governance 目录

---

## 目录结构（部署相关）

```
agent-store/
├── deploy/
│   ├── build.sh                         ← 一键 Maven + docker build
│   ├── docker-compose.yml
│   ├── .env.example
│   └── DEPLOYMENT.md
├── adapter-versatile-agent-java/deploy/Dockerfile
└── edp-agent-java/
    ├── engine/src/main/resources/
    │   ├── application.yml              ← edpa.agent.model / versatile
    │   └── governance/                  ← planrule / actrule / scriptconfig
    ├── scenarios/wealth-demo/
    └── deploy/
        ├── Dockerfile
        ├── requirements-mcp.txt
        └── config/
            ├── README.md
            └── edp-config.yaml          ← 路径锚点（非业务配置）
```

---

## 调用链路

```
客户端
  → edp-agent (:8190)
      → adapter-versatile (:8191)     [EDP_AGENT_VERSATILE_A2A_URL]
          → 外部 Versatile Mock       [deploy/.env → VERSATILE_URL]
```

---

## 一、向同事索取的信息（外部 Versatile Mock）

### 必填

| 信息 | 写入位置 |
|------|----------|
| Versatile REST URL 模板（含 `{workflow_id}`、`{conversation_id}`） | `deploy/.env` → `VERSATILE_URL` |
| workflow_id | `VERSATILE_WORKFLOW_ID` |
| workspace_id | `VERSATILE_WORKSPACE_ID` |
| adapter 容器内可达的 host:port | `VERSATILE_URL` 的主机部分 |

### 常见 `VERSATILE_URL` 写法

| 场景 | 示例 |
|------|------|
| Mock 映射到宿主机 30001（同机） | `http://host.docker.internal:30001/v1/0/agent-manager/workflows/{workflow_id}/conversations/{conversation_id}` |
| Mock 在 10.1.2.3:30001 | `http://10.1.2.3:30001/v1/0/...` |
| 共享 Docker 网络，服务名 `versatile-mock` | `http://versatile-mock:30001/v1/0/...` |

Compose 已配置 `extra_hosts: host.docker.internal:host-gateway`。

---

## 二、前置条件

```bash
git --version
java -version          # 21
docker --version       # 20.10+
docker compose version # v2+
```

```bash
git clone <仓库地址> spring-ai-ascend
cd spring-ai-ascend
```

---

## 三、Maven 打包 + 构建镜像

### 方式 A：一键脚本（推荐）

```bash
bash agent-store/deploy/build.sh
```

### 方式 B：分步执行

在**仓库根目录**：

```bash
./mvnw clean install -DskipTests
./mvnw -pl agent-store/edp-agent-java -am package -DskipTests
./mvnw -f agent-store/adapter-versatile-agent-java/pom.xml package -DskipTests

docker build -t adapter-versatile-agent-java:latest \
  -f agent-store/adapter-versatile-agent-java/deploy/Dockerfile \
  agent-store/adapter-versatile-agent-java

docker build -t edp-agent-java:latest \
  -f agent-store/edp-agent-java/deploy/Dockerfile \
  agent-store/edp-agent-java
```

确认产物：

```bash
ls agent-store/edp-agent-java/engine/target/edp-agent-engine-*.jar
ls agent-store/adapter-versatile-agent-java/target/adapter-versatile-agent-java-*.jar
docker images | grep -E 'adapter-versatile-agent-java|edp-agent-java'
```

---

## 四、配置环境变量

```bash
cd agent-store/deploy
cp .env.example .env
vi .env
```

### 必须修改

```env
EDP_AGENT_MODEL_API_KEY=你的真实密钥
VERSATILE_URL=http://host.docker.internal:30001/v1/0/agent-manager/workflows/{workflow_id}/conversations/{conversation_id}
EDP_AGENT_VERSATILE_A2A_URL=http://adapter-versatile:8191/a2a
```

`EDP_AGENT_VERSATILE_A2A_URL` 在 `docker-compose.yml` 中有默认值，但建议在 `.env` 中显式写出，便于排查。

### 完整 `.env` 示例

```env
EDP_AGENT_PORT=8190
ADAPTER_PORT=8191

EDP_AGENT_MODEL_API_KEY=你的真实密钥
EDP_AGENT_MODEL_PROVIDER=OpenAI
EDP_AGENT_MODEL_NAME=deepseek-chat
EDP_AGENT_MODEL_BASE_URL=https://api.deepseek.com/v1

VERSATILE_URL=http://host.docker.internal:30001/v1/0/agent-manager/workflows/{workflow_id}/conversations/{conversation_id}
VERSATILE_WORKFLOW_ID=mock_workflow
VERSATILE_WORKSPACE_ID=10
VERSATILE_RESULT_NODE_TYPE=QA
VERSATILE_RESULT_NODE_NAME=GXZQAResponseNode

EDP_AGENT_VERSATILE_A2A_URL=http://adapter-versatile:8191/a2a
EDP_AGENT_VERSATILE_URL=

MCP_MASTER_URL=http://your-mcp-host:8000/sse
MCP_STANDBY_URL=http://your-mcp-host:8000/sse
MCP_ACCESS_TOKEN=your-token
MCP_APP_NAME=your-app
```

> **MCP 说明**：`MCP_*` 由 edp-agent 容器内 Python skill 脚本读取；不需要 MCP 时可留空。值两侧不要加空格或引号（`KEY=value`，不是 `KEY = "value"`）。

### 环境变量对照表

| 变量 | 消费者 | 作用 |
|------|--------|------|
| `VERSATILE_URL` | adapter | adapter → 外部 Mock REST |
| `VERSATILE_WORKFLOW_ID` | adapter | URL 中 workflow 占位 |
| `VERSATILE_WORKSPACE_ID` | adapter | REST 查询参数 |
| `VERSATILE_RESULT_NODE_*` | adapter | 流式结果节点识别 |
| `EDP_AGENT_MODEL_API_KEY` | edp-agent | LLM 密钥（**必填**） |
| `EDP_AGENT_MODEL_NAME` | edp-agent | 模型名，默认 `deepseek-v4-pro` |
| `EDP_AGENT_VERSATILE_A2A_URL` | edp-agent | → adapter A2A，默认 `http://adapter-versatile:8191/a2a` |
| `EDP_AGENT_VERSATILE_URL` | edp-agent（可选） | 直连 Mock REST |
| `MCP_*` | edp-agent（可选） | MCP 产品列表 |

> **注意**：`EDP_AGENT_VERSATILE_A2A_URL` 是新版环境变量名（对应 `application.yml` 的 `edpa.agent.versatile.adapter-a2a-url`）。

---

## 五、启动服务

```bash
cd agent-store/deploy
docker compose up -d
```

| 容器 | 端口 |
|------|------|
| `adapter-versatile` | 8191 |
| `edp-agent` | 8190 |

```bash
docker compose ps
docker compose logs -f
docker compose down    # 停止
```

代码更新后：`bash ../deploy/build.sh` 或 `docker compose up -d --build`。

---

## 六、部署后验证

```bash
# 外部 Mock（adapter 容器内）
docker exec adapter-versatile curl -sf http://host.docker.internal:30001/health

# 本栈健康
curl -s http://localhost:8191/.well-known/agent-card.json
curl -s http://localhost:8190/.well-known/agent-card.json

# edp-agent → adapter
docker exec edp-agent curl -s http://adapter-versatile:8191/.well-known/agent-card.json

# adapter 上游日志
docker compose logs adapter-versatile | grep "versatile request url"
```

端到端 A2A：

```bash
curl -N -X POST http://localhost:8190/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendStreamingMessage",
    "id": "test-1",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-1",
        "contextId": "conv-linux-test-001",
        "parts": [{"text": "推荐理财产品"}]
      },
      "metadata": {"userId": "test", "agentId": "edp-agent"}
    }
  }'
```

---

## 七、离线部署

```bash
docker save adapter-versatile-agent-java:latest -o adapter-versatile.tar
docker save edp-agent-java:latest -o edp-agent.tar
# 目标机：docker load + cp .env.example .env + docker compose up -d
```

---

## 八、故障排查

| 现象 | 处理 |
|------|------|
| `VERSATILE_URL is required` | 创建并填写 `deploy/.env` |
| adapter 连 Mock 超时 | `VERSATILE_URL` 勿用 `localhost`，改用 `host.docker.internal` 或 IP |
| edp-agent 连不上 adapter | 未设置 `EDP_AGENT_VERSATILE_A2A_URL` | 在 `deploy/.env` 设为 `http://adapter-versatile:8191/a2a` |
| MCP 调用失败 | `MCP_*` 格式错误或容器内不可达 | 检查 `.env` 无空格/引号；`docker compose logs edp-agent` |
| 用了旧的 `agent-store/.env` | compose 在 `deploy/` 目录，不读上级 `.env` | 只维护 `agent-store/deploy/.env` |
| edp-agent 启动报 apiKey | 设置 `EDP_AGENT_MODEL_API_KEY` |
| governance 未加载 | 确认镜像含 `/app/config/governance/`（重建 edp-agent 镜像） |
| `docker build` 找不到 JAR | 先执行 Maven 打包 |

---

## 九、完整流程

```
git clone
    ↓
向同事索取 Mock 地址 + workflow_id + workspace_id
    ↓
bash agent-store/deploy/build.sh
    ↓
cd agent-store/deploy && cp .env.example .env && 填写密钥与 VERSATILE_URL
    ↓
docker compose up -d
    ↓
curl 健康检查 + A2A 流式验证
```

---

## 附录 A：Docker vs 本地开发

| 配置项 | Docker Compose | 本地开发 |
|--------|----------------|----------|
| 环境变量文件 | **`agent-store/deploy/.env`** | **`edp-agent-java/.env`** |
| Compose 是否读取 | ✅ 仅此文件 | ❌ |
| adapter → Mock | `VERSATILE_URL` | 本地 mock 用 `localhost:30001`（adapter 的 `VERSATILE_URL`） |
| edp-agent → adapter | `EDP_AGENT_VERSATILE_A2A_URL` | `http://localhost:8191/a2a` |
| edp-agent 直连 Mock | `EDP_AGENT_VERSATILE_URL`（可选） | `http://localhost:30001/...` |
| 治理配置 | 镜像内 `/app/config/governance/` | `resources/governance/` |
| MCP | `deploy/.env` 中 `MCP_*` | `edp-agent-java/.env` 中 `MCP_*` |
| Mock 部署 | 同事负责 | 本地 `mock/` 自行启动 |

---

## 附录 B：从旧 `agent-store/.env` 迁移

若你此前在 **`agent-store/.env`**（仓库根下、三容器 mock 时代）配置过环境变量，请按下列规则迁到 **`agent-store/deploy/.env`**：

| 旧变量 | 处理 |
|--------|------|
| `VERSATILE_MOCK_PORT` | 🗑️ 删除（不再部署 mock 容器） |
| `VERSATILE_URL=...versatile-mock...` | ⚠️ 改为同事 Mock 的可达地址，同机常用 `host.docker.internal:30001` |
| `EDP_AGENT_MODEL_*` | 📋 原样复制到 `deploy/.env` |
| `MCP_*` | 📋 迁入 `deploy/.env`，去掉 `=` 两侧空格和引号 |
| （缺失）`EDP_AGENT_VERSATILE_A2A_URL` | ➕ 新增 `http://adapter-versatile:8191/a2a` |
| （缺失）`VERSATILE_RESULT_NODE_*` | ➕ 补全，默认 `QA` / `GXZQAResponseNode` |

迁移完成后：

1. 确认 **`agent-store/.env` 不再用于 compose**（该文件已改为废弃说明）
2. 在 `agent-store/deploy` 目录执行 `docker compose down && docker compose up -d`
3. 验证：`docker exec edp-agent curl -s http://adapter-versatile:8191/.well-known/agent-card.json`

### 变量该写哪个文件

| 变量 | `deploy/.env` | `edp-agent-java/.env` |
|------|:-------------:|:---------------------:|
| `VERSATILE_URL` | ✅ | ❌ |
| `EDP_AGENT_VERSATILE_A2A_URL` | ✅ `adapter-versatile:8191` | ✅ `localhost:8191` |
| `EDP_AGENT_VERSATILE_URL` | 可选 | 可选 |
| `EDP_AGENT_MODEL_API_KEY` | ✅ | ✅ |
| `MCP_*` | 可选 | 可选 |
| `EDP_AGENT_SCENARIO_HOME` | Compose 镜像内已固定 | ✅ 本地需要 |

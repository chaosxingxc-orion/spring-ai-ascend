# FEAT-013 真实环境 E2E 验证指导书

> 基于最新代码状态(2026-07-16):含 `AgentBusInfrastructureConfiguration` 共享 infra 抽取 + `RocketMqBrokerClientConfiguration` 去 gateway 化 + `BrokerControlDescriptor` SPI 提升后的代码状态。
>
> 目标:在本地真实环境(RocketMQ + Postgres + 多进程)端到端验证 FEAT-013 client-invocation event forwarding 的 §6.2 全部 UseCase 场景。

---

## 目录

- [第一部分:环境搭建](#第一部分环境搭建)
  - [1.1 在 WSL Ubuntu 中搭建 Postgres](#11-在-wsl-ubuntu-中搭建-postgres)
  - [1.2 启动 RocketMQ + Postgres 一键编排](#12-启动-rocketmq--postgres-一键编排)
  - [1.3 手动启动 RocketMQ(不依赖 docker-compose)](#13-手动启动-rocketmq不依赖-docker-compose)
  - [1.4 数据库表初始化(Flyway 迁移 / 手动建表)](#14-数据库表初始化flyway-迁移--手动建表)
- [第二部分:构建 + 启动进程](#第二部分构建--启动进程)
  - [2.1 进程拓扑](#21-进程拓扑)
  - [2.2 构建 agent-bus fat-jar](#22-构建-agent-bus-fat-jar)
  - [2.3 准备测试类 classpath](#23-准备测试类-classpath)
  - [2.4 启动 4 个进程](#24-启动-4-个进程)
- [第三部分:FEAT-013 黑盒测试用例](#第三部分feat-013-黑盒测试用例)
- [第四部分:数据库验证查询](#第四部分数据库验证查询)
- [第五部分:进程退出 / 重置](#第五部分进程退出--重置)
- [第六部分:完成确认清单](#第六部分完成确认清单)
- [附录:常见问题](#附录常见问题)

---

# 第一部分:环境搭建

## 1.1 在 WSL Ubuntu 中搭建 Postgres

### 环境现状

- WSL Ubuntu 2(运行中)
- Docker 已安装在 WSL 内,但默认用户无 docker socket 权限(需 sudo)
- 端口 5432 未占用

### 步骤 1:在 WSL 中启动 Docker daemon

打开一个 WSL 终端(在 Windows Terminal 中输入 `wsl`,或在 PowerShell 中输入 `wsl`),执行:

```bash
# 启动 docker daemon(二选一)
sudo service docker start
# 或(若用 systemd)
sudo systemctl start docker

# 验证 daemon 已启动
sudo docker ps
# 应看到空列表(只有表头 CONTAINER ID ...)

# (可选)将当前用户加入 docker 组,免 sudo
sudo usermod -aG docker $USER
# 需要重新打开 WSL 会话或执行 newgrp docker 才生效
```

> **关于免 sudo**:加入 docker 组后,**关闭并重新打开 WSL 终端**,后续 `docker` 命令即可免 sudo。本指导后续命令以 `sudo docker` 形式给出,你可根据是否已加组自行调整。

### 步骤 2:启动 Postgres 容器(独立方式)

参数与项目 `docker-compose.yml` 中的 `postgres` 服务完全一致(db=agentbus / user=agentbus / password=agentbus / 端口 5432 / 镜像 postgres:16.2)。

```bash
# 拉取镜像(首次需要)
sudo docker pull postgres:16.2

# 启动 Postgres 容器
sudo docker run -d \
  --name ascend-postgres \
  --restart unless-stopped \
  -e POSTGRES_DB=agentbus \
  -e POSTGRES_USER=agentbus \
  -e POSTGRES_PASSWORD=agentbus \
  -p 5432:5432 \
  postgres:16.2
```

**关键点**:
- `-p 5432:5432` 将容器端口映射到 WSL 的 5432。由于 WSL2 的端口会自动转发到 Windows 主机,Windows 侧的 `localhost:5432` 也能访问。
- 容器名固定为 `ascend-postgres`(与 docker-compose.yml 一致,便于后续若切到 docker-compose 也不冲突)。

### 步骤 3:验证 Postgres 可用

```bash
# 1. 查看容器状态(应 Up)
sudo docker ps --filter name=ascend-postgres

# 2. 执行健康检查
sudo docker exec ascend-postgres pg_isready -U agentbus -d agentbus
# 应输出: /var/run/postgresql:5432 - accepting connections

# 3. 进入 psql 验证连接
sudo docker exec -it ascend-postgres psql -U agentbus -d agentbus -c "\conninfo"
# 应显示: You are connected to database "agentbus" as user "agentbus" ...

# 4. 退出 psql
# (上一条 -c 已自动退出)
```

### 步骤 4:验证 Windows 侧可访问

回到 Windows PowerShell(或继续在 WSL,因为 WSL2 端口自动转发):

```powershell
# 在 Windows PowerShell 中执行(需先安装 psql 客户端,或用 telnet 测端口)
Test-NetConnection -ComputerName localhost -Port 5432
# 应看到: TcpTestSucceeded : True
```

若 Windows 侧没有 `psql` 客户端,不影响后续验证 —— agent-bus 进程会用 JDBC 连接 `jdbc:postgresql://localhost:5432/agentbus`。

---

## 1.2 启动 RocketMQ + Postgres 一键编排

**推荐**:直接用项目根目录的 `docker-compose.yml` 一键启动 RocketMQ + Postgres,无需单独按 1.1 步骤手动启动 Postgres。

```bash
# 1. 启动 docker daemon(每次重启 WSL 后需要)
sudo service docker start

# 2. 进入项目根目录(WSL 视角)
cd /mnt/d/code/spring-ai-ascend-d00650599-2/spring-ai-ascend

# 3. 一键启动 RocketMQ + Postgres(docker-compose.yml 在仓内)
sudo docker compose up -d postgres rocketmq-nameserver rocketmq-broker rocketmq-init

# 4. 查看初始化是否成功(rocketmq-init 会创建 8 个 topic)
sudo docker logs ascend-rocketmq-init | tail -20
# 应看到 "Done. Topic list:" + 8 个 ascend_bus_* topic

# 5. 验证所有容器健康
sudo docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
# 应看到 4 个容器:ascend-postgres / ascend-rocketmq-nameserver / ascend-rocketmq-broker / ascend-rocketmq-init(已 Exited 0)
```

**关键点**:
- `broker.conf` 已设置 `enablePropertyFilter=true`,SQL92 过滤生效(否则 `DeliveryFilter` 不起作用)
- 8 个 topic 通过 `rocketmq-init` 自动创建,无需手动 `mqadmin`
- Postgres 默认 db/user/pass = `agentbus/agentbus/agentbus`,端口 5432

> **方式选择**:
> - 1.1 + 1.3 = 手动方式分别启动 Postgres + RocketMQ(分步排查问题时用)
> - 1.2 = docker-compose 一键启动 RocketMQ + Postgres(推荐,日常 E2E 用)

---

## 1.3 手动启动 RocketMQ(不依赖 docker-compose)

若不想用 `docker compose`,可手动 `docker run` 启动 RocketMQ 三件套(nameserver + broker + init)。所有参数与 `docker-compose.yml` 一致,仅以 `docker run` 形式表达。

### 步骤 1:创建 Docker 网络(让三个容器互通)

```bash
# 创建一个桥接网络(后续三个容器都加入此网络,可用容器名互访)
sudo docker network create ascend-net 2>/dev/null || echo "network already exists"
```

> **关键点**:`rocketmq-broker` 需要通过容器名 `rocketmq-nameserver` 访问 nameserver,`rocketmq-init` 也需要通过容器名访问两者。Docker 自定义桥接网络支持容器名 DNS 解析,默认 bridge 网络不支持。

### 步骤 2:启动 RocketMQ NameServer

```bash
sudo docker run -d \
  --name ascend-rocketmq-nameserver \
  --network ascend-net \
  --network-alias rocketmq-nameserver \
  --restart unless-stopped \
  -p 9876:9876 \
  -e JAVA_OPT_EXT="-server -Xms512m -Xmx512m -Xmn128m" \
  apache/rocketmq:5.3.1 \
  sh mqnamesrv
```

**预期**:
- 容器启动后监听 9876
- 健康检查:`bash -c '</dev/tcp/127.0.0.1/9876'` 应成功

验证:
```bash
# 查看日志确认 nameserver 就绪
sudo docker logs ascend-rocketmq-nameserver 2>&1 | tail -10
# 应看到: ... The Name Server boot success. serializeType=JSON ...
```

### 步骤 3:启动 RocketMQ Broker

**必须在项目根目录下执行**(因为要挂载 `broker.conf`):

```bash
# 进入项目根目录(WSL 视角)—— broker.conf 在仓内
cd /mnt/d/code/spring-ai-ascend-d00650599-2/spring-ai-ascend

sudo docker run -d \
  --name ascend-rocketmq-broker \
  --network ascend-net \
  --network-alias rocketmq-broker \
  --restart unless-stopped \
  -p 10909:10909 \
  -p 10911:10911 \
  -e NAMESRV_ADDR=rocketmq-nameserver:9876 \
  -e JAVA_OPT_EXT="-server -Xms512m -Xmx512m -Xmn128m" \
  -v "$(pwd)/broker.conf:/home/rocketmq/rocketmq-5.3.1/conf/broker.conf:ro" \
  apache/rocketmq:5.3.1 \
  sh mqbroker -c /home/rocketmq/rocketmq-5.3.1/conf/broker.conf
```

**关键点**:
- `NAMESRV_ADDR=rocketmq-nameserver:9876` 用容器名访问 nameserver(依赖步骤 1 的自定义网络)
- `-v "$(pwd)/broker.conf:..."` 挂载仓内 `broker.conf`,**必须用 `-c` 加载它**,否则 `enablePropertyFilter=true` 不生效(SQL92 过滤会被 broker 忽略,`DeliveryFilter` 失效)
- 镜像 `apache/rocketmq:5.3.1` 与 `docker-compose.yml` 一致

验证:
```bash
# 查看日志确认 broker 已向 nameserver 注册
sudo docker logs ascend-rocketmq-broker 2>&1 | tail -20
# 应看到: ... boot success. serializeType=JSON and name server is rocketmq-nameserver:9876 ...

# 用 mqadmin 确认 broker 已注册到 DefaultCluster
sudo docker run --rm --network ascend-net \
  -e NAMESRV_ADDR=rocketmq-nameserver:9876 \
  -e PATH=/home/rocketmq/rocketmq-5.3.1/bin:/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
  apache/rocketmq:5.3.1 \
  sh mqadmin clusterList -n rocketmq-nameserver:9876
# 应看到表格,含 DefaultCluster / broker-a / 0 / ... 一行
```

> **若 clusterList 看不到 broker**:broker 启动后注册 nameserver 需要几秒。等待 10-30s 后重试。若仍未注册,检查 broker 日志中的 `connect to namesrv failed` 报错(通常是 NAMESRV_ADDR 拼错或网络不通)。

### 步骤 4:创建 8 个 Topic(一次性)

FEAT-013 §5.2 要求 4 个 topic(`ascend_bus_invocation_*`),FEAT-014 §5.2 要求 4 个(`ascend_bus_a2a_*`),共 8 个。用 `mqadmin updatetopic` 逐个创建:

```bash
# 定义辅助函数(在 WSL bash 中)
create_topic() {
  local topic=$1
  echo "[init] creating topic: $topic"
  sudo docker run --rm --network ascend-net \
    -e NAMESRV_ADDR=rocketmq-nameserver:9876 \
    -e PATH=/home/rocketmq/rocketmq-5.3.1/bin:/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    apache/rocketmq:5.3.1 \
    sh mqadmin updatetopic -n rocketmq-nameserver:9876 -c DefaultCluster -t "$topic"
}

# FEAT-013 §5.2 — client invocation event forwarding (4 topics)
create_topic ascend_bus_invocation_req
create_topic ascend_bus_invocation_deliver
create_topic ascend_bus_invocation_resp_in
create_topic ascend_bus_invocation_resp_out

# FEAT-014 §5.2 — A2A call event forwarding (4 topics)
create_topic ascend_bus_a2a_req
create_topic ascend_bus_a2a_deliver
create_topic ascend_bus_a2a_resp_in
create_topic ascend_bus_a2a_resp_out
```

> **topic 命名**:必须用下划线 `_`,不能用点 `.`。RocketMQ topic 校验器 `^[%|a-zA-Z0-9_-]+$` 禁止点号(联调时 mqadmin 拒绝过点号名)。

验证 8 个 topic 全部创建成功:
```bash
sudo docker run --rm --network ascend-net \
  -e NAMESRV_ADDR=rocketmq-nameserver:9876 \
  -e PATH=/home/rocketmq/rocketmq-5.3.1/bin:/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
  apache/rocketmq:5.3.1 \
  sh mqadmin topicList -n rocketmq-nameserver:9876 | grep ascend_bus
# 应看到 8 行(4 个 invocation_* + 4 个 a2a_*)
```

### 步骤 5:验证端到端连通

在 WSL 中(或 Windows 侧,因 WSL2 端口自动转发):

```bash
# 1. 确认 3 个容器状态
sudo docker ps --filter name=ascend-rocketmq --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
# 应看到:ascend-rocketmq-nameserver (Up, 9876) + ascend-rocketmq-broker (Up, 10909/10911)

# 2. Windows 侧端口连通性(在 Windows PowerShell)
Test-NetConnection -ComputerName localhost -Port 9876
# TcpTestSucceeded : True
```

### 手动方式对应的 8 个 topic 列表

| Topic | 用途 | 所属 |
|------|------|------|
| `ascend_bus_invocation_req` | hop1:gateway → event-bus 转发请求 | FEAT-013 §5.2 |
| `ascend_bus_invocation_deliver` | hop2:event-bus → runtime 投递请求 | FEAT-013 §5.2 |
| `ascend_bus_invocation_resp_in` | runtime → event-bus 响应入口 | FEAT-013 §5.2 |
| `ascend_bus_invocation_resp_out` | event-bus → gateway 响应出口 | FEAT-013 §5.2 |
| `ascend_bus_a2a_req` | hop1:gateway → event-bus A2A 请求 | FEAT-014 §5.2 |
| `ascend_bus_a2a_deliver` | hop2:event-bus → runtime A2A 投递 | FEAT-014 §5.2 |
| `ascend_bus_a2a_resp_in` | runtime → event-bus A2A 响应入口 | FEAT-014 §5.2 |
| `ascend_bus_a2a_resp_out` | event-bus → gateway A2A 响应出口 | FEAT-014 §5.2 |

### 手动方式清理

```bash
# 停止并删除 3 个 RocketMQ 容器
sudo docker rm -f ascend-rocketmq-nameserver ascend-rocketmq-broker

# 删除网络(若不再需要)
sudo docker network rm ascend-net
```

> **注意**:`broker.conf` 中 `brokerClusterName=DefaultCluster` / `brokerName=broker-a` / `brokerId=0` 必须保持与 `docker-compose.yml` 一致,否则 `mqadmin updatetopic -c DefaultCluster` 会找不到 cluster。仓内的 `broker.conf` 已正确配置,无需改动。

---

## 1.4 数据库表初始化(Flyway 迁移 / 手动建表)

### 背景

agent-bus 的数据库 schema 由 3 个 Flyway 迁移脚本管理,位于 `agent-bus/src/main/resources/db/migration/`:

| 脚本 | 创建的表 / 列 | 用途 |
|------|------|------|
| `V1__create_agent_bus_forwarding_outbox_inbox.sql` | `agent_bus_forwarding_outbox` / `agent_bus_forwarding_inbox` + 索引 + RLS | FEAT-012 转发持久化:发送方 outbox + 接收方 inbox 去重 |
| `V2__create_agent_registry_mvp.sql` | `agent_registry_mvp` + GIN/BTREE 索引 + RLS | FEAT-014 注册中心:Agent 注册 + 发现 + 健康探测 |
| `V3__add_outbox_correlation_event_type.sql` | `agent_bus_forwarding_outbox` 加 `correlation_id` / `event_type` 列 | FEAT-013 扩展:转发 outbox 增加关联 ID + 事件类型 |

**正常路径(推荐)**:项目已包含 [application.yml](file:///d:/code/spring-ai-ascend-d00650599-2/spring-ai-ascend/agent-bus/src/main/resources/application.yml),显式配置了 `spring.datasource.*` + `spring.flyway.enabled=true` + `baseline-on-migrate=true`。启动 event-bus 进程时 Flyway 会自动执行 V1→V2→V3 建表,**无需手动操作**。

**Fallback 路径**:仅当 Flyway 自动运行失败(日志报 `relation "agent_registry_mvp" does not exist` 等)时,才需要按下方步骤手动建表。

### 步骤 1:检查 Flyway 是否已自动运行

在 WSL 中执行(假设 Postgres 容器名为 `ascend-postgres`):

```bash
# 检查 flyway_schema_history 表是否存在(Flyway 运行后会自动创建此表)
sudo docker exec ascend-postgres psql -U agentbus -d agentbus -c \
  "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"

# 如果返回 3 行(V1/V2/V3,success=t),说明 Flyway 已自动运行,跳到步骤 3 验证表结构
# 如果返回 "relation \"flyway_schema_history\" does not exist",说明 Flyway 未运行,继续步骤 2
```

### 步骤 2(Fallback):手动执行 3 个迁移脚本

> **仅当步骤 1 显示 Flyway 未运行时才执行此步骤。** 若 application.yml 已存在且 event-bus 启动正常,跳过此步。

在 WSL 中执行(按 V1 → V2 → V3 顺序,**顺序不能颠倒**,V3 依赖 V1 的表):

```bash
# 迁移脚本目录(WSL 视角)
MIGRATION_DIR=/mnt/d/code/spring-ai-ascend-d00650599-2/spring-ai-ascend/agent-bus/src/main/resources/db/migration

# V1:创建 outbox + inbox 表 + 索引 + RLS
sudo docker exec -i ascend-postgres psql -U agentbus -d agentbus < $MIGRATION_DIR/V1__create_agent_bus_forwarding_outbox_inbox.sql
# 应看到多个 CREATE TABLE / CREATE INDEX / CREATE POLICY 输出,最后无错误

# V2:创建 agent_registry_mvp 表 + GIN/BTREE 索引 + RLS
sudo docker exec -i ascend-postgres psql -U agentbus -d agentbus < $MIGRATION_DIR/V2__create_agent_registry_mvp.sql
# 应看到 CREATE TABLE / CREATE INDEX / ALTER TABLE / CREATE POLICY 输出

# V3:给 outbox 加 correlation_id + event_type 列
sudo docker exec -i ascend-postgres psql -U agentbus -d agentbus < $MIGRATION_DIR/V3__add_outbox_correlation_event_type.sql
# 应看到两条 ALTER TABLE 输出
```

> **关于 Flyway 与手动建表的兼容性**:手动建表后,后续 event-bus 启动时如果 Flyway autoconfig 触发,它会检测到 `flyway_schema_history` 表不存在,尝试重新执行 V1-V3。由于表已存在,`CREATE TABLE` 会报错。解决方案:
> - **方案 A(推荐)**:手动建表后,同时手动插入 `flyway_schema_history` 记录,让 Flyway 认为迁移已执行(见下方"标记 Flyway 已执行")
> - **方案 B**:保持现状,Flyway autoconfig 不触发时手动建表就足够,E2E 验证不依赖 Flyway
> - **方案 C**:删表重建,让 Flyway 自动执行(需先 DROP 所有表)

### 步骤 3:验证表结构

```bash
# 1. 查看所有表(应看到 4 张表:agent_bus_forwarding_outbox / agent_bus_forwarding_inbox / agent_registry_mvp + flyway_schema_history 可选)
sudo docker exec ascend-postgres psql -U agentbus -d agentbus -c "\dt"

# 2. 验证 V1 表的索引
sudo docker exec ascend-postgres psql -U agentbus -d agentbus -c \
  "SELECT indexname FROM pg_indexes WHERE tablename IN ('agent_bus_forwarding_outbox', 'agent_bus_forwarding_inbox');"
# 应看到:ix_outbox_claim_due + pk_outbox + pk_inbox

# 3. 验证 V2 表的索引
sudo docker exec ascend-postgres psql -U agentbus -d agentbus -c \
  "SELECT indexname FROM pg_indexes WHERE tablename = 'agent_registry_mvp';"
# 应看到:idx_agent_registry_mvp_search_tsv + idx_agent_registry_mvp_tenant_capability + ix_agent_registry_mvp_heartbeat_due + agent_registry_mvp_pkey

# 4. 验证 V3 新增列
sudo docker exec ascend-postgres psql -U agentbus -d agentbus -c \
  "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'agent_bus_forwarding_outbox' AND column_name IN ('correlation_id', 'event_type');"
# 应看到 2 行:correlation_id character varying / event_type character varying

# 5. 验证 RLS 已启用
sudo docker exec ascend-postgres psql -U agentbus -d agentbus -c \
  "SELECT tablename, rowsecurity FROM pg_tables WHERE schemaname = 'public' AND rowsecurity = true;"
# 应看到 3 行:agent_bus_forwarding_outbox / agent_bus_forwarding_inbox / agent_registry_mvp
```

### 步骤 4(可选):标记 Flyway 已执行

如果手动建表后想让 Flyway 后续启动时跳过迁移(避免重复执行报错),手动插入 `flyway_schema_history` 记录:

```bash
sudo docker exec -i ascend-postgres psql -U agentbus -d agentbus << 'EOF'
CREATE TABLE IF NOT EXISTS flyway_schema_history (
    installed_rank INT NOT NULL,
    version VARCHAR(50),
    description VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INT,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMPTZ NOT NULL DEFAULT now(),
    execution_time INT NOT NULL,
    success BOOLEAN NOT NULL
);
ALTER TABLE flyway_schema_history ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);
CREATE INDEX flyway_schema_history_s_idx ON flyway_schema_history (success);

INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success) VALUES
    (1, '1', 'create agent bus forwarding outbox inbox', 'SQL', 'V1__create_agent_bus_forwarding_outbox_inbox.sql', NULL, 'agentbus', 0, true),
    (2, '2', 'create agent registry mvp', 'SQL', 'V2__create_agent_registry_mvp.sql', NULL, 'agentbus', 0, true),
    (3, '3', 'add outbox correlation event type', 'SQL', 'V3__add_outbox_correlation_event_type.sql', NULL, 'agentbus', 0, true);
EOF
```

> **注意**:`checksum` 设为 NULL,Flyway 启动时会跳过 checksum 校验(对 NULL checksum 不校验)。如果后续 event-bus 启动时 Flyway autoconfig 仍未触发(当前情况),此表也无副作用。

### 步骤 5(可选):完全重置数据库

如果需要从头重建数据库(清空所有表 + 重新建表):

```bash
# 删除所有表 + flyway_schema_history(级联删除)
sudo docker exec ascend-postgres psql -U agentbus -d agentbus -c \
  "DROP TABLE IF EXISTS agent_bus_forwarding_outbox, agent_bus_forwarding_inbox, agent_registry_mvp, flyway_schema_history CASCADE;"

# 然后重新执行步骤 2
```

---

# 第二部分:构建 + 启动进程

## 2.1 进程拓扑

共 5 个进程(2 个 Docker 容器 + 3 个 Java 进程):

```
┌─────────────────┐   ┌──────────────────┐   ┌──────────────────┐
│ Postgres (5432) │   │ RocketMQ NameSrv │   │ RocketMQ Broker  │
│  (Docker 容器)  │   │     (9876)       │   │  (10909/10911)   │
└─────────────────┘   └──────────────────┘   └──────────────────┘
            ▲                    ▲                    ▲
            │                    │                    │
   ┌────────┴────────┐  ┌────────┴───────────────────┴────────┐
   │ agent-bus       │  │  agent-bus fat-jar (同一个 jar)      │
   │ fat-jar         │  │  --spring.profiles.active=eventbus   │
   │ --spring.profiles.active=gateway  │  (event-bus relay +   │
   │  (gateway 进程) │   │   registry 进程)                    │
   └────────┬────────┘  └──────────────────┬───────────────────┘
            │                              │
            └──────────┬───────────────────┘
                       │
              ┌────────┴────────────────┐
              │ TempRuntimeMain         │
              │ (测试 agent-runtime)    │
              └─────────────────────────┘
```

- **gateway 与 event-bus 是同一个 fat-jar**,通过 `--spring.profiles.active` 区分
- **event-bus profile 内同时启动**:relay worker(forward+response)+ registry(registry-mvp controller + probe scheduler + Flyway V2)
- **Postgres + RocketMQ 在 WSL Ubuntu 的 Docker 中**

### 进程编排说明(重要)

`TempClientMain` 本身会启动一个 Spring context(gateway profile),**它就是 gateway 进程**。因此有两种编排方式:

| 方式 | event-bus jar | gateway jar | TempClientMain | TempRuntimeMain | 说明 |
|------|------|------|------|------|------|
| **方式 A(推荐)** | ✅ 启动 | ❌ 不启动 | ✅ 启动(内含 gateway) | ✅ 启动 | TempClientMain 内含 gateway context,直接调 `routeClientRequest` |
| 方式 B | ✅ 启动 | ✅ 启动 | ✅ 启动 | ✅ 启动 | 两个 gateway 会竞争同一 outbox + RocketMQ group,**不推荐** |

**本指导使用方式 A**:不单独启动 gateway jar,而是用 `TempClientMain` 作为 gateway + client 二合一。

---

## 2.2 构建 agent-bus fat-jar

在 Windows PowerShell 中:

```powershell
# 1. 确保父 pom 已安装(只需一次)
mvn install -N -q

# 2. 构建 agent-bus fat-jar(跳过测试加速)
mvn -f agent-bus/pom.xml package -DskipTests -q
# 产物:agent-bus/target/agent-bus-0.2.0-SNAPSHOT.jar (Spring Boot fat-jar)

# 3. 验证 fat-jar 可执行(看到 Spring Boot 启动日志后立即 Ctrl+C)
java -jar agent-bus\target\agent-bus-0.2.0-SNAPSHOT.jar --help 2>&1 | Select-Object -First 5
```

**关键点**:
- 使用 `-f agent-bus/pom.xml` 避开 `agent-client/middleware/evolve` 模块的 0.1.0-SNAPSHOT 父 pom 问题
- `spring-boot-maven-plugin` 的 `repackage` goal 已配置,直接产出可运行 fat-jar

---

## 2.3 准备测试类 classpath

`TempRuntimeMain` 和 `TempClientMain` 都在 `test` 目录,不在 fat-jar 中。需要单独构建一个包含测试类的 classpath:

```powershell
# 1. 构建测试类的 classpath(maven 会把 main + test + 依赖都列出来)
mvn -f agent-bus/pom.xml dependency:build-classpath "-Dmdep.outputFile=cp.txt" -q
$cp = "agent-bus\target\classes;agent-bus\target\test-classes;" + (Get-Content agent-bus\cp.txt)

# 2. 验证 TempRuntimeMain 可加载
java -cp $cp com.huawei.ascend.bus.test.TempRuntimeMain --help
# 应看到完整使用说明

# 3. 验证 TempClientMain 可加载
java -cp $cp com.huawei.ascend.bus.test.TempClientMain --help
# 应看到完整使用说明
```

**关键点**:
- `mvn dependency:build-classpath` 输出到 `cp.txt`,后续运行可复用
- classpath 顺序:`classes;test-classes;依赖`

---

## 2.4 启动 4 个进程

> **顺序重要**:Docker → event-bus → TempRuntimeMain → TempClientMain(最后,触发请求)

### 2.4.1 启动 Docker 容器(第一组)

参见 [1.2 启动 RocketMQ + Postgres 一键编排](#12-启动-rocketmq--postgres-一键编排)。

确认容器健康后继续。

### 2.4.2 启动 event-bus + registry 进程(第一个 Java 进程)

```powershell
# 在 Windows PowerShell 中(新开一个窗口)
$env:SPRING_PROFILES_ACTIVE = "eventbus"
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/agentbus"
$env:SPRING_DATASOURCE_USERNAME = "agentbus"
$env:SPRING_DATASOURCE_PASSWORD = "agentbus"

java -jar agent-bus\target\agent-bus-0.2.0-SNAPSHOT.jar `
  --spring.profiles.active=eventbus `
  --agent-bus.nameserver=localhost:9876 `
  --agent-bus.namespace=ascend-prod `
  --agent-bus.producer-group=eventbus-producer `
  --agent-bus.gateway-service-id=gateway-01 `
  --agent-bus.event-bus-service-id=eventbus-01 `
  --agent-bus.tenant=tenant-a `
  --agent-bus.poll-wait-millis=3000 `
  --agent-bus.accept-timeout-ms=30000 `
  --agent-bus.response-timeout-ms=60000 `
  --agent-bus.lease-duration-ms=60000
```

**预期日志**:
- Flyway 迁移:`Successfully applied 3 migrations` (V1 outbox/inbox + V2 registry + V3 correlation)
- `RelayProducer started` (group=`eventbus-producer-relay`)
- `forwardRelayConsumer.subscribe` → `ascend_bus_invocation_req` / `ascend_bus_a2a_req`
- `responseRelayConsumer.subscribe` → `ascend_bus_invocation_resp_in` / `ascend_bus_a2a_resp_in`
- `RelayScheduler` 启动(forwardRelayTick + responseRelayTick 每 1s tick)
- Registry controller 启动(端口 8080,`POST /registry/runtime` 等)
- **进程不会退出**(保持运行)

### 2.4.3 启动测试用 agent-runtime(第二个 Java 进程)

```powershell
# 新开一个 PowerShell 窗口(使用 2.3 构建的 classpath)
$cp = "agent-bus\target\classes;agent-bus\target\test-classes;" + (Get-Content agent-bus\cp.txt)

# 默认 BLOCKING 模式(§6.2.1)
java -cp $cp com.huawei.ascend.bus.test.TempRuntimeMain `
  --nameserver localhost:9876 `
  --runtime runtime-01 `
  --tenant tenant-a `
  --mode BLOCKING `
  --route invocation `
  --verbose
```

**预期日志**:
```
[TempRuntime runtime-01] Starting TempRuntime: nameserver=localhost:9876 runtime=runtime-01 tenant=tenant-a mode=BLOCKING route=invocation consumerGroup=runtime-runtime-01 respInTopic=ascend_bus_invocation_resp_in
[TempRuntime runtime-01] Producer started (group=runtime-producer-runtime-01)
[TempRuntime runtime-01] Consumer subscribed: group=runtime-runtime-01 route=invocation filter={tenantId=tenant-a, targetServiceId=runtime-01}
[TempRuntime runtime-01] Poll loop started (pollWaitMs=3000)
```
- 订阅 `ascend_bus_invocation_deliver`,filter `{tenantId=tenant-a, targetServiceId=runtime-01}`
- **进程不退出**,保持轮询

### 2.4.4 触发客户端请求(第三个 Java 进程,临时)

`TempClientMain` 是 gateway + client 二合一。每次运行 = 发一次请求 + 拿响应 + 退出。

```powershell
# 新开一个 PowerShell 窗口
$cp = "agent-bus\target\classes;agent-bus\target\test-classes;" + (Get-Content agent-bus\cp.txt)

# UC-01:阻塞调用(BLOCKING 模式)
java -cp $cp com.huawei.ascend.bus.test.TempClientMain `
  --spring.profiles.active=gateway `
  --agent-bus.nameserver=localhost:9876 `
  --agent-bus.namespace=ascend-prod `
  --agent-bus.gateway-service-id=gateway-01 `
  --agent-bus.event-bus-service-id=eventbus-01 `
  --agent-bus.tenant=tenant-a `
  --agent-bus.producer-group=gateway-producer `
  --agent-bus.accept-timeout-ms=10000 `
  --agent-bus.response-timeout-ms=30000 `
  --agent-bus.poll-wait-millis=3000 `
  --agent-bus.lease-duration-ms=60000 `
  --spring.datasource.url=jdbc:postgresql://localhost:5432/agentbus `
  --spring.datasource.username=agentbus `
  --spring.datasource.password=agentbus `
  --request-type RUN_CREATE `
  --target-service runtime-01 `
  --route-handle invocation `
  --capability a2a `
  --payload "hello"
```

**预期日志**:
```
==== TempClientMain ====
requestId        = <UUID>
tenantId         = tenant-a
idempotencyKey   = <UUID>
requestType      = RUN_CREATE
traceId          = <32hex>
...
---- dispatching routeClientRequest ----

==== IngressResponse ====
requestId        = <UUID>
status           = ACCEPTED
cursor           = task-1
rejectionReason  = null
elapsedMs        = <XXX>
```

---

# 第三部分:FEAT-013 黑盒测试用例

基于 `architecture/L2-Low-Level-Design/agent-bus/feat-013-client-invocation-event-forwarding.md` §6.2 用户场景,共 7 个用例。

## 测试前置:进程状态确认

| 进程 | 状态确认命令 |
|------|------|
| Postgres | `sudo docker exec ascend-postgres pg_isready -U agentbus -d agentbus` |
| RocketMQ | `sudo docker exec ascend-rocketmq-broker mqadmin clusterList -n localhost:9876` 看到 DefaultCluster |
| event-bus | 日志含 `RelayScheduler started` + `forwardRelayConsumer.subscribe` |
| runtime | 日志含 `Poll loop started` |

---

## UC-01:阻塞调用返回最终响应(§6.2.1)

**场景**:Client 发 SendMessage 阻塞调用 → 网关转发 → runtime 响应 ACCEPTED + RESPONSE(snapshot) + TERMINAL(completed) → 网关返回一次性响应给 client。

**前置**:runtime 进程 `--mode BLOCKING`

**操作**:
1. 按 2.4.4 启动 `TempClientMain`,`--request-type RUN_CREATE`
2. 等待 `TempClientMain` 返回 `IngressResponse`

**预期**:

| 检查点 | 期望 |
|------|------|
| runtime 日志 | 收到 `CLIENT_INVOCATION_REQUESTED`,依次 `SEND: INVOCATION_ACCEPTED` / `SEND: INVOCATION_RESPONSE` / `SEND: INVOCATION_TERMINAL` |
| event-bus 日志 | forward relay 转 1 条 hop2 deliver;response relay 转 3 条 resp_out |
| TempClientMain 输出 | `status=ACCEPTED, cursor=task-1` (因 TERMINAL 在窗口内到达 → `COMPLETED_RESPONSE` → ACCEPTED with taskId) |
| DB inbox 表 | `SELECT count(*) FROM forwarding_inbox WHERE consumer_service_id='eventbus-01'` 增加 4 条(req + 3 resp 各一条) |
| DB outbox 表 | gateway 的 outbox 有 1 条 `ACKED` 记录 |

**黑盒视角**:Client 拿到 `ACCEPTED + cursor=task-1`,相当于一次性拿到最终结果引用。

---

## UC-02:阻塞退化为 Task 引用(§6.2.2)

**场景**:窗口内只收到 ACCEPTED,最终响应超时 → 网关返回 `ACCEPTED_WITH_TASK`。

**前置**:重启 runtime 进程 `--mode ACCEPTED_ONLY`(只发 ACCEPTED,不发 RESPONSE/TERMINAL)

**操作**:
1. 按 2.4.4 启动 `TempClientMain`,设置较短超时:`--agent-bus.response-timeout-ms=10000`
2. 等待 10s 超时

**预期**:

| 检查点 | 期望 |
|------|------|
| runtime 日志 | 只 `SEND: INVOCATION_ACCEPTED`,无后续 |
| TempClientMain 输出 | `status=ACCEPTED, cursor=task-N`(因窗口超时但有 taskId → `ACCEPTED_WITH_TASK`) |
| 耗时 | ≈ `response-timeout-ms` (10s) |

---

## UC-03:接受窗口 UNKNOWN + 同键重试(§6.2.3)

**场景**:窗口内 runtime 完全无响应 → 网关返回 `UNKNOWN/DEFERRED` → client 用同 `idempotencyKey` 重试 → runtime 已建 Task → 返回同 taskId。

**前置**:重启 runtime 进程 `--mode DEFER_THEN_RESOLVE`

**操作**:
1. 第一次调用,用 `--idempotency-key K1`,`--agent-bus.accept-timeout-ms=10000`
2. 等待 10s 超时,返回 `DEFERRED`(runtime 第一次收到请求,创建 task 但不发响应)
3. 第二次调用,**用同一 `--idempotency-key K1`**(runtime 第二次命中 `taskByIdempotencyKey`,复用 taskId,发 BLOCKING 响应)

**预期**:

| 检查点 | 期望 |
|------|------|
| 第一次返回 | `status=DEFERRED, cursor=null`(无 accepted → UNKNOWN) |
| 第二次返回 | `status=ACCEPTED, cursor=task-N`(同 taskId,证明幂等) |
| runtime 日志(第一次) | `RECV ... idem=K1` → 无 SEND(create task only) |
| runtime 日志(第二次) | `RECV ... idem=K1` → `SEND: ACCEPTED taskId=task-N` + `RESPONSE` + `TERMINAL` |
| runtime 内部 | 第二次 `taskByIdempotencyKey` 命中 → 复用同一 `task-N`(§4.4 layer 2) |

> **模式说明**:`DEFER_THEN_RESOLVE` 在 runtime 进程内维护 `taskByIdempotencyKey` 状态。第一次请求会创建 task 但不发响应(gateway accept window 超时 → UNKNOWN);第二次同 idempotencyKey 请求命中 map,复用同 taskId 并回放 BLOCKING 响应序列。这样无需 SIGSTOP/SIGCONT 暂停/恢复 runtime,只需两次连续调用即可验证"UNKNOWN + 同键重试 + 幂等复用 taskId"完整链路。

---

## UC-04:流式调用建立(§6.2.4)

**场景**:Client 发 SendStreamingMessage → runtime 返回 ACCEPTED + STREAM_READY(streamRef) → 网关返回 ACCEPTED with streamRef cursor。

**前置**:重启 runtime 进程 `--mode STREAMING`

**操作**:
1. 按 2.4.4 启动 `TempClientMain`,`--request-type RUN_CREATE`

**预期**:

| 检查点 | 期望 |
|------|------|
| runtime 日志 | `SEND: INVOCATION_ACCEPTED` + `SEND: INVOCATION_STREAM_READY` (extra=`streamRef=stream://task-N`) |
| TempClientMain 输出 | `status=ACCEPTED, cursor=stream://task-N` (因 STREAM_READY → `STREAM_READY` 状态,cursor=streamRef) |
| 后续 | (S4 才实现真正的 SSE 桥接,当前只返回 stream 引用 cursor) |

---

## UC-05:取消已有 Task(§6.2.5)

**场景**:Client 发 CancelTask → runtime 返回 `INVOCATION_TERMINAL(status=cancelled)`。

**前置**:runtime `--mode BLOCKING`(FEAT-001 stub)

**操作**:
1. 先调一次 RUN_CREATE,拿到 `task-N`
2. 调用 `TempClientMain`,`--request-type RUN_CANCEL --payload "task-N"`

**预期**:

| 检查点 | 期望 |
|------|------|
| runtime 日志 | RECV `CLIENT_INVOCATION_CANCEL_REQUESTED` → `SEND: INVOCATION_TERMINAL status=cancelled` (FEAT-001 stub 路径) |
| TempClientMain 输出 | `status=ACCEPTED` (TERMINAL → `COMPLETED_RESPONSE`) |

---

## UC-06:查询 Task(§6.2.5)

**场景**:Client 发 GetTask/ListTasks → runtime 返回 `INVOCATION_RESPONSE(status=snapshot)`。

**前置**:runtime `--mode BLOCKING`

**操作**:
1. 调用 `TempClientMain`,`--request-type RUN_GET --payload "task-N"`

**预期**:

| 检查点 | 期望 |
|------|------|
| runtime 日志 | RECV `CLIENT_INVOCATION_QUERY_REQUESTED` → `SEND: INVOCATION_RESPONSE status=snapshot` |
| TempClientMain 输出 | `status=ACCEPTED` (RESPONSE → `COMPLETED_RESPONSE`) |

---

## UC-07:服务端拒绝(§6.2.5)

**场景**:服务端发 `INVOCATION_REJECTED` → 网关返回 `REJECTED`。

**前置**:`TempRuntimeMain` 以 `--mode REJECT` 启动。

**操作**:
1. 重启 runtime,加 `--mode REJECT`
2. 调用 `TempClientMain`,`--request-type RUN_CREATE`

**预期**:

| 检查点 | 期望 |
|------|------|
| runtime 日志 | `SEND: eventType=INVOCATION_REJECTED ... taskId=null status=rejected target=gateway-01` |
| TempClientMain 输出 | `status=REJECTED, rejectionReason=server-policy-rejected`(不伪造 taskId) |

---

## 替代方案:跑 RealBrokerTwoHopRelayIntegrationTest

**最关键也是最快的 E2E 验证**:跑 `RealBrokerTwoHopRelayIntegrationTest`(它已等价于 UC-01 + 跨租户过滤 + 幂等去重三个核心场景):

```powershell
$env:ROCKETMQ_NAMESERVER = "localhost:9876"
mvn -f agent-bus/pom.xml test `
  -Dtest=RealBrokerTwoHopRelayIntegrationTest `
  -DfailIfNoTests=false 2>&1 | Select-Object -Last 40
```

**这个 IT 测试会验证**:gateway 发 hop1 → forward relay 转 hop2 deliver → TempRuntime 收到并响应 → response relay 转 resp_out → gateway acceptWindow 收到响应。**4 个 test case 全绿即 E2E 通过**。

---

# 第四部分:数据库验证查询

E2E 测试用例的检查点需要查询 Postgres 验证 outbox/inbox/registry 表状态。以下命令均在 WSL 中执行(假设 Postgres 容器名为 `ascend-postgres`)。

## 4.1 查看各表记录数

快速判断哪些表有数据:

```bash
sudo docker exec ascend-postgres psql -U agentbus -d agentbus -c \
  "SELECT 'agent_bus_forwarding_outbox' AS table_name, count(*) AS rows FROM agent_bus_forwarding_outbox
   UNION ALL
   SELECT 'agent_bus_forwarding_inbox', count(*) FROM agent_bus_forwarding_inbox
   UNION ALL
   SELECT 'agent_registry_mvp', count(*) FROM agent_registry_mvp;"
```

## 4.2 查看 outbox 转发表(发送方持久化队列)

FEAT-013 的 gateway 在此写入请求,relay worker 从此消费转发。

```bash
# 查看所有记录(按时间倒序,最近 20 条)
sudo docker exec ascend-postgres psql -U agentbus -d agentbus -c \
  "SELECT tenant_id, message_id, source_service_id, target_service_id,
          route_handle, status, attempt_count, event_type, correlation_id,
          to_timestamp(created_at/1000) AS created_time
   FROM agent_bus_forwarding_outbox
   ORDER BY created_at DESC
   LIMIT 20;"
```

**字段含义**:

| 字段 | 含义 |
|------|------|
| `status` | PENDING / DISPATCHING / ACKED / RETRY_SCHEDULED / DLQ / EXPIRED |
| `lease_owner` | 当前持有租约的 relay worker(空=未持有) |
| `event_type` | FEAT-013 新增:事件类型(如 `CLIENT_INVOCATION_REQUESTED`) |
| `correlation_id` | FEAT-013 新增:关联 ID,用于 response 匹配 |
| `attempt_count` | 重试次数 |

## 4.3 查看 inbox 去重表(接收方)

event-bus relay 在此记录消费的消息(幂等去重)。

```bash
sudo docker exec ascend-postgres psql -U agentbus -d agentbus -c \
  "SELECT tenant_id, message_id, consumer_service_id, status, failure_code,
          to_timestamp(received_at/1000) AS received_time
   FROM agent_bus_forwarding_inbox
   ORDER BY received_at DESC
   LIMIT 20;"
```

**字段含义**:

| 字段 | 含义 |
|------|------|
| `status` | RECEIVED / DUPLICATE_SUPPRESSED / CONSUMED / REJECTED |
| `consumer_service_id` | 消费方服务 ID(如 `eventbus-01`) |
| `failure_code` | 失败原因(如 `duplicate_suppressed`) |

## 4.4 查看 agent_registry_mvp 注册表

Agent 注册信息(可通过 `POST /registry/runtime` 注册或由 TempRuntime 启动时注册)。

```bash
sudo docker exec ascend-postgres psql -U agentbus -d agentbus -c \
  "SELECT tenant_id, agent_id, endpoint_url, status, capabilities,
          to_timestamp(last_heartbeat/1000) AS last_heartbeat_time
   FROM agent_registry_mvp
   ORDER BY last_heartbeat DESC;"
```

**字段含义**:

| 字段 | 含义 |
|------|------|
| `status` | ONLINE / DEGRADED / OFFLINE |
| `last_heartbeat` | 最近心跳时间(epoch 毫秒) |
| `capabilities` | Agent 能力(如 `["a2a"]`) |
| `endpoint_url` | Agent runtime 的访问地址 |

## 4.5 一键查看所有表状态(推荐)

```bash
sudo docker exec ascend-postgres psql -U agentbus -d agentbus << 'EOF'
\echo '=== 表记录数 ==='
SELECT 'agent_bus_forwarding_outbox' AS table_name, count(*) AS rows FROM agent_bus_forwarding_outbox
UNION ALL SELECT 'agent_bus_forwarding_inbox', count(*) FROM agent_bus_forwarding_inbox
UNION ALL SELECT 'agent_registry_mvp', count(*) FROM agent_registry_mvp;

\echo ''
\echo '=== outbox 最近 5 条 ==='
SELECT message_id, source_service_id, target_service_id, status, event_type
FROM agent_bus_forwarding_outbox ORDER BY created_at DESC LIMIT 5;

\echo ''
\echo '=== inbox 最近 5 条 ==='
SELECT message_id, consumer_service_id, status
FROM agent_bus_forwarding_inbox ORDER BY received_at DESC LIMIT 5;

\echo ''
\echo '=== registry 所有 Agent ==='
SELECT agent_id, endpoint_url, status FROM agent_registry_mvp;
EOF
```

## 4.6 进入 psql 交互式 shell

支持 SQL 命令补全,便于临时查询:

```bash
sudo docker exec -it ascend-postgres psql -U agentbus -d agentbus
```

进入后常用命令:
- `\dt` - 列出所有表
- `\d agent_registry_mvp` - 查看表结构
- `SELECT * FROM agent_registry_mvp;` - 查询所有数据
- `\q` - 退出

## 4.7 UC 用例对应的关键查询

| UC 用例 | 关键查询 |
|------|------|
| UC-01 阻塞调用 | `SELECT count(*) FROM agent_bus_forwarding_inbox WHERE consumer_service_id='eventbus-01'`(应增 4 条:req + 3 resp) |
| UC-01 阻塞调用 | `SELECT status FROM agent_bus_forwarding_outbox WHERE source_service_id='gateway-01'`(应为 ACKED) |
| UC-03 幂等重试 | `SELECT message_id, count(*) FROM agent_bus_forwarding_outbox GROUP BY message_id HAVING count(*) > 1`(应无重复,同 idempotencyKey 复用同一 message_id) |
| UC-05 取消 | `SELECT event_type FROM agent_bus_forwarding_outbox WHERE event_type LIKE '%CANCEL%'`(应有 1 条) |

---

# 第五部分:进程退出 / 重置

```powershell
# 关闭所有 Java 进程(gateway / event-bus / runtime)
# 各窗口 Ctrl+C 即可(有 shutdown hook)
```

```bash
# 关闭 Docker 容器(WSL 中)
sudo docker compose down   # 关闭并删除容器(数据保留在 volume)
# 或
sudo docker compose stop   # 仅停止,保留容器

# 清空 DB 数据(完全重置——方式 A:删 volume 重建容器)
sudo docker compose down -v  # 删除 volume
sudo docker compose up -d postgres rocketmq-nameserver rocketmq-broker rocketmq-init
# 然后重新执行 1.4 步骤 2 手动建表

# 清空 DB 数据(完全重置——方式 B:仅删表,不删容器)
# 见 1.4 步骤 5
```

---

# 第六部分:完成确认清单

执行 E2E 时,请逐项反馈:

1. ✅ 1.1/1.2:`docker compose up -d` 成功,4 个容器健康
2. ✅ 1.4:数据库表已创建(手动执行 V1/V2/V3 或 Flyway 自动迁移),`\dt` 看到 3 张表 + 索引 + RLS
3. ✅ 2.2:`agent-bus-0.2.0-SNAPSHOT.jar` 构建成功
4. ✅ 2.3:classpath 构建成功,`TempRuntimeMain --help` / `TempClientMain --help` 正常
5. ✅ 2.4.2:event-bus 进程启动,relay scheduler 启动,无 "polled before subscribe" / "connect to 172.x.x.x failed"
6. ✅ 2.4.3:TempRuntimeMain 启动,订阅 deliver topic
7. ✅ UC-01 ~ UC-06:逐个用例执行结果

**最关键验证**:跑 `RealBrokerTwoHopRelayIntegrationTest`,4 个 test 全绿。

---

# 附录:常见问题

## Q1: `sudo docker ps` 报 "Cannot connect to the Docker daemon"

→ Docker daemon 未启动。执行 `sudo service docker start`。

## Q2: Windows 侧 `localhost:5432` 连不上

→ WSL2 端口转发偶尔失效。在 Windows PowerShell 中执行 `wsl --shutdown` 然后重新打开 WSL,或检查 Windows 的 `vEthernet (WSL)` 网卡防火墙规则。

## Q3: RocketMQ broker 启动失败 / `enablePropertyFilter` 不生效

→ 检查 `broker.conf` 是否挂载到容器内,且 `broker.conf` 中 `enablePropertyFilter=true`。`docker-compose.yml` 中 `rocketmq-broker` 服务的 `command` 应包含 `-c /etc/rocketmq/broker.conf`。

## Q4: Flyway 迁移未运行 / 报 `relation "xxx" does not exist`

→ 检查 [application.yml](file:///d:/code/spring-ai-ascend-d00650599-2/spring-ai-ascend/agent-bus/src/main/resources/application.yml) 是否在 fat-jar 内(用 `jar tf agent-bus/target/agent-bus-*.jar | Select-String application.yml` 确认)。该文件显式配置了 `spring.flyway.enabled=true` + `baseline-on-migrate=true`,正常情况下 Flyway 自动执行 V1-V3。若仍失败,手动执行 3 个 SQL 脚本(见 1.4 步骤 2),或手动插入 `flyway_schema_history` 记录(见 1.4 步骤 4)。若 `flyway_schema_history` 表残留半完成状态,需 `DROP TABLE` 后重建(见 1.4 步骤 5)。

## Q5: event-bus 启动后立即退出

→ 检查日志中是否有 `BeanCreationException`。常见原因:
- RocketMQ nameserver 未启动(端口 9876 不通)
- Postgres 未启动(端口 5432 不通)
- 端口 8080 被占用(registry controller)

## Q6: TempRuntimeMain 收不到消息

→ 检查:
- runtime 的 `--tenant` 是否与 event-bus 的 `--agent-bus.tenant` 一致
- runtime 的 `--runtime` 是否与 client 的 `--target-service` 一致
- broker 是否启用 `enablePropertyFilter`(否则 SQL92 filter 不生效,消息不会被投递)
- `ascend_bus_invocation_deliver` topic 是否存在(`docker exec ascend-rocketmq-broker mqadmin topicList -n localhost:9876 | grep deliver`)

## Q7: TempClientMain 报 "BeanCreationException: gatewayResponseSubscription"

→ 说明已有另一个 gateway 进程在运行(端口/producer-group 冲突)。确保没有同时运行 standalone gateway jar 和 TempClientMain(二选一,见 [2.1 进程编排说明](#进程编排说明重要))。

## Q8: 想用 docker-compose 一键启动(RocketMQ + Postgres)

→ 在 WSL 中:
```bash
cd /mnt/d/code/spring-ai-ascend-d00650599-2/spring-ai-ascend
sudo docker compose up -d postgres rocketmq-nameserver rocketmq-broker rocketmq-init
```

## Q9: 手动启动 RocketMQ 时 broker 注册不到 nameserver

→ 检查:
- broker 容器是否加入了自定义网络 `ascend-net`(必须 `--network ascend-net`)
- `NAMESRV_ADDR` 是否为 `rocketmq-nameserver:9876`(容器名 + 端口,不是 `localhost`)
- nameserver 容器是否先于 broker 启动(broker 有重试机制,但仍需 nameserver 最终可达)
- 用 `sudo docker exec ascend-rocketmq-broker sh -c 'cat /etc/hosts'` 确认 DNS 解析

## Q10: 手动启动时 `mqadmin updatetopic` 报 "cluster not exist"

→ 检查:
- `broker.conf` 中 `brokerClusterName=DefaultCluster` 是否生效(必须用 `-c` 加载)
- broker 是否已成功注册到 nameserver(`mqadmin clusterList` 应看到 DefaultCluster)
- 等待 10-30s 后重试(broker 注册需要时间)

## Q11: event-bus 启动报 `relation "agent_registry_mvp" does not exist` 或 `relation "agent_bus_forwarding_outbox" does not exist`

→ Flyway 未自动执行迁移脚本。检查 [application.yml](file:///d:/code/spring-ai-ascend-d00650599-2/spring-ai-ascend/agent-bus/src/main/resources/application.yml) 是否打包进 fat-jar。解决:
- 重新 `mvn -f agent-bus/pom.xml package -DskipTests` 确保 application.yml 被打包
- 或手动执行 3 个 SQL 脚本(见 1.4 步骤 2)
- 或手动插入 `flyway_schema_history` 记录后重启 event-bus(见 1.4 步骤 4)

---

## 相关文件链接

- [docker-compose.yml](file:///d:/code/spring-ai-ascend-d00650599-2/spring-ai-ascend/docker-compose.yml)
- [broker.conf](file:///d:/code/spring-ai-ascend-d00650599-2/spring-ai-ascend/broker.conf)
- [agent-bus/pom.xml](file:///d:/code/spring-ai-ascend-d00650599-2/spring-ai-ascend/agent-bus/pom.xml)
- [TempRuntimeMain.java](file:///d:/code/spring-ai-ascend-d00650599-2/spring-ai-ascend/agent-bus/src/test/java/com/huawei/ascend/bus/test/TempRuntimeMain.java)
- [TempClientMain.java](file:///d:/code/spring-ai-ascend-d00650599-2/spring-ai-ascend/agent-bus/src/test/java/com/huawei/ascend/bus/test/TempClientMain.java)
- [GatewayRuntimeConfiguration.java](file:///d:/code/spring-ai-ascend-d00650599-2/spring-ai-ascend/agent-bus/src/main/java/com/huawei/ascend/bus/gateway/runtime/GatewayRuntimeConfiguration.java)
- [EventBusRelayConfiguration.java](file:///d:/code/spring-ai-ascend-d00650599-2/spring-ai-ascend/agent-bus/src/main/java/com/huawei/ascend/bus/forwarding/runtime/relay/EventBusRelayConfiguration.java)
- [FEAT-013 LLD 文档](file:///d:/code/spring-ai-ascend-d00650599-2/spring-ai-ascend/architecture/L2-Low-Level-Design/agent-bus/feat-013-client-invocation-event-forwarding.md)
- [RealBrokerTwoHopRelayIntegrationTest.java](file:///d:/code/spring-ai-ascend-d00650599-2/spring-ai-ascend/agent-bus/src/test/java/com/huawei/ascend/bus/forwarding/runtime/transport/broker/rocketmq/RealBrokerTwoHopRelayIntegrationTest.java)

# collaboration — A2A 之上的多 agent 协同 + 评测套件

在已搭好的 A2A 协议之上,实现**多 agent 协同工作**与**多任务协同评测**。独立工作区(孤儿 Maven 模块,不进 reactor,不改平台),协同引擎**传输无关**——既能用确定性内存 worker 跑评测(无需 LLM/网络),也能桥接到真实 A2A agent。

## 协同协议(`core/`)

一个 `Coordinator` 把一批 `SubTask` 分发给一组 `Worker`,实现五种协作模式:

| 模式 | 实现 |
|---|---|
| **分发 distribution** | 按 `capability` 把子任务路由到能处理的 worker(多 worker 轮询;重试时换一个) |
| **任务令牌 task token** | 每次分发签发 `TaskToken`(tokenId + taskId + idempotencyKey + deadline,仿平台 `S2cCallbackEnvelope`);over A2A 走 `Message.metadata` |
| **令牌响应校验** | worker 必须回传它收到的令牌;`Coordinator` 校验 tokenId/taskId/幂等键/未过期,否则 `TOKEN_REJECT` |
| **hand-over 交接** | worker 可把任务交接给另一 capability(`HANDED_OVER` + 目标),协调器重新分发 |
| **回收 reclaim** | 超时/失败/校验不过 → 回收并重派(尽量换 worker),直到 `maxAttempts` |
| **校验 validation** | `ResultValidator` 把关 `COMPLETED` 结果(默认非空输出) |

每一步都落 `CoordinationEvent`(DISPATCH/HANDOVER/RECLAIM/VALIDATE_*/TOKEN_REJECT/COMPLETE/FAIL)——既是审计轨迹,也是评测打分依据。

`Worker` 是 SPI:`sim/ScriptedWorker`(确定性、可脚本化,含对抗行为如伪造令牌/空输出)用于评测;`a2a/A2aWorker`(下述)桥接真实 A2A agent。

## 评测套件(`eval/`)—— 评测任务编制 + 评测集生成

- **`EvalSetGenerator`**:生成 11 个场景的评测集(happy/分发/交接/回收成功/回收耗尽/伪造令牌/缺令牌/校验失败/人审/无 worker/混合),每个声明**预期每任务状态 + 必须出现的协作事件**。
- **生成评测集**:序列化为 [`src/main/resources/eval/collaboration-eval-set.json`](src/main/resources/eval/collaboration-eval-set.json)。
- **`EvalRunner`**:加载评测集 → 跑协调器 → 对照预期打分(状态匹配 + 必需事件齐全),确定性可复现。

```bash
./collaboration/eval.sh            # 生成评测集 + 加载回来运行 + 报告
./collaboration/eval.sh generate   # 仅(重)生成评测集 JSON
./collaboration/eval.sh run        # 仅运行现有评测集
```
输出:每场景 ✅/❌ + `11/11 场景通过` + `eval-results.json`。

## 接真实 A2A(`a2a/`)

`A2aWorker` 把一个远程 A2A agent 包成 `Worker`:用 SDK `ClientTransport` 发消息(任务令牌放 `Message.metadata`)、读回 `Task` 状态映射成 `WorkResult`、用 `cancelTask` 实现回收。这样同一个 `Coordinator` 既能编排真实 A2A agent,也能在评测里用内存 worker 复现。

## 构建

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home \
  ./mvnw -f collaboration/pom.xml -DskipTests package
```

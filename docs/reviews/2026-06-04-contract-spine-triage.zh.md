# 契约 triage —— 脊柱穿越判据 + 首轮标注（label-only）

Date: 2026-06-04
Status: criterion + first-pass labels（**只标注，不删、不改 SPI 计数**）
分支: `governance/ai-native-asset-disposition`

## 判据

`docs/contracts/*.v1.yaml` 共 40 个。**live = 被那条 always-green 端到端 sample（`samples/agent-service-a2a-llm-e2e`）真实跨越的契约**；其余为 kernel-wired（内核接好、sample 未演练）或 speculative（设计态、运行不可达）。

40 个契约配一个数天的 runtime = 架构月的过度规约——但**本轮不归档**（多数被 live L1 架构文档/ADR 引用，删会制造悬挂引用，见 disposition charter §12）。本轮只**贴状态标签**，实际降级留到派生落地后的 AST 精确 pass。

## 首轮标注（粗判，待 AST 精确化）

- **live（~15，脊柱实穿）**：a2a-envelope · agent-definition · engine-envelope · engine-port · engine-hooks · execution-request · governed-messages · model-streaming · run-event · work-item · tool-result · iam-bridge · error-class · correlation-record · checkpoint-record(stub)
- **kernel-wired（~7，内核接好、sample 未演练）**：s2c-callback · model-invocation · interrupt-registration · session-snapshot · backpressure-request · structured-output · skill-definition
- **speculative（~18，运行不可达）**：access-intent · audit-trail · config-snapshot-ref · cost-governance · federation-envelope · intercept-request · reflection-envelope · vector-store · memory-store · chat-advisor · agent-invoke-request · plan · plan-projection · planning-request · prompt-template · ingress-envelope · embedding/retriever 类

## 精确化方法（后续）

AST 扫 `agent-service` + `agent-runtime` 源码对契约类型的 import；交叉 `architecture-status.yaml` 的 `status:` 字段；按 e2e 测试覆盖回链。**speculative 的实际处置不在本轮**——按 A/B 范式，让脊柱拉动而非一次删清。

## 验证（2026-06-04 仓库级 ref-scan）

对全部 40 个契约做了仓库级引用扫描：**没有一个是零引用孤儿**。最少的（config-snapshot-ref / intercept-request / interrupt-registration / session-snapshot）也各有 ≥1 个非-catalog 的 live 引用（ADR / L1 文档 / 代码）；所谓 speculative 的（vector-store=17 · memory-store=12 · federation=6 · cost-governance=9 …）引用更多。

**结论：契约层面没有可安全删除的对象**——删任意一个都会制造悬挂引用 = bug。这与治理制品的自检结论一致：架构月的过度规约已织入语料库，无法靠一次删除变"干净"。**精简只能由运行脊柱随时间拉动/淘汰（某契约 repo-wide refs 归零时才归档），不是一次性删除动作。**

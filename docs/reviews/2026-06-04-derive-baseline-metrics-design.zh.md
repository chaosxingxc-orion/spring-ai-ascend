# 派生 baseline_metrics — 设计（design-only）

Date: 2026-06-04
Status: **IMPLEMENTED 2026-06-04** — `gate/lib/sync_baseline.py`（--check 守卫 + --write 修正，显式 confident-field allowlist）+ `count_adrs` 含 locked 修正 + CI `--check` 步（复活 Rule 82 守卫）。本轮实修 `enforcer_rows 88→41`。gate GREEN / self-tests 102/102 / --check exit 0。
分支: `governance/ai-native-asset-disposition`
前置: A 篇范式 + B 篇 charter（`docs/reviews/2026-06-04-ai-native-*.zh.md`）

## 为什么 —— 本轮自检的实测铁证

`architecture-status.yaml#baseline_metrics` 是一份**手维护台账**，跨 6+ 个面手工锁步。团队把 **Rule 82**（baseline 一致性"警察"）退役后，它**立刻、全面地漂了**，且漂移已随 #127 进入 `main`。本轮用 gate 自己的 canonical 计数器实测：

| 数 | baseline_metrics | README L196-198 | canonical 实测 | 状态 |
|---|---|---|---|---|
| active_gate_checks | 35 → **本轮修 32** | 35 → **本轮修 32** | **32** `count_gate_rules` | 一个数手改 **6 面**才一致 |
| enforcer_rows | 88 | 88 | **41** `count_enforcers` | 漂移；88-vs-41 定义存疑，**未臆改** |
| adr_count | 65 | 64 | 54 `count_adrs`(顶层) | **三个面三个数**；65=active+locked 是定义差 |
| active_engineering_rules | 54 | 55 | — | README 与 baseline 已不一致 |
| graph nodes/edges | 381/573 | **514/708** | 381/573（Rule 106 强制） | README 严重过时 |

> 修一个 `active_gate_checks` 就要手改 6 个面；其余几个数 README 与 baseline **本身就对不上**。这就是手维护投影的税，且**已在 main 上漂成事实**。这是"派生记录"必要性的最强证据——不是理论，是现场。

## 设计 —— 单一 emitter

计数器**已经存在**于 `gate/lib/build_release_evidence.py`：`count_gate_rules` / `count_enforcers` / `count_adrs` / `count_active_engineering_rules` / `graph_counts`。它们就是 live 真值。派生几乎是把现成件接起来：

1. 新增 `emit_baseline_metrics`（或给 `build_release_evidence.py` 加 `--write-baseline` 模式）：跑上述计数器 → 写 `baseline_metrics` 的**数值字段**（保留人工的 provenance 注释 = git 历史，不再承载真值）。
2. `README` / `gate/README` 的数字改为**派生注入**（构建期填充占位符，或 CI 校验"文档数字 == emitted"），不再手抄。
3. **"emitted == referenced" 恒真检查**替掉已退役的 Rule 82——因为源唯一，parity 定义上成立，不可能再漂。

## retire-on-derive

派生落地后消失的手维护面：`baseline_metrics` 数值手编、`README`/`gate/README` 数字手抄、`allowed_claim` 数字串、各 release-note 的 baseline 行。`Rule 106`（graph parity）保留但退化为"emitter 自洽"。

## 边界（本轮不做）

- **只设计，不实现**（owner 拨盘）。
- **意图层不可派生**：principle 语义、enforcer↔constraint 绑定是人写的判断，保持人工策展。
- **enforcer_rows 88-vs-41 的定义差**（行 vs 映射 vs IT-anchored）由 emitter 实现时**一次性定准**，本轮不臆断、不手改。

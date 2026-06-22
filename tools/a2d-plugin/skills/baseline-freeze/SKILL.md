---
name: baseline-freeze
description: A2D 步骤 8(基线固化)。把本轮实现的证据 + 对账结果 + 架构更新稿,固化为更新后的 4+1 架构基线 yaml,并渲染成静态审阅 HTML。发布前锁定架构真相。
---

# baseline-freeze — A2D 步骤 8 基线固化

## 何时用

闸门 2(步骤 7 后置对账)通过后、发布前。把"这一轮到底把架构真相改成了什么"固化成基线,作为下一个 A2D 周期的起点(步骤 2 现状扫描的输入)。

## 你要产出的三样东西

1. **更新后的 4+1 基线 yaml**(基线全貌,覆盖五视图 + 元素/关系事实表 + accepted/deferred/risk)
2. **静态审阅 HTML**(给人看,`render.py --mode full`)
3. **变更摘要**:本轮新增 / 修改 / 废弃了哪些元素、关系;新增 / 关闭了哪些 deferred;发布风险有无升降

## 数据模型(权威来源)

完整字段定义在 `skills/_shared/baseline-rw/baseline.py` 的模块 **docstring**。对应 `review-packets/_TEMPLATE.md` §3(4+1 View Model)+ §9(基线)。

活样例:`examples/agent-bus-forwarding-baseline.yaml`。**先读这份样例再动手。**

## 流程

1. **读输入**(步骤 7 的产物):
   - 本轮实现的证据(代码改动、契约测试、harness 结果)
   - 步骤 6 实现与步骤 5 计划的对账差异
   - 步骤 3 的架构更新稿(本轮预期的 delta)
   - 上一版基线 yaml(若存在)

2. **产出 / 更新 4+1 基线 yaml**——逐视图落事实:
   - `logical`:本轮能力块、状态机、契约有没有变?元素事实表(E-xxx)更新。
   - `development`:依赖关系、ArchUnit 边界、禁用项有没有变?
   - `process`:运行时流程、失败/重试/取消路径有没有变?
   - `physical`:部署影响、数据/租户/凭证/网络边界有没有变?
   - `scenario`:场景表(SC-xxx)——本轮有哪些场景从 draft 升到 accepted?有没有新增场景?
   - `accepted_facts`:把本轮**已验证落地**的事实加进来(每条带 source 锚点)。
   - `deferred`:**关键**——本轮打开了哪些结转项?关闭了哪些?**每条必须有 owner + trigger + next_entry。**
   - `release_risk`:本轮风险升降。

3. **校验**(deny-by-default,必须过):
   ```bash
   python skills/_shared/baseline-rw/baseline.py check <baseline.yaml>
   ```
   不过就修到过。校验规则见 `baseline.py validate()`。

4. **渲染审阅 HTML**:
   ```bash
   python skills/_shared/renderer/render.py <baseline.yaml> <output.html> --mode full
   ```

5. **报告变更摘要 + 打开 HTML** 让人审阅。

## deny-by-default 红线

这些是 `baseline.py` 会拒绝的,也是你产出时必须守的:

- 顶层 `version` / `title` / `status` 必填,`status` ∈ {accepted, draft, reviewed, superseded}
- `views` 至少含 `logical`
- 元素 `id` 唯一;`view` / `type` / `status` 枚举合法
- 关系 `from` / `to` 必须引用存在的元素 ID;`relation_type` 等枚举合法
- **`deferred` 每一项必须有 `owner` + `trigger` + `next_entry`**——没有后续入口的结转等于丢失,这是本工具最硬的一条线
- `release_risk.level` ∈ {L1, L2, L3}

## 不要做的事

- 不要编造未验证的事实塞进 `accepted_facts`(那里只放本轮有证据落地的)
- 不要把"还没决定"的架构选择写成 `accepted`——未决项进 `deferred` 或 `release_risk`
- 不要手写 HTML——HTML 永远由 `render.py` 产出,人只维护 yaml(单一真相源)
- 不要在 yaml 里放自由散文当结构化数据用——散文进 `scope` / `summary`,事实进表

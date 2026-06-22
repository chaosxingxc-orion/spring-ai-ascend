---
name: arch-update-draft
description: A2D 步骤 3(架构更新稿)。把澄清后的需求 + 现状扫描 + 上轮基线,变成本轮的架构更新提案——五视图各受什么影响、哪些元素/关系/状态要变更、有什么风险与结转,产出结构化 4+1 delta 并渲染审阅 HTML。闸门 1 前用。
---

# arch-update-draft — A2D 步骤 3 架构更新稿

## 何时用

步骤 2(现状扫描)之后、**闸门 1** 评审之前。这是 A2D 的第一个闸门:本轮"打算怎么改架构"必须在这里讲清楚,过闸门 1 才进步骤 4(派生切片计划)。

输入:步骤 1 澄清后的需求 + 步骤 2 现状扫描结果 + 上一版 4+1 基线 yaml。

## 你要产出的东西

1. **影响分析**:本轮需求会触动五视图里的哪些部分(逐视图过一遍)
2. **本轮架构 delta**:新增 / 修改 / 废弃哪些元素(E-xxx)、关系(R-xxx)、状态、契约;标 `status: draft` 的就是本轮要动但还没落地的
3. **结构化 4+1 yaml**(本轮提案,合并到基线骨架上)
4. **静态审阅 HTML**(给闸门 1 评审看)

## 数据模型

与 `baseline-freeze` 同一份模型,权威定义在 `skills/_shared/baseline-rw/baseline.py` 的模块 docstring。区别:

- 步骤 3 产出的 yaml 顶层 `status` 通常标 `draft`(尚未固化)
- 本轮**打算新增但还没实现**的元素/关系,`status: draft`;已存在不动的,`status: accepted`
- 活样例:`examples/agent-bus-forwarding-baseline.yaml`(那是步骤 8 固化后的状态;步骤 3 的产物会把其中 draft 项作为"本轮要做的")

## 流程

1. **读输入**:需求(步骤 1)+ 现状扫描(步骤 2)+ 上轮基线 yaml。

2. **影响分析**(逐视图,这是步骤 3 的核心智力活):
   - `logical`:需求要不要新增能力块 / 状态 / 契约?现有边界守得住吗?
   - `development`:要不要新增 / 改依赖?ArchUnit 边界要不要调?
   - `process`:运行时流程变不变?有没有新的失败 / 重试路径?
   - `physical`:部署影响变不变?数据 / 租户 / 凭证 / 网络边界变不变?
   - `scenario`:要支撑哪些新场景?现有场景哪些会变?

3. **产出本轮 delta yaml**:把影响分析落成结构化数据。
   - **对本轮要动的元素/关系/事实标注 `change: added|modified|removed`**——delta 视图据此着色,只详列变更项(added 绿 / modified 黄 / removed 红),未标注的视为本轮不动
   - 新增元素 `status: draft`
   - 本轮打开的结转项写进 `deferred`(同样**必须有 owner + next_entry**)
   - 本轮预见的风险写进 `release_risk`

4. **校验**:
   ```bash
   python skills/_shared/baseline-rw/baseline.py check <draft.yaml>
   ```

5. **渲染审阅 HTML(delta 模式)**:
   ```bash
   python skills/_shared/renderer/render.py <draft.yaml> <output.html> --mode delta
   ```
   delta 视图只详列 `change ≠ unchanged` 的元素/关系(added 绿 / modified 黄 / removed 红 删除线),配变更摘要置顶。活样例:`examples/agent-bus-a2a-connpool-update.yaml`。

6. **面向闸门 1 给评审摘要**:本轮要动什么、为什么、风险与结转是什么、需要评审拍板哪些点。

## deny-by-default 红线

与 `baseline-freeze` 完全一致(同一份 `baseline.py`)。重点再强调:

- **`deferred` 每项必须有 `owner` + `trigger` + `next_entry`**——架构更新稿里凡是"先不决定 / 留待后轮"的,都必须有明确后续入口,否则不许结转
- 本轮打算做但未落地的,标 `draft`,不要伪装成 `accepted`
- 关系 `from` / `to` 必须引用存在的元素(哪怕该元素本轮才新增、也是 draft)

## 与 baseline-freeze 的分工

| | arch-update-draft(步骤 3) | baseline-freeze(步骤 8) |
|---|---|---|
| 时机 | 闸门 1 前 | 闸门 2 后、发布前 |
| 性质 | 提案(打算改什么) | 固化(实际改成了什么) |
| 元素 status | 本轮待落地 = draft | 本轮已验证 = accepted |
| accepted_facts | 少(还没落地) | 多(有证据才进) |
| 输入 | 需求 + 现状 + 上轮基线 | 证据 + 对账 + 更新稿 |

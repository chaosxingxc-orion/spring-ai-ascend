# A2D Plugin

A2D (Architecture to Delivery) 工具链插件。以 4+1 架构视图为枢纽的两闸门模型。

## 这个插件做什么

把 A2D 流程里**步骤 3(架构更新稿)** 和 **步骤 8(基线固化)** 的人工流程,固化成可执行的 Claude skill,并把结构化 4+1 数据渲染成静态审阅 HTML。

流程主表见 `docs/architecture/l0/10-governance/a2d-expanded-flow.html`。构建计划见 `docs/architecture/l0/10-governance/a2d-tools-build-plan.md`。

## 架构分层

```
skill (AI 驱动)          renderer (确定性)           产物
┌─────────────┐         ┌──────────────┐         ┌──────────┐
│ arch-update │ markdown│ baseline-rw  │  yaml   │ renderer │ → 审阅 HTML
│ -draft      │ ──────> │ (load/validate) ─────> │ (jinja2) │
│ baseline    │  /需求  │              │         │          │
│ -freeze     │  → yaml │              │         │          │
└─────────────┘         └──────────────┘         └──────────┘
   step 3 / 8             _shared/baseline-rw      _shared/renderer
```

- **skill 层**:AI 驱动,负责把散文 markdown / 版本需求 → 结构化 4+1 yaml。这是步骤 3/8 的智能部分。
- **baseline-rw**:`skills/_shared/baseline-rw/baseline.py`。4+1 基线的 load/save/validate。deny-by-default 校验(必填字段、ID 唯一、引用有效、deferred 必须有后续入口)。
- **renderer**:`skills/_shared/renderer/render.py`。确定性,把 4+1 yaml 渲染成单文件审阅 HTML(内联 CSS,无外部依赖)。视觉沿用 a2d-expanded-flow.html 的色板与质感。

## 数据模型

4+1 基线 yaml 的完整结构定义在 `baseline.py` 模块 docstring,活示例在 `examples/agent-bus-forwarding-baseline.yaml`。对应 `review-packets/_TEMPLATE.md` §3(4+1 View Model)+ §9(基线)。

## 本地测试

```bash
# 1. 直接跑 renderer(不装插件)
#    full 模式(step 8 基线全貌,含内嵌架构图)
python skills/_shared/renderer/render.py examples/agent-bus-forwarding-baseline.yaml out.html
#    delta 模式(step 3 更新稿,只详列变更项)
python skills/_shared/renderer/render.py examples/agent-bus-a2a-connpool-update.yaml delta.html --mode delta

# 2. 冒烟测试(15 项,纯标准库,无需 pytest)
python tests/test_smoke.py

# 3. 作为插件安装
/plugin marketplace add ./tools/a2d-plugin
/plugin install a2d@a2d-toolkit
# 然后调用:/a2d:baseline-freeze 或 /a2d:arch-update-draft
```

## skill

| skill | 步骤 | 职责 |
|---|---|---|
| `/a2d:arch-update-draft` | step 3(闸门 1 前) | 需求+现状+基线 → 本轮 4+1 delta → 审阅 HTML(delta 视图) |
| `/a2d:baseline-freeze` | step 8 | 证据+对账+更新稿+基线 → 更新后基线 → 审阅 HTML(full 视图) |

## 范围

- ✓ renderer full 模式(基线全貌 HTML)
- ✓ renderer delta 模式(step 3 更新稿:change 字段着色,只详列变更项)
- ✓ baseline-rw load/save/validate(deny-by-default,含 change/diagrams 校验)
- ✓ step 8 baseline-freeze skill + step 3 arch-update-draft skill
- ✓ puml 嵌入(SVG 存在则嵌入图,否则内嵌 .puml 源;零外部依赖)
- ◐ puml→SVG 渲染管线(需 plantuml 环境;本地无 jar 且网络不通,见 build-plan §9 Q5)
- ✗ v2 9 步编号迁移(独立切片 S0)

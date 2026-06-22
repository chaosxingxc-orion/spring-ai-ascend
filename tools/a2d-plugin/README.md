# A2D Toolkit Plugin

A2D (Architecture to Delivery) 工具链 — 把架构治理流程(步骤 3 架构更新稿、步骤 8 基线固化)固化成 Claude skill,并把 4+1 架构数据渲染成静态审阅 HTML。

## 快速开始

### 不装插件,直接跑渲染器

```bash
cd tools/a2d-plugin
python skills/_shared/renderer/render.py examples/agent-bus-forwarding-baseline.yaml out.html
# 浏览器打开 out.html
```

### 校验一份 4+1 基线 yaml

```bash
python skills/_shared/baseline-rw/baseline.py check examples/agent-bus-forwarding-baseline.yaml
```

### 作为 Claude 插件安装

```bash
/plugin marketplace add ./tools/a2d-plugin
/plugin install a2d@a2d-toolkit
```

调用:`/a2d:baseline-freeze`、`/a2d:arch-update-draft`。

## 目录

```
tools/a2d-plugin/
├── .claude-plugin/          plugin.json + marketplace.json
├── skills/
│   ├── arch-update-draft/   step 3 skill(待实现)
│   ├── baseline-freeze/     step 8 skill
│   └── _shared/
│       ├── baseline-rw/     4+1 基线读写校验库(Python)
│       ├── renderer/        yaml → 审阅 HTML 渲染器(Python+Jinja2)
│       └── templates/4plus1/ 4+1 数据模型说明
├── examples/                真实样例(agent-bus 转发运行时基线)
└── tests/                   renderer / baseline-rw 测试
```

## 数据模型

4+1 基线 yaml 对应 `docs/architecture/l0/10-governance/review-packets/_TEMPLATE.md` §3(4+1 View Model)+ §9。完整字段见 `skills/_shared/baseline-rw/baseline.py` docstring,活示例见 `examples/`。

## 设计文档

- 流程主表:`docs/architecture/l0/10-governance/a2d-expanded-flow.html`
- 构建计划:`docs/architecture/l0/10-governance/a2d-tools-build-plan.md`

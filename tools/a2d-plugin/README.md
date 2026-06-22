# A2D Toolkit Plugin

A2D (Architecture to Delivery) 工具链 — 把架构治理流程(步骤 3 架构更新稿、步骤 8 基线固化)固化成 Claude skill,并把 4+1 架构数据渲染成静态审阅 HTML。

## 快速开始

### 不装插件,直接跑渲染器

```bash
cd tools/a2d-plugin
# step 8 基线全貌(full 模式)
python skills/_shared/renderer/render.py examples/agent-bus-forwarding-baseline.yaml out.html
# step 3 架构更新稿(delta 模式,只详列变更项)
python skills/_shared/renderer/render.py examples/agent-bus-a2a-connpool-update.yaml delta.html --mode delta
# 浏览器打开 out.html / delta.html
```

两份产物均已预渲染在 `examples/*.html`(含内嵌的 5 张 l0 架构图 .puml 源)。

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

4+1 基线 yaml 对应 `docs/architecture/l0/10-governance/review-packets/_TEMPLATE.md` §3(4+1 View Model)+ §9。完整字段见 `skills/_shared/baseline-rw/baseline.py` docstring,活示例见 `examples/`。要点:

- **change 字段**(`added|modified|removed|unchanged`):step 3 更新稿标注本轮变更,delta 模式据此着色
- **diagrams 字段**:引用 .puml 图源(相对仓库根),审阅 HTML 内嵌——同名 .svg 存在则嵌入图,否则内嵌源码块
- **deferred 项**:deny-by-default,必须有 owner + next_entry

## 设计文档

- 流程主表:`docs/architecture/l0/10-governance/a2d-expanded-flow.html`
- 构建计划:`docs/architecture/l0/10-governance/a2d-tools-build-plan.md`

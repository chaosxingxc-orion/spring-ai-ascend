# 技能创建指南

Author OpenClaw-compatible skills for WorkMate `office/skills/`.

## SKILL.md structure

- Title (`# name`)
- Short description paragraph
- `## Capabilities` bullet list
- `## SOP` numbered steps

## skill.yaml fields

```yaml
id: my-skill
name: Display name
description: One-line summary for the market card
category: dev
tags: [tag1]
source: builtin
defaultInstalled: false
skillFile: SKILL.md
```

## Install flow

Skills appear in `GET /api/v1/skills`; users install via `POST /api/v1/skills/{id}/install`.

"""Template rendering for workflow frame fields."""
from __future__ import annotations

import re
from typing import Any


_TEMPLATE_RE = re.compile(r"\{\{([^}]+)\}\}")


def _resolve_path(ctx: dict[str, Any], path: str) -> Any:
    parts = path.strip().split(".")
    cur: Any = ctx
    for part in parts:
        if isinstance(cur, dict):
            cur = cur.get(part)
        else:
            return None
    return cur


def render_value(value: Any, ctx: dict[str, Any]) -> Any:
    if not isinstance(value, str):
        return value

    def repl(match: re.Match[str]) -> str:
        resolved = _resolve_path(ctx, match.group(1))
        if resolved is None:
            return match.group(0)
        return str(resolved)

    return _TEMPLATE_RE.sub(repl, value)


def render_dict(data: dict[str, Any], ctx: dict[str, Any]) -> dict[str, Any]:
    out: dict[str, Any] = {}
    for key, value in data.items():
        if isinstance(value, dict):
            out[key] = render_dict(value, ctx)
        elif isinstance(value, list):
            out[key] = [
                render_dict(item, ctx) if isinstance(item, dict) else render_value(item, ctx)
                for item in value
            ]
        else:
            out[key] = render_value(value, ctx)
    return out

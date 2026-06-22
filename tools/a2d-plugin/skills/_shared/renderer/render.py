#!/usr/bin/env python3
"""renderer:把 4+1 基线 yaml 渲染成单文件静态审阅 HTML。

输出是自包含的单文件 HTML(CSS 内联,无外部依赖),视觉沿用
a2d-expanded-flow.html 的色板与质感,布局针对 4+1 基线内容。

用法:
    python render.py <input.yaml> <output.html> [--mode full|delta] [--title "..."]

模式:
    full   基线全貌(step 8 baseline-freeze 产物)。✓ MVP 已实现。
    delta  更新稿(step 3 arch-update-draft 产物)。◐ 接口预留。
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

# 让脚本可从任意位置运行:把 _shared/baseline-rw 加入 sys.path
_HERE = Path(__file__).resolve().parent
_SHARED = _HERE.parent  # skills/_shared
sys.path.insert(0, str(_SHARED / "baseline-rw"))

import baseline  # noqa: E402
from jinja2 import Environment, FileSystemLoader, TemplateNotFound  # noqa: E402


def render(data: dict, mode: str = "full", override_title: str | None = None) -> str:
    env = Environment(
        loader=FileSystemLoader(str(_HERE / "templates")),
        autoescape=True,
        trim_blocks=True,
        lstrip_blocks=True,
    )
    tmpl_name = "baseline.html.j2" if mode == "full" else "delta.html.j2"
    try:
        tmpl = env.get_template(tmpl_name)
    except TemplateNotFound:
        # delta 模板尚未实现时回退到 full
        tmpl = env.get_template("baseline.html.j2")
    title = override_title or data.get("title") or data.get("version") or "4+1 基线"
    return tmpl.render(baseline=data, title=title, mode=mode)


def main() -> None:
    ap = argparse.ArgumentParser(description="把 4+1 基线 yaml 渲染成审阅 HTML")
    ap.add_argument("input", help="基线 yaml 路径")
    ap.add_argument("output", help="输出 html 路径")
    ap.add_argument("--mode", default="full", choices=["full", "delta"])
    ap.add_argument("--title", default=None, help="覆盖 HTML 标题")
    ap.add_argument("--no-strict", action="store_true", help="校验失败仍渲染(开发用)")
    args = ap.parse_args()

    try:
        data = baseline.load(args.input)
    except Exception as e:
        print(f"✗ 加载失败: {e}", file=sys.stderr)
        sys.exit(1)

    errors = baseline.validate(data)
    if errors and not args.no_strict:
        print(f"✗ 基线校验失败({len(errors)} 项),拒绝渲染。加 --no-strict 强制:", file=sys.stderr)
        for e in errors:
            print(f"  - {e}", file=sys.stderr)
        sys.exit(1)

    html = render(data, mode=args.mode, override_title=args.title)
    Path(args.output).write_text(html, encoding="utf-8")
    print(f"✓ 已渲染 {args.input} → {args.output} (mode={args.mode}, {len(html)} bytes)")


if __name__ == "__main__":
    main()

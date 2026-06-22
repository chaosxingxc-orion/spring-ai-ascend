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


def _find_repo_root(start: Path) -> Path | None:
    """从 start 向上找 .git,返回仓库根(找不到返回 None)。"""
    p = start.resolve()
    while p != p.parent:
        if (p / ".git").exists():
            return p
        p = p.parent
    return None


def _load_diagrams(data: dict, base_dir: Path | None) -> list[dict]:
    """读 diagrams:同名 .svg 存在则内联,否则内嵌 .puml 源 + 渲染命令提示。

    路径相对仓库根解析(回退到 base_dir)。零外部依赖——本地无 plantuml 时
    显示源码块,有 plantuml 环境生成 .svg 放旁边即自动升级为内嵌图。
    """
    diags: list[dict] = []
    repo = _find_repo_root(base_dir) if base_dir else None
    bases = [b for b in ([repo, base_dir] if repo else [base_dir]) if b]
    for d in data.get("diagrams") or []:
        rel = d.get("path", "")
        full = next((b / rel for b in bases if (b / rel).exists()), None)
        entry = {"path": rel, "caption": d.get("caption", "")}
        if full:
            entry["source"] = full.read_text(encoding="utf-8")
            svg = full.with_suffix(".svg")
            if svg.exists():
                entry["svg"] = svg.read_text(encoding="utf-8")
        else:
            entry["missing"] = True
        diags.append(entry)
    return diags


def render(data: dict, mode: str = "full", override_title: str | None = None, base_dir: Path | None = None) -> str:
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
    ctx = {"baseline": data, "title": title, "mode": mode, "diagrams": _load_diagrams(data, base_dir)}
    if mode == "delta":
        ctx["change_counts"] = baseline.change_counts(data)
    return tmpl.render(**ctx)


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

    html = render(data, mode=args.mode, override_title=args.title, base_dir=Path(args.input).resolve().parent)
    Path(args.output).write_text(html, encoding="utf-8")
    print(f"✓ 已渲染 {args.input} → {args.output} (mode={args.mode}, {len(html)} bytes)")


if __name__ == "__main__":
    main()

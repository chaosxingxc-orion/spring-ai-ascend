#!/usr/bin/env python3
"""Byte-identical render-idempotency gate driver for Rule G-13.

Reads `docs/governance/templates/surface-classification.yaml`, and for
every entry whose `bucket` is `templated` or `hybrid`:

  1. Verifies `template:` exists on disk.
  2. Verifies `output:` exists on disk.
  3. Invokes the `context_loader:` plugin in
     `gate/lib/load_render_context.py` to assemble the render context.
  4. Runs `gate/lib/render_template.py` in `--check` mode and compares
     the rendered buffer against the on-disk `output:` file.
  5. Skips byte comparison when the output is explicitly listed in the
     entry's `historical_grandfathered_list`.
  6. Reports per-template PASS / FAIL with a unified-diff snippet on
     mismatch.

Exit codes:
  0  - all entries (possibly zero) PASS
  1  - structural problem with the registry itself (missing keys, etc.)
  2  - at least one template entry FAIL'd

When the templates list is empty (today's W0 state), the gate is
vacuously satisfied — exit 0.

Authority: ADR-0119; Rule G-13 sub-clause .b; rule card
docs/governance/rules/rule-G-13.md.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any

import yaml


REPO_DEFAULT = Path(__file__).resolve().parents[2]


def _load_yaml(path: Path) -> Any:
    if not path.is_file():
        return None
    with path.open("r", encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def _validate_registry(registry: Any) -> list[str]:
    errs: list[str] = []
    if not isinstance(registry, dict):
        return ["registry root is not a mapping"]
    if "schema_version" not in registry:
        errs.append("missing schema_version key")
    if "templates" not in registry:
        errs.append("missing templates key")
    elif not isinstance(registry["templates"], list):
        errs.append("templates key is not a list")
    return errs


def _validate_entry(entry: Any, idx: int) -> list[str]:
    errs: list[str] = []
    if not isinstance(entry, dict):
        return [f"entry #{idx}: not a mapping"]
    for required in ("template", "output", "context_loader", "bucket"):
        if required not in entry:
            errs.append(f"entry #{idx} ({entry.get('output', '?')}): missing key {required!r}")
    bucket = entry.get("bucket")
    if bucket not in (None, "templated", "hybrid"):
        errs.append(
            f"entry #{idx} ({entry.get('output', '?')}): "
            f"bucket must be 'templated' or 'hybrid'; got {bucket!r}"
        )
    return errs


def _check_entry(repo: Path, entry: dict[str, Any], verbose: bool) -> tuple[bool, str]:
    template_path = repo / entry["template"]
    output_path = repo / entry["output"]
    plugin_name = entry["context_loader"]

    if not template_path.is_file():
        return False, f"template file missing on disk: {entry['template']}"
    if not output_path.is_file():
        return False, f"rendered output missing on disk: {entry['output']}"

    grandfathered_list = entry.get("historical_grandfathered_list")
    if grandfathered_list:
        listed_path = repo / str(grandfathered_list)
        if listed_path.is_file():
            grandfathered_outputs = {
                line.strip()
                for line in listed_path.read_text(encoding="utf-8").splitlines()
                if line.strip() and not line.lstrip().startswith("#")
            }
            if entry["output"] in grandfathered_outputs:
                return True, f"historical-grandfathered by {grandfathered_list}"

    # The render engine + context loader live alongside this checker. The
    # `repo` argument is the *target* repo whose template registry we're
    # validating; the engines themselves resolve relative to THIS script's
    # location so the checker works against synthetic temp repos in tests.
    lib_dir = Path(__file__).resolve().parent
    sys.path.insert(0, str(lib_dir))
    try:
        import load_render_context as lrc  # type: ignore[import-not-found]
        import render_template as rt  # type: ignore[import-not-found]
    finally:
        sys.path.pop(0)

    rt._enforce_determinism_env()

    args_ns = argparse.Namespace(
        seed=str(repo / entry["seed"]) if entry.get("seed") else None,
        run_self_tests=False,
        include_maven_reports=False,
    )
    try:
        context = lrc.load_context(plugin_name, repo, args_ns)
    except SystemExit as exc:
        return False, f"context loader {plugin_name!r} raised: {exc.code}"
    except Exception as exc:  # noqa: BLE001 - we want to surface anything
        return False, f"context loader {plugin_name!r} raised {type(exc).__name__}: {exc}"

    template_text = template_path.read_text(encoding="utf-8")
    try:
        rendered = rt.render(template_text, context)
    except Exception as exc:  # noqa: BLE001
        return False, f"render failed: {type(exc).__name__}: {exc}"

    existing = output_path.read_text(encoding="utf-8")
    if existing == rendered:
        return True, "byte-identical"
    diff = rt._diff_snippet(existing, rendered)
    return False, f"render drift detected\n{diff}"


def check_all(repo: Path, verbose: bool = False) -> int:
    registry_path = repo / "docs" / "governance" / "templates" / "surface-classification.yaml"
    registry = _load_yaml(registry_path)
    if registry is None and not registry_path.is_file():
        print(f"FAIL: registry missing -- {registry_path}", file=sys.stderr)
        return 1
    errs = _validate_registry(registry)
    if errs:
        for err in errs:
            print(f"FAIL: registry: {err}", file=sys.stderr)
        return 1

    templates = registry.get("templates") or []
    if not templates:
        print("PASS: template-render idempotency (registry vacuous; 0 entries)")
        return 0

    failure_count = 0
    for idx, entry in enumerate(templates):
        entry_errs = _validate_entry(entry, idx)
        if entry_errs:
            for err in entry_errs:
                print(f"FAIL: {err}", file=sys.stderr)
                failure_count += 1
            continue
        ok, message = _check_entry(repo, entry, verbose)
        prefix = "PASS" if ok else "FAIL"
        line = f"{prefix}: {entry['output']} ({entry['context_loader']}) -- {message}"
        if ok:
            print(line)
        else:
            print(line, file=sys.stderr)
            failure_count += 1

    if failure_count:
        print(f"FAIL: {failure_count} template(s) drifted", file=sys.stderr)
        return 2
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(REPO_DEFAULT), help="Repository root")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args(argv)
    return check_all(Path(args.root).resolve(), verbose=args.verbose)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

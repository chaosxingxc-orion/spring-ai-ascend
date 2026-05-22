#!/usr/bin/env python3
"""
gate/lib/validate_recurring_families.py — rc19 Wave 1 (ADR-0096)

Python-backed yaml validation for the recurring-defect-family ledger.
Replaces the awk-based parser in gate/lib/check_recurring_families.sh
(rc18 Wave 1) which had structural limitations:

- awk couldn't distinguish `- id:` inside `root_cause: |` literal blocks
  from real family boundaries (ADV-RC18-4)
- awk couldn't validate semantic dates (9999-12-31 passed ISO regex)
- awk couldn't compare yaml CONTENT between git commits, only mtime
  (ADV-RC18-1: no-op commit defeated mtime-only freshness)

This script is invoked by the bash helper in
`gate/lib/check_recurring_families.sh` via:

    python3 gate/lib/validate_recurring_families.py wellformed <yaml>
    python3 gate/lib/validate_recurring_families.py freshness <yaml> [<repo_root>]
    python3 gate/lib/validate_recurring_families.py parity <yaml> <md>

Exit code: 0 on pass, 1 on fail. Fail messages printed to stdout (so
the bash caller can iterate them via while-read).

Authority: ADR-0096 (rc19 comprehensive meta-recursion close).
"""

from __future__ import annotations

import datetime
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Iterable

REQUIRED_TOPKEYS = ["schema_version", "last_updated", "families"]
REQUIRED_FAMILY_FIELDS = [
    "title",
    "first_observed_rc",
    "last_observed_rc",
    "occurrences",
    "root_cause",
    "surfaces",
    "prevention_rules",
    "cleanup_status",
    "open_residual",
]
CLEANUP_STATUS_ENUM = {
    "closed",
    "structurally_addressed",
    "partial",
    "incomplete",
    "monitoring",
}
# Base signal paths (always trigger freshness on change). The auto-derive
# step (cf. derive_signal_paths) walks families[].surfaces[] dynamically
# and adds every disk-resolvable surface to this set — that closes
# ADV-RC18-3 (signal-set narrower than tracked surfaces) without the
# hard-coded SURFACE_PREFIX_BASES that rc19 Wave 1 introduced (those
# entries — `agent-`, `**/module-metadata.yaml`, etc. — were either
# invalid git pathspecs or duplicated by surface-derived entries; rc20
# Wave 3 / ADR-0097 removed them as dead placebo per review Finding F4).
BASE_SIGNAL_PATHS = [
    "docs/adr/",
    "docs/governance/architecture-status.yaml",
    "docs/logs/releases/",
    "docs/governance/rules/",
    "CLAUDE.md",
]


def fail(msg: str) -> None:
    print(f"FAIL: {msg}")


def load_yaml(path: str) -> dict | None:
    """Load yaml safely. Returns None on parse error (caller emits FAIL).

    rc19 Wave 1 strictness (close ADV-RC18 + Correctness Finding 1):
      - Catches ValueError too (pyyaml's date constructor raises ValueError
        for impossible dates like 2026-13-32, not YAMLError).
      - Uses a custom SafeLoader subclass that rejects duplicate keys at
        the same map level — closes the duplicate-field defense that
        pyyaml otherwise silently dedupes (last wins).
    """
    try:
        import yaml
        from yaml.constructor import ConstructorError
    except ImportError:
        fail(f"pyyaml not installed; cannot parse {path} (run: pip install pyyaml) -- ADR-0096 Wave 1 prerequisite")
        return None

    class StrictSafeLoader(yaml.SafeLoader):
        pass

    def _no_duplicate_keys(loader, node, deep=False):
        mapping = {}
        for key_node, value_node in node.value:
            key = loader.construct_object(key_node, deep=deep)
            if key in mapping:
                raise ConstructorError(
                    "while constructing a mapping",
                    node.start_mark,
                    f"duplicate key found: {key!r}",
                    key_node.start_mark,
                )
            value = loader.construct_object(value_node, deep=deep)
            mapping[key] = value
        return mapping

    StrictSafeLoader.add_constructor(
        yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG,
        _no_duplicate_keys,
    )

    try:
        with open(path, encoding="utf-8") as f:
            return yaml.load(f, Loader=StrictSafeLoader)
    except (yaml.YAMLError, ValueError) as e:
        fail(f"yaml parse error in {path}: {e}")
        return None
    except OSError as e:
        fail(f"cannot read {path}: {e}")
        return None


def cmd_wellformed(yaml_path: str) -> int:
    """Sub-check .a — yaml well-formedness (file + structure + enum + dates)."""
    if not os.path.isfile(yaml_path):
        fail(f"{yaml_path} missing -- Rule G-9.a / E156 (ADR-0096)")
        return 1

    data = load_yaml(yaml_path)
    if data is None:
        return 1  # load_yaml already emitted FAIL
    if not isinstance(data, dict):
        fail(f"{yaml_path} top-level must be a mapping, got {type(data).__name__} -- Rule G-9.a / E156")
        return 1

    failures = 0
    # Top-level keys present
    for key in REQUIRED_TOPKEYS:
        if key not in data:
            fail(f"{yaml_path} missing top-level key {key!r} -- Rule G-9.a / E156")
            failures += 1

    # last_updated format AND semantic validity (close ADV-RC18-2 + future-date)
    last_updated = data.get("last_updated")
    if last_updated is not None:
        # Coerce to string (yaml may parse YYYY-MM-DD as date object)
        last_str = str(last_updated)
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", last_str):
            fail(
                f"last_updated value {last_str!r} is not ISO YYYY-MM-DD format -- "
                f"Rule G-9.a / E156 (fix 1e)"
            )
            failures += 1
        else:
            try:
                last_date = datetime.date.fromisoformat(last_str)
            except ValueError:
                fail(
                    f"last_updated value {last_str!r} is syntactically ISO but not a real date "
                    f"(e.g. 2026-13-32) -- Rule G-9.a / E156 (Wave 1 semantic-date defense, "
                    f"ADV-RC18-2)"
                )
                failures += 1
            else:
                today = datetime.date.today()
                if last_date > today:
                    fail(
                        f"last_updated value {last_str} is future-dated (today is {today.isoformat()}) "
                        f"-- Rule G-9.a / E156 (Wave 1 chronological-sanity defense)"
                    )
                    failures += 1

    # families must be non-empty list (close empty-array defect)
    families = data.get("families", [])
    if not isinstance(families, list) or len(families) == 0:
        fail(
            f"families must be a non-empty list (got {type(families).__name__} of length "
            f"{len(families) if isinstance(families, list) else 'N/A'}) -- Rule G-9.a / E156 (fix 1b)"
        )
        return failures + 1

    # Per-family validation
    seen_ids: set[str] = set()
    for idx, fam in enumerate(families):
        if not isinstance(fam, dict):
            fail(f"family[{idx}] is not a mapping -- Rule G-9.a / E156")
            failures += 1
            continue
        fid = fam.get("id", f"<unknown-at-index-{idx}>")
        if fid in seen_ids:
            fail(f"family id {fid!r} declared more than once -- Rule G-9.a / E156 (fix 1d)")
            failures += 1
        seen_ids.add(fid)

        # All 9 required fields present
        for field in REQUIRED_FAMILY_FIELDS:
            if field not in fam:
                fail(
                    f"family {fid} missing required field {field!r} -- "
                    f"Rule G-9.a / E156 (fix 1d, ADR-0096 python parser)"
                )
                failures += 1

        # cleanup_status enum value
        status = fam.get("cleanup_status")
        if status is not None:
            status_str = str(status).strip()
            if status_str not in CLEANUP_STATUS_ENUM:
                fail(
                    f"family {fid} field cleanup_status value {status_str!r} not in enum "
                    f"{sorted(CLEANUP_STATUS_ENUM)} -- Rule G-9.a / E156 (fix 1c)"
                )
                failures += 1

    return 1 if failures > 0 else 0


def _git_run(args: list[str], cwd: str) -> str:
    """Run git with arguments; return stdout stripped. Returns '' on error.

    Force UTF-8 decoding (rc19 Wave 1 fix): subprocess.run with text=True uses
    the platform default encoding on Windows (GBK), which fails on git show
    output containing UTF-8 chars like em-dash `—`. Force utf-8 + replace
    errors so cross-platform parity holds.
    """
    try:
        result = subprocess.run(
            ["git", *args],
            cwd=cwd,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            check=False,
        )
        if result.returncode != 0:
            return ""
        return (result.stdout or "").strip()
    except OSError:
        return ""


def derive_signal_paths(yaml_data: dict, repo_root: str = ".") -> list[str]:
    """
    Derive signal paths from family surfaces (closes ADV-RC18-3).
    Returns sorted list of paths suitable for `git log -- <paths>`.

    rc20 Wave 3 / ADR-0097 hardening:
      - Sanitize each surface token: reject `../`, absolute paths, and
        colon-bearing tokens (e.g. `gate/x.sh:func`) which git silently
        accepts but never matches; warn so typos surface rather than
        becoming vacuous freshness passes (closes Finding F7).
      - Only emit paths that actually resolve on disk OR are explicit
        directory prefixes ending in `/` — keeps the set tight without
        re-introducing the hard-coded SURFACE_PREFIX_BASES placebos.

    rc32 hardening:
      - Glob tokens (`agent-*/ARCHITECTURE.md`, `docs/**/*.puml`,
        `.github/workflows/*.yml`, etc.) were previously WARN'd as
        "does not resolve on disk" then added to the path set unchanged.
        `git log -- <literal-glob-pattern>` does NOT expand `*` in
        pathspecs without `:(glob)` magic, so the surface was tracked
        on paper only. Result: 23 silent no-ops at rc31 HEAD.
      - Fix: tokens containing `*` are now expanded via
        `glob.glob(token, recursive=True)` at the python level. Each
        match becomes a concrete repo-relative path the freshness
        check actually scans. Empty expansion still WARNs.
    """
    import glob as _glob_mod
    paths = set(BASE_SIGNAL_PATHS)
    for fam in yaml_data.get("families", []):
        fid = fam.get("id", "<unknown>") if isinstance(fam, dict) else "<unknown>"
        for surface in fam.get("surfaces", []) if isinstance(fam, dict) else []:
            if not isinstance(surface, str):
                continue
            token = surface.split()[0] if surface.split() else ""
            base = token.split("#")[0]
            if not base or base.startswith(("`", "git ")):
                continue
            # Reject path-traversal + absolute paths (git pathspecs are repo-relative)
            if base.startswith(("/", "../")) or "/../" in base:
                print(
                    f"WARN: family {fid} surface {surface!r} rejected — "
                    f"not a repo-relative path (rc20 Wave 3 sanitizer)",
                    file=sys.stderr,
                )
                continue
            # Reject colon-bearing tokens (git accepts but never matches)
            if ":" in base:
                print(
                    f"WARN: family {fid} surface {surface!r} rejected — "
                    f"colon-bearing tokens are not valid git pathspecs (rc20 Wave 3 sanitizer)",
                    file=sys.stderr,
                )
                continue
            # rc32: expand glob tokens before disk-existence check.
            # `git log -- <literal-glob>` does NOT match without :(glob) magic,
            # so we resolve at the python level into concrete paths.
            if "*" in base or "?" in base or "{" in base:
                # Brace expansion isn't supported by python glob; strip simple
                # `{a,b,c}` alternation by trying each alternative if present.
                # For the common cases in this corpus (e.g. `**/*.{yaml,yml,tpl,md}`)
                # we split on the brace group and expand each alternative.
                expansions: list[str] = []
                brace_match = re.search(r"\{([^{}]+)\}", base)
                if brace_match:
                    alts = [a.strip() for a in brace_match.group(1).split(",")]
                    for alt in alts:
                        expansions.append(
                            base[: brace_match.start()] + alt + base[brace_match.end() :]
                        )
                else:
                    expansions = [base]
                matched_any = False
                for pattern in expansions:
                    # Glob from repo_root so the resulting paths are repo-relative.
                    abs_pattern = os.path.join(repo_root, pattern)
                    matches = _glob_mod.glob(abs_pattern, recursive=True)
                    for m in matches:
                        rel = os.path.relpath(m, repo_root).replace(os.sep, "/")
                        if rel and not rel.startswith(".."):
                            paths.add(rel)
                            matched_any = True
                if not matched_any:
                    print(
                        f"WARN: family {fid} surface {surface!r} (token={base!r}) "
                        f"glob expansion matched no files under {repo_root!r}; "
                        f"freshness will not track this surface (rc32 glob expander; "
                        f"likely typo or stale path)",
                        file=sys.stderr,
                    )
                continue
            base = base.replace("**/*", "").replace("**/", "")
            if not base:
                continue
            # Warn (but accept) if the surface doesn't resolve on disk; gives
            # authors a fast typo-feedback signal instead of vacuous freshness.
            abs_check = os.path.join(repo_root, base.rstrip("/"))
            if not (os.path.exists(abs_check) or base.endswith("/")):
                print(
                    f"WARN: family {fid} surface {surface!r} (token={base!r}) "
                    f"does not resolve on disk under {repo_root!r}; "
                    f"freshness will silently match nothing (rc20 Wave 3 sanitizer; "
                    f"likely typo or stale path)",
                    file=sys.stderr,
                )
            paths.add(base)
    return sorted(p for p in paths if p)


def cmd_freshness(yaml_path: str, repo_root: str = ".") -> int:
    """
    Sub-check .b — freshness via CONTENT diff (closes ADV-RC18-1 no-op
    commit attack).

    Algorithm:
      1. Shallow-clone fail-closed (fix 1h)
      2. Load yaml to derive signal paths from families[].surfaces[]
      3. Get most-recent commit SHA that touched any signal path
      4. Compare yaml CONTENT at that commit's parent vs current yaml.
         If equal: yaml wasn't actually updated to reflect the refresh-
         signal (no-op commit, whitespace bump, etc.) → FAIL.
      5. If signal commit IS the yaml-touching commit itself, compare
         to yaml content at the parent of that commit.
    """
    if not os.path.isfile(yaml_path):
        return 0  # subsumed by .a

    # Confirm we're in a git repo (single rev-parse call; rc20 Wave 3 / ADR-0097
    # consolidates the duplicate rev-parse from rc19 per review Finding F13).
    git_dir = _git_run(["rev-parse", "--git-dir"], repo_root)
    if not git_dir:
        return 0  # no git, can't check; tolerate

    # Fix 1h: shallow-clone fail-closed (close ADV-RC18-5 — handle empty
    # output from older git too).
    is_shallow_out = _git_run(["rev-parse", "--is-shallow-repository"], repo_root)
    if is_shallow_out == "true":
        fail(
            "cannot evaluate freshness on shallow clone "
            "(run git fetch --unshallow in CI; CI workflows must set actions/checkout fetch-depth: 0) "
            "-- Rule G-9.b / E157 (fix 1h)"
        )
        return 1
    # Also handle empty output (older git versions) by checking .git/shallow marker
    if not is_shallow_out:
        shallow_marker = os.path.join(repo_root, git_dir, "shallow")
        if os.path.isfile(shallow_marker):
            fail(
                "cannot evaluate freshness on shallow clone (detected via .git/shallow marker "
                "after empty --is-shallow-repository output; older git) "
                "-- Rule G-9.b / E157 (Wave 1 ADV-RC18-5 close)"
            )
            return 1

    data = load_yaml(yaml_path)
    if data is None:
        return 0  # subsumed by .a

    signal_paths = derive_signal_paths(data, repo_root)
    # Most-recent commit SHA touching any signal path
    signal_sha = _git_run(
        ["log", "-1", "--format=%H", "--", *signal_paths], repo_root
    )
    if not signal_sha:
        return 0  # no signal commits (impossible in a real repo, defensive)

    # Yaml's path relative to repo_root (git expects relative paths)
    yaml_rel = os.path.relpath(yaml_path, repo_root).replace(os.sep, "/")

    # Try to get yaml content at signal_sha's first parent (state BEFORE signal landed).
    # `^1` (first-parent) is explicit per rc20 Wave 1 / ADR-0097 — defends against
    # ADV-RC19-merge-ambiguity (a future non-squash merge with N parents would have
    # `^` resolve to first parent silently; making it explicit + documenting the
    # squash-merge expectation closes the latent freshness false-pass/fail).
    parent_content = _git_run(["show", f"{signal_sha}^1:{yaml_rel}"], repo_root)
    # If parent commit doesn't exist (initial commit) or path didn't exist, use signal_sha itself
    if not parent_content:
        parent_content = _git_run(["show", f"{signal_sha}:{yaml_rel}"], repo_root)

    # Current yaml content
    try:
        with open(yaml_path, encoding="utf-8") as f:
            current_content = f.read()
    except OSError as e:
        fail(f"cannot read {yaml_path}: {e} -- Rule G-9.b / E157")
        return 1

    # Normalize whitespace for comparison (closes "trailing newline bump" attack)
    def normalize(s: str) -> str:
        return "\n".join(line.rstrip() for line in s.splitlines() if line.strip())

    parent_norm = normalize(parent_content)
    current_norm = normalize(current_content)

    if parent_norm == current_norm and parent_content:
        fail(
            f"yaml content unchanged since refresh-signal commit {signal_sha[:8]} "
            f"(signal touched {', '.join(signal_paths[:3])}{'...' if len(signal_paths) > 3 else ''}); "
            f"no-op edits like trailing-newline bumps do not satisfy freshness "
            f"-- Rule G-9.b / E157 (Wave 1 content-diff defense, ADV-RC18-1)"
        )
        return 1
    return 0


def cmd_parity(yaml_path: str, md_path: str) -> int:
    """
    Sub-check .c — yaml/md family-id parity AND cleanup_status text parity.

    Uses pyyaml on yaml side (no regex assumption about id chars) and
    a wider md regex `^### F-[A-Za-z0-9_-]+` to mirror the yaml's
    accepted character set (closes Correctness Finding 2 — asymmetric
    anchoring).

    rc20 Wave 1 / ADR-0097: also compares `cleanup_status` value per
    family-id between yaml and the §0 family table in md (closes
    Finding F2/F3 — F-recursive-prevention-irony status drift between
    yaml `closed` and md `structurally addressed` slipped through
    id-only parity).
    """
    if not os.path.isfile(yaml_path) or not os.path.isfile(md_path):
        return 0

    data = load_yaml(yaml_path)
    if data is None:
        return 0  # subsumed by .a

    yaml_ids: set[str] = set()
    yaml_id_to_status: dict[str, str] = {}
    for fam in data.get("families", []):
        if isinstance(fam, dict):
            fid = fam.get("id")
            status = fam.get("cleanup_status")
            if isinstance(fid, str):
                yaml_ids.add(fid)
                if isinstance(status, str):
                    yaml_id_to_status[fid] = status

    md_ids: set[str] = set()
    md_heading_re = re.compile(r"^### (F-[A-Za-z0-9_-]+)", re.MULTILINE)
    try:
        with open(md_path, encoding="utf-8") as f:
            md_content = f.read()
    except OSError as e:
        fail(f"cannot read {md_path}: {e} -- Rule G-9.c / E158")
        return 1

    for m in md_heading_re.finditer(md_content):
        md_ids.add(m.group(1))

    # Parse status from md §0 table rows. Table format:
    #   | N | F-<id> | <title> | <occurrences> | <status-symbol-prefix> <status-text> ... |
    # We extract the canonical enum token from the last cell.
    md_id_to_status_text: dict[str, str] = {}
    md_table_row_re = re.compile(
        r"^\|\s*\d+\s*\|\s*(F-[A-Za-z0-9_-]+)\s*\|[^|]*\|[^|]*\|\s*[^|]*\|",
        re.MULTILINE,
    )
    for m in md_table_row_re.finditer(md_content):
        fid = m.group(1)
        # Get the row's full text (m.group(0) ends mid-cell — get full line)
        line_start = md_content.rfind("\n", 0, m.start()) + 1
        line_end = md_content.find("\n", m.start())
        if line_end == -1:
            line_end = len(md_content)
        row = md_content[line_start:line_end]
        # Last cell content (between the 5th `|` and the trailing `|`)
        cells = [c.strip() for c in row.split("|")]
        if len(cells) >= 6:
            md_id_to_status_text[fid] = cells[5]

    only_yaml = yaml_ids - md_ids
    only_md = md_ids - yaml_ids
    failures = 0
    if only_yaml:
        fail(
            f"family ids in yaml but missing from md ^### F- headings: "
            f"{' '.join(sorted(only_yaml))} -- Rule G-9.c / E158"
        )
        failures += 1
    if only_md:
        fail(
            f"family ids in md ^### F- headings but missing from yaml: "
            f"{' '.join(sorted(only_md))} -- Rule G-9.c / E158"
        )
        failures += 1

    # Status-text parity per id (rc20 Wave 1 / ADR-0097).
    # Anchor to the LEADING status token (after optional emoji prefix) so that
    # `closed by adding [META]` inside the prose tail does not mis-bind to the
    # `closed` enum. Cell shape: `<emoji>? <enum-token-words> (optional prose)`.
    # Skip leading non-alpha chars (emojis, variant selectors, whitespace,
    # symbol punctuation) before the enum word. Picks up `⚠️ partial`,
    # `🟡 monitoring`, `✅ closed`, plain `partial`, etc.
    md_anchor_re = re.compile(
        r"^[^A-Za-z]*"
        r"(closed|structurally[ _]addressed|monitoring|partial|incomplete)\b",
        re.IGNORECASE,
    )
    normalise_token = {
        "closed": "closed",
        "structurally addressed": "structurally_addressed",
        "structurally_addressed": "structurally_addressed",
        "monitoring": "monitoring",
        "partial": "partial",
        "incomplete": "incomplete",
    }
    for fid in sorted(yaml_ids & md_ids):
        yaml_status = yaml_id_to_status.get(fid)
        md_cell = md_id_to_status_text.get(fid)
        # Skip status-text parity for ids whose yaml entry has no cleanup_status
        # field OR whose md side has no §0 table row — those omissions are
        # subsumed by sub-check .a (yaml well-formedness) and md format checks.
        if yaml_status is None or md_cell is None:
            continue
        m_anchor = md_anchor_re.match(md_cell)
        if not m_anchor:
            fail(
                f"family {fid}: md §0 table status cell has no leading "
                f"cleanup_status token (cell={md_cell!r}); expected `<emoji>? "
                f"<closed|structurally addressed|monitoring|partial|incomplete> ...` "
                f"-- Rule G-9.c / E158"
            )
            failures += 1
            continue
        md_enum_found = normalise_token[m_anchor.group(1).lower()]
        if md_enum_found != yaml_status:
            fail(
                f"family {fid}: cleanup_status drift — yaml={yaml_status!r} "
                f"md={md_enum_found!r} (md cell leading token from: {md_cell!r}) "
                f"-- Rule G-9.c / E158"
            )
            failures += 1

    return 1 if failures > 0 else 0


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(f"Usage: {argv[0]} <wellformed|freshness|parity> <args...>", file=sys.stderr)
        return 2
    cmd = argv[1]
    args = argv[2:]
    if cmd == "wellformed":
        if len(args) != 1:
            print("Usage: wellformed <yaml-path>", file=sys.stderr)
            return 2
        return cmd_wellformed(args[0])
    if cmd == "freshness":
        if len(args) not in (1, 2):
            print("Usage: freshness <yaml-path> [<repo-root>]", file=sys.stderr)
            return 2
        return cmd_freshness(args[0], args[1] if len(args) == 2 else ".")
    if cmd == "parity":
        if len(args) != 2:
            print("Usage: parity <yaml-path> <md-path>", file=sys.stderr)
            return 2
        return cmd_parity(args[0], args[1])
    print(f"Unknown command: {cmd}", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))

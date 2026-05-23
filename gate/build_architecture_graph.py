#!/usr/bin/env python3
"""
Build docs/governance/architecture-graph.yaml from authoritative inputs.

This is the implementation of CLAUDE.md Rule 34 / ADR-0068. The output is the
single machine-readable join of all architectural relationships an LLM needs
to traverse: principle → rule → enforcer → test → artefact.

Inputs (read-only — none of these are hand-edited by this script):
    - docs/governance/principle-coverage.yaml      (principle → rule)
    - docs/governance/enforcers.yaml               (rule → enforcer, enforcer → test/artefact)
    - docs/governance/architecture-status.yaml     (capability → test)
    - <module>/module-metadata.yaml                (module → module allowed/forbidden)
    - docs/adr/*.yaml                              (adr → adr supersedes/extends/relates_to)
    - ARCHITECTURE.md + agent-*/ARCHITECTURE.md + docs/L2/*.md  (level/view front-matter)

Output:
    - docs/governance/architecture-graph.yaml      (canonical graph)
    - docs/governance/architecture-graph.mmd       (Mermaid renderer, optional)

The script is idempotent: re-running on unchanged inputs produces a
byte-identical output (sorted keys, deterministic order). Gate Rule 38
verifies idempotency in CI.

Authority:
    - CLAUDE.md Rule 34
    - ADR-0068 (Layered 4+1 + Architecture Graph as Twin Sources of Truth)
    - Gate Rule 38 (architecture_graph_well_formed)
    - Gate Rule 40 (enforcer_reachable_from_principle)
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any

try:
    import yaml  # PyYAML
except ImportError:
    print("ERROR: PyYAML not installed. `pip install pyyaml`", file=sys.stderr)
    sys.exit(2)

REPO = Path(__file__).resolve().parent.parent
GOV = REPO / "docs" / "governance"
ADR_DIR = REPO / "docs" / "adr"
L2_DIR = REPO / "docs" / "L2"
OUTPUT_YAML = GOV / "architecture-graph.yaml"
OUTPUT_MMD = GOV / "architecture-graph.mmd"

FRONT_MATTER_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n", re.DOTALL)


# ---------------------------------------------------------------------------
# Loaders
# ---------------------------------------------------------------------------


def load_yaml(path: Path) -> Any:
    if not path.is_file():
        return None
    with path.open("r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def parse_front_matter(md_path: Path) -> dict | None:
    if not md_path.is_file():
        return None
    text = md_path.read_text(encoding="utf-8")
    m = FRONT_MATTER_RE.match(text)
    if not m:
        return None
    try:
        return yaml.safe_load(m.group(1)) or {}
    except yaml.YAMLError:
        return None


def discover_modules(repo: Path) -> list[Path]:
    return sorted(repo.glob("*/module-metadata.yaml"))


def discover_adrs(adr_dir: Path) -> list[Path]:
    return sorted(adr_dir.glob("*.yaml"))


def discover_l2_docs(l2_dir: Path) -> list[Path]:
    if not l2_dir.is_dir():
        return []
    return sorted(p for p in l2_dir.rglob("*.md") if p.name != "README.md")


# ---------------------------------------------------------------------------
# Anchor resolution (CLAUDE.md Rule 34, Phase M B1)
# ---------------------------------------------------------------------------


def _resolve_anchor(file_path: Path, anchor: str) -> bool:
    """Return True if ``anchor`` names a real method / heading / function / YAML key inside ``file_path``.

    Closes the Phase-M P0-β gap: prior validation only checked that the FILE
    existed, so enforcer rows pointing at e.g. ``RunHttpContractIT.java#createReturnsPending``
    silently passed even though the method does not exist. Per file extension:

    - ``.java`` : regex finds a method declaration with the anchor as the name.
    - ``.md``   : regex finds a heading whose text contains the anchor token,
                  OR a generic word-boundary match anywhere in the file.
    - ``.sh``   : regex finds a function definition ``anchor()`` OR a
                  Rule-section comment ``# Rule N — anchor`` OR a literal
                  case-label match used by the gate script.
    - ``.yaml`` : the file is parsed; top-level keys or list-row ``id:`` values
                  containing the anchor count as a hit.
    - other     : conservatively returns True (don't fail on unknown ext).
    """
    if not file_path.is_file() or not anchor:
        return False
    try:
        text = file_path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return False
    suffix = file_path.suffix.lower()
    a_re = re.escape(anchor)
    if suffix == ".java":
        # Match a method DECLARATION (not a Javadoc or comment reference).
        # Walk line-by-line, skip lines that start with `*` or `//`, then look
        # for `<return-type-token>` + `<anchor>` + `(`.
        for line in text.splitlines():
            stripped = line.lstrip()
            if not stripped or stripped.startswith(("*", "//", "/*")):
                continue
            if re.search(r"\b(?:void|[\w<>?,\s\[\]]+?)\s+" + a_re + r"\s*\(", line):
                return True
        return False
    if suffix == ".md":
        if re.search(r"^#{1,6}\s+.*" + a_re, text, re.MULTILINE):
            return True
        return bool(re.search(r"\b" + a_re + r"\b", text))
    if suffix == ".sh":
        if re.search(r"^" + a_re + r"\s*\(\s*\)", text, re.MULTILINE):
            return True
        if re.search(r"^#\s*Rule\s+\d+[a-z]?\s+[—\-]+\s+" + a_re, text, re.MULTILINE):
            return True
        # gate-script style: the rule name appears literally in a fail_rule call or as a heading comment
        return bool(re.search(r"\b" + a_re + r"\b", text))
    if suffix == ".yaml":
        try:
            data = yaml.safe_load(text)
        except yaml.YAMLError:
            return False
        if isinstance(data, dict):
            if anchor in data:
                return True
        if isinstance(data, list):
            for row in data:
                if isinstance(row, dict) and row.get("id") == anchor:
                    return True
        return bool(re.search(r"\b" + a_re + r"\b", text))
    if suffix == ".sql":
        return bool(re.search(r"\b" + a_re + r"\b", text, re.IGNORECASE))
    return True


# ---------------------------------------------------------------------------
# SPI discovery (CLAUDE.md Rule 32 + Rule 34, Phase M E1)
# ---------------------------------------------------------------------------


def _spi_interfaces(module_dir: Path, pkg: str) -> list[Path]:
    """Return Java source files under <module_dir>/src/main/java/<pkg path>/."""
    pkg_path = module_dir / "src" / "main" / "java" / Path(*pkg.split("."))
    if not pkg_path.is_dir():
        return []
    return sorted(pkg_path.glob("*.java"))


# ---------------------------------------------------------------------------
# Graph assembly
# ---------------------------------------------------------------------------


def build_graph(repo: Path) -> dict:
    nodes: dict[str, dict] = {}
    edges: list[dict] = []

    def add_node(node_id: str, ntype: str, **attrs: Any) -> None:
        if node_id in nodes:
            nodes[node_id].update(attrs)
            return
        nodes[node_id] = {"id": node_id, "type": ntype, **attrs}

    def add_edge(src: str, dst: str, etype: str, **attrs: Any) -> None:
        edges.append({"src": src, "dst": dst, "type": etype, **attrs})

    # -- 1. Principles + principle→rule edges --
    pc = load_yaml(GOV / "principle-coverage.yaml") or {}
    for p in pc.get("principles", []):
        pid = p["id"]
        add_node(pid, "principle", name=p.get("name", ""), motivation=p.get("motivation", "").strip())
        for rule in p.get("operationalised_by_rules", []):
            add_node(rule, "rule")
            add_edge(pid, rule, "operationalised_by")
        for rule in p.get("deferred_operationalisers", []):
            add_node(rule, "rule", deferred=True)
            add_edge(pid, rule, "deferred_operationaliser")
        # Phase 7 audit fix (Rule 34 closure): cross_cutting_invariant is a
        # principle → rule edge of distinct semantics. Without this loop the
        # graph dropped P-M → Rule-48 silently (Rule-48 became an orphan node).
        cci = p.get("cross_cutting_invariant")
        if cci:
            cci_rule = cci.get("rule") if isinstance(cci, dict) else cci
            if cci_rule:
                add_node(cci_rule, "rule")
                add_edge(pid, cci_rule, "cross_cutting_invariant")
    for legacy in pc.get("legacy_principles", []):
        lid = legacy["id"]
        add_node(lid, "legacy_principle", name=legacy.get("name", ""))
        composes_with = legacy.get("composes_with")
        if composes_with:
            add_node(composes_with, "principle")
            add_edge(lid, composes_with, "composes_with")
        for rule in legacy.get("operationalised_by_rules", []):
            add_node(rule, "rule")
            add_edge(lid, rule, "operationalised_by")
        for rule in legacy.get("deferred_operationalisers", []):
            add_node(rule, "rule", deferred=True)
            add_edge(lid, rule, "deferred_operationaliser")

    # -- 2. Enforcers + rule→enforcer + enforcer→artefact edges --
    enforcers = load_yaml(GOV / "enforcers.yaml") or []
    # Match both integer (Rule 28) and namespaced (Rule D-1, R-C.a, G-3.f, M-2.b) refs.
    rule_extract = re.compile(r"Rule\s+([A-Z]-[A-Z0-9]+(?:\.[a-z])?|\d+[a-z]?)", re.IGNORECASE)
    for row in enforcers:
        eid = row["id"]
        add_node(
            eid,
            "enforcer",
            kind=row.get("kind", "unknown"),
            level=row.get("level", "unknown"),
            view=row.get("view", "unknown"),
            asserts=row.get("asserts", ""),
        )
        # Heuristic: extract Rule-N tokens from constraint_ref
        constraint_ref = row.get("constraint_ref", "")
        for n in rule_extract.findall(constraint_ref):
            rule_id = f"Rule-{n}"
            add_node(rule_id, "rule")
            add_edge(rule_id, eid, "enforced_by")

        artifact = row.get("artifact", "")
        if artifact:
            file_part, _, anchor = artifact.partition("#")
            file_id = f"file:{file_part}"
            file_path = repo / file_part
            file_exists = file_path.exists()
            anchor_ok: bool | None = None
            if anchor:
                anchor_ok = _resolve_anchor(file_path, anchor) if file_exists else False
            add_node(
                file_id,
                "artefact",
                path=file_part,
                exists=file_exists,
                anchor=anchor or None,
                anchor_resolves=anchor_ok,
            )
            add_edge(eid, file_id, "asserts_in", anchor=anchor or None, anchor_resolves=anchor_ok)

    # -- 3. ADR nodes + adr→adr edges --
    for adr_path in discover_adrs(ADR_DIR):
        data = load_yaml(adr_path)
        if not isinstance(data, dict) or not data.get("id", "").startswith("ADR-"):
            continue
        aid = data["id"]
        add_node(
            aid,
            "adr",
            title=data.get("title", ""),
            status=data.get("status", "unknown"),
            level=data.get("level", "unknown"),
            view=data.get("view", "unknown"),
            file=str(adr_path.relative_to(repo)).replace("\\", "/"),
        )
        for target in data.get("supersedes", []) or []:
            add_node(target, "adr")
            add_edge(aid, target, "supersedes")
        for target in data.get("supersedes_partial", []) or []:
            add_node(target, "adr")
            add_edge(aid, target, "supersedes_partial")
        for target in data.get("extends", []) or []:
            add_node(target, "adr")
            add_edge(aid, target, "extends")
        for target in data.get("relates_to", []) or []:
            add_node(target, "adr")
            add_edge(aid, target, "relates_to")

    # -- 4. Modules + module→module edges + module→spi→interface edges (Phase M E1) --
    for mm_path in discover_modules(repo):
        data = load_yaml(mm_path)
        if not isinstance(data, dict):
            continue
        module_dir = mm_path.parent
        mid = f"module:{data.get('module', module_dir.name)}"
        spi_packages = data.get("spi_packages", []) or []
        add_node(
            mid,
            "module",
            kind=data.get("kind", "unknown"),
            version=data.get("version", "unknown"),
            spi_packages=spi_packages,
            dfx_doc=data.get("dfx_doc"),
        )
        for allowed in data.get("allowed_dependencies", []) or []:
            tgt = f"module:{allowed}"
            add_node(tgt, "module")
            add_edge(mid, tgt, "may_depend_on")
        for forbidden in data.get("forbidden_dependencies", []) or []:
            tgt = f"module:{forbidden}"
            add_node(tgt, "module")
            add_edge(mid, tgt, "must_not_depend_on")
        # SPI first-class nodes (Phase M E1) — gives Rule 32 / P-D a traversable path
        for pkg in spi_packages:
            spi_id = f"spi:{pkg}"
            add_node(spi_id, "spi_package", package=pkg, level="L1", view="development", module=mid)
            add_edge(mid, spi_id, "exposes_spi")
            for iface_path in _spi_interfaces(module_dir, pkg):
                rel = str(iface_path.relative_to(repo)).replace("\\", "/")
                file_id = f"file:{rel}"
                add_node(file_id, "artefact", path=rel, exists=True, anchor=None, anchor_resolves=None)
                add_edge(spi_id, file_id, "contains")

    # -- 5. Capability nodes + capability→test edges (architecture-status) --
    arch_status = load_yaml(GOV / "architecture-status.yaml") or {}
    capabilities = arch_status if isinstance(arch_status, dict) else {}
    # Architecture-status is a flat dict {capability_key: {...}}; skip non-dict header keys.
    skip = {"version", "generated_at", "repository_counts", "schema_version"}
    for key, val in capabilities.items():
        if key in skip or not isinstance(val, dict):
            continue
        cid = f"capability:{key}"
        add_node(
            cid,
            "capability",
            status=val.get("status", "unknown"),
            shipped=bool(val.get("shipped", False)),
            allowed_claim=val.get("allowed_claim", "").strip()[:160],
        )
        for impl in val.get("implementation", []) or []:
            file_id = f"file:{impl}"
            file_path = repo / impl
            add_node(file_id, "artefact", path=impl, exists=file_path.exists(), anchor=None)
            add_edge(cid, file_id, "implemented_by")
        for test in val.get("tests", []) or []:
            tfile, _, tanchor = test.partition("#")
            file_id = f"file:{tfile}"
            file_path = repo / tfile
            t_exists = file_path.exists()
            t_anchor_ok: bool | None = None
            if tanchor:
                t_anchor_ok = _resolve_anchor(file_path, tanchor) if t_exists else False
            add_node(
                file_id,
                "artefact",
                path=tfile,
                exists=t_exists,
                anchor=tanchor or None,
                anchor_resolves=t_anchor_ok,
            )
            add_edge(cid, file_id, "tested_by", anchor=tanchor or None, anchor_resolves=t_anchor_ok)

    # -- 6. (level, view) → artefact edges from front-matter --
    # `sorted()` around the glob is required for Rule 38 idempotency: filesystem
    # iteration order differs between NTFS (Windows) and ext4 (Linux/WSL), so the
    # graph would otherwise be order-sensitive and fail re-run byte-equality.
    for md_path in [repo / "ARCHITECTURE.md", *sorted(repo.glob("agent-*/ARCHITECTURE.md")), *discover_l2_docs(L2_DIR)]:
        fm = parse_front_matter(md_path)
        if not fm:
            continue
        rel = str(md_path.relative_to(repo)).replace("\\", "/")
        file_id = f"file:{rel}"
        add_node(file_id, "artefact", path=rel, exists=True, anchor=None)
        level = fm.get("level")
        view = fm.get("view")
        if level:
            lv_id = f"level:{level}"
            add_node(lv_id, "level")
            add_edge(lv_id, file_id, "indexes")
        if view:
            vw_id = f"view:{view}"
            add_node(vw_id, "view")
            add_edge(vw_id, file_id, "indexes")

    # ADRs are .yaml not .md but their level/view are already in the ADR loader above; emit
    # (level, view) → adr edges for graph traversal symmetry.
    for n in list(nodes.values()):
        if n["type"] == "adr":
            level = n.get("level")
            view = n.get("view")
            if level and level != "unknown":
                add_node(f"level:{level}", "level")
                add_edge(f"level:{level}", n["id"], "indexes")
            if view and view != "unknown":
                add_node(f"view:{view}", "view")
                add_edge(f"view:{view}", n["id"], "indexes")

    # Sort everything for byte-identical idempotency.
    sorted_nodes = [nodes[k] for k in sorted(nodes.keys())]
    sorted_edges = sorted(edges, key=lambda e: (e["src"], e["dst"], e["type"]))

    return {
        "version": 1,
        "schema": "architecture-graph/v1",
        "authority": "ADR-0068 (Layered 4+1 + Architecture Graph); CLAUDE.md Rule 34",
        "generator": "gate/build_architecture_graph.py",
        "node_count": len(sorted_nodes),
        "edge_count": len(sorted_edges),
        "nodes": sorted_nodes,
        "edges": sorted_edges,
    }


# ---------------------------------------------------------------------------
# Validation (graph-level checks executed by Gate Rule 38)
# ---------------------------------------------------------------------------


def validate(graph: dict) -> list[str]:
    errors: list[str] = []
    nodes_by_id = {n["id"]: n for n in graph["nodes"]}

    # E1. Every edge endpoint resolves.
    for e in graph["edges"]:
        if e["src"] not in nodes_by_id:
            errors.append(f"edge src missing: {e['src']} -> {e['dst']} ({e['type']})")
        if e["dst"] not in nodes_by_id:
            errors.append(f"edge dst missing: {e['src']} -> {e['dst']} ({e['type']})")

    # E2. Every artefact node with exists=False is a defect (the file should exist).
    for n in graph["nodes"]:
        if n["type"] == "artefact" and n.get("exists") is False:
            errors.append(f"artefact path does not exist: {n['path']} (node {n['id']})")

    # E2b. Every artefact carrying an `anchor` MUST have anchor_resolves=True (Phase M B1).
    # This closes the gap the L1 expert review flagged (P0-2 / P2-1): an enforcer
    # row that names a non-existent method/heading/key now fails Rule 38 directly.
    for n in graph["nodes"]:
        if n["type"] != "artefact":
            continue
        anchor = n.get("anchor")
        resolves = n.get("anchor_resolves")
        if anchor and resolves is False:
            errors.append(
                f"artefact anchor does not resolve: {n['path']}#{anchor} "
                f"(node {n['id']}) — fix the enforcer row's #anchor or rename the target method/heading"
            )

    # E3. DAG-ness on supersedes/extends.
    def has_cycle(edge_type: str) -> list[str]:
        adj = defaultdict(list)
        for e in graph["edges"]:
            if e["type"] == edge_type:
                adj[e["src"]].append(e["dst"])
        visited: set[str] = set()
        stack: set[str] = set()
        cycle: list[str] = []

        def dfs(u: str) -> bool:
            if u in stack:
                cycle.append(u)
                return True
            if u in visited:
                return False
            visited.add(u)
            stack.add(u)
            for v in adj.get(u, []):
                if dfs(v):
                    cycle.append(u)
                    return True
            stack.discard(u)
            return False

        for n in list(adj.keys()):
            if dfs(n):
                return list(reversed(cycle))
        return []

    for et in ("supersedes", "extends"):
        cycle = has_cycle(et)
        if cycle:
            errors.append(f"cycle in {et} sub-graph: {' -> '.join(cycle)}")

    return errors


# ---------------------------------------------------------------------------
# Mermaid renderer (one-shot, optional)
# ---------------------------------------------------------------------------


def render_mermaid(graph: dict, max_nodes: int = 60) -> str:
    lines = ["```mermaid", "flowchart LR"]
    type_shape = {
        "principle": '("**{}**\\n[principle]")',
        "rule": '["{}"]',
        "enforcer": '(["{}"])',
        "artefact": '[/"{}"/]',
        "adr": '["{}"]',
        "module": '["{}"]',
        "capability": '(["{}"])',
        "level": '(("{}"))',
        "view": '(("{}"))',
    }
    counted = 0
    for n in graph["nodes"]:
        if counted >= max_nodes:
            lines.append(f"  more[\"... +{graph['node_count'] - max_nodes} more nodes\"]")
            break
        shape_tpl = type_shape.get(n["type"], '["{}"]')
        label = n["id"]
        if n["type"] == "principle":
            label = f"{n['id']}<br/>{n.get('name', '')[:24]}"
        elif n["type"] == "adr":
            label = f"{n['id']}<br/>{n.get('title', '')[:30]}"
        safe_id = re.sub(r"[^A-Za-z0-9_]", "_", n["id"])
        lines.append(f"  {safe_id}{shape_tpl.format(label)}")
        counted += 1
    for e in graph["edges"][: max_nodes * 4]:
        sid = re.sub(r"[^A-Za-z0-9_]", "_", e["src"])
        did = re.sub(r"[^A-Za-z0-9_]", "_", e["dst"])
        arrow = {"may_depend_on": "-->", "must_not_depend_on": "-.x", "supersedes": "==>"}.get(e["type"], "-->")
        lines.append(f"  {sid} {arrow}|{e['type']}| {did}")
    lines.append("```")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--check", action="store_true", help="build and validate; emit non-zero on validation failure")
    p.add_argument("--no-write", action="store_true", help="do not write the output file (use with --check)")
    p.add_argument("--mermaid", action="store_true", help="also emit Mermaid renderer to docs/governance/architecture-graph.mmd")
    args = p.parse_args()

    graph = build_graph(REPO)
    yaml_text = yaml.safe_dump(graph, sort_keys=False, allow_unicode=True, width=1000)

    # Rule 34 normative header: this file is generated, not hand-edited.
    # Listed in plan D:/.claude/plans/spi-atomic-willow.md as fix D2.
    header = (
        "# DO NOT HAND-EDIT -- generated by gate/build_architecture_graph.py per\n"
        "# CLAUDE.md Rule 34 / ADR-0068. Re-run `bash gate/build_architecture_graph.sh`\n"
        "# (or `python gate/build_architecture_graph.py`) to regenerate.\n"
        "#\n"
        "# Inputs (any change here MUST be followed by a regen + commit):\n"
        "#   docs/governance/principle-coverage.yaml\n"
        "#   docs/governance/enforcers.yaml\n"
        "#   docs/governance/architecture-status.yaml\n"
        "#   docs/adr/*.yaml\n"
        "#   */module-metadata.yaml\n"
        "#   ARCHITECTURE.md + agent-*/ARCHITECTURE.md + docs/L2/**/*.md (front-matter)\n"
        "#\n"
        "# Gate Rule 38 (architecture_graph_well_formed) verifies graph well-formedness;\n"
        "# Gate Rule 40 (enforcer_reachable_from_principle) verifies every principle\n"
        "# -> rule -> enforcer -> artefact chain closes.\n"
        "\n"
    )

    if not args.no_write:
        # Force LF line endings regardless of platform (Win-native Python would
        # otherwise translate \n -> \r\n in text mode, breaking Rule 42
        # cross-platform idempotency when the same input is built on Git Bash
        # for Windows vs WSL/Linux).
        OUTPUT_YAML.write_bytes((header + yaml_text).encode("utf-8"))
        print(f"Wrote {OUTPUT_YAML.relative_to(REPO)}: {graph['node_count']} nodes, {graph['edge_count']} edges")
    else:
        print(f"Built graph (not written): {graph['node_count']} nodes, {graph['edge_count']} edges")

    if args.mermaid:
        mmd_text = render_mermaid(graph)
        OUTPUT_MMD.write_bytes(mmd_text.encode("utf-8"))
        print(f"Wrote {OUTPUT_MMD.relative_to(REPO)}")

    errors = validate(graph)
    if errors:
        print("\nVALIDATION ERRORS:", file=sys.stderr)
        for err in errors:
            print(f"  - {err}", file=sys.stderr)
        if args.check:
            return 1
    else:
        print("Graph validation: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())

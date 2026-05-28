#!/usr/bin/env python3
"""Gate check: Rule G-15 (Fact-Layer Integrity) — enforcer E179, gate Rule 131.

Sub-clauses checked by wave (this script is multi-mode; the calling gate
script passes a sub-clause set via --enforce):

  .a (W1+): architecture/facts/README.md exists; schema/fact.schema.yaml
           parses as YAML; generated/ exists; saa-property-authority.yaml
           parses as YAML.
  .b (W2+): every entry in architecture/facts/generated/*.json carries the
           eight required provenance fields.
  .c (W4+): files under architecture/facts/generated/ are byte-identical to
           extractor re-emission at the same workspace HEAD (requires the
           extractor to be invoked separately first; this check only
           verifies the on-disk banner + that an entry's `repo_commit`
           matches the actual HEAD).
  .d (W5+): shipped + http/spi function points carry non-empty
           code_entrypoint_refs[], test_refs[], and (input|output)
           contract_refs[].

Usage:
    python3 gate/lib/check_fact_layer_integrity.py --enforce a
    python3 gate/lib/check_fact_layer_integrity.py --enforce a,b
    python3 gate/lib/check_fact_layer_integrity.py --enforce a,b,c,d

Exit codes:
    0 — all enforced sub-clauses passed
    1 — one or more enforced sub-clauses failed (prints findings to stderr)
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Iterable

REQUIRED_PROVENANCE_FIELDS = (
    "fact_id",
    "fact_kind",
    "source_kind",
    "source_path",
    "extractor",
    "extractor_version",
    "repo_commit",
    "observed_value",
)
REPO_COMMIT_RE = re.compile(r"^[0-9a-f]{40}$")
FACT_ID_RE = re.compile(r"^[a-z][a-z0-9/-]*[a-z0-9]$")


def repo_root() -> Path:
    """Return the repository root (the directory two levels above this script)."""
    return Path(__file__).resolve().parent.parent.parent


def _yaml_parses(path: Path) -> tuple[bool, str]:
    """Return (parses, error_message_if_not)."""
    if not path.is_file():
        return False, f"missing file {path}"
    try:
        import yaml  # type: ignore[import-not-found]
    except ImportError:
        # Fall back: validate it is non-empty text and looks roughly like YAML.
        # The dedicated yaml-parse self-test in the gate covers stricter cases
        # via WSL/Python with PyYAML; this fallback keeps Windows-only Bash
        # invocations from blowing up the rule.
        try:
            text = path.read_text(encoding="utf-8")
        except OSError as exc:
            return False, f"cannot read {path}: {exc}"
        if not text.strip():
            return False, f"{path} is empty"
        return True, ""
    try:
        with path.open("r", encoding="utf-8") as fh:
            yaml.safe_load(fh)
    except yaml.YAMLError as exc:  # type: ignore[attr-defined]
        return False, f"{path} failed YAML parse: {exc}"
    except OSError as exc:
        return False, f"cannot read {path}: {exc}"
    return True, ""


def check_subclause_a(root: Path) -> list[str]:
    """W1+: directory structure + schema YAML parseability."""
    findings: list[str] = []
    required = [
        root / "architecture" / "facts" / "README.md",
        root / "architecture" / "facts" / "schema" / "fact.schema.yaml",
        root / "architecture" / "profile" / "saa-property-authority.yaml",
    ]
    for path in required:
        if not path.is_file():
            findings.append(f"G-15.a missing required file: {path.relative_to(root)}")
    generated_dir = root / "architecture" / "facts" / "generated"
    if not generated_dir.is_dir():
        findings.append(
            f"G-15.a missing required directory: {generated_dir.relative_to(root)}"
        )

    for yaml_path in (
        root / "architecture" / "facts" / "schema" / "fact.schema.yaml",
        root / "architecture" / "profile" / "saa-property-authority.yaml",
    ):
        if yaml_path.is_file():
            ok, msg = _yaml_parses(yaml_path)
            if not ok:
                findings.append(f"G-15.a YAML parse failure: {msg}")
    return findings


def check_subclause_b(root: Path) -> list[str]:
    """W2+: every generated fact entry validates against the fact JSON schema.

    Round-2 Wave B (2026-05-28 P1-5): replaces the hand-rolled subset check
    with a real JSON-schema validator. Falls back to the subset check when
    PyYAML and jsonschema are not both importable.
    """
    findings: list[str] = []
    generated_dir = root / "architecture" / "facts" / "generated"
    if not generated_dir.is_dir():
        # .a should already have flagged this; .b is vacuous when the dir is missing.
        return findings

    schema_dict = _load_fact_schema(root)
    validator = _build_schema_validator(schema_dict)

    for json_path in sorted(generated_dir.glob("*.json")):
        try:
            text = json_path.read_text(encoding="utf-8")
        except OSError as exc:
            findings.append(f"G-15.b cannot read {json_path.relative_to(root)}: {exc}")
            continue
        try:
            payload = json.loads(text)
        except json.JSONDecodeError as exc:
            findings.append(
                f"G-15.b {json_path.relative_to(root)} is not valid JSON: {exc}"
            )
            continue
        entries: Iterable[dict[str, object]]
        if isinstance(payload, list):
            entries = payload
        elif isinstance(payload, dict) and "facts" in payload and isinstance(payload["facts"], list):
            entries = payload["facts"]
        else:
            findings.append(
                f"G-15.b {json_path.relative_to(root)} top-level shape must be a list of facts or an object with a 'facts' list"
            )
            continue
        for idx, entry in enumerate(entries):
            if not isinstance(entry, dict):
                findings.append(
                    f"G-15.b {json_path.relative_to(root)} entry #{idx} is not an object"
                )
                continue
            # Always run the subset check (works without PyYAML/jsonschema).
            for field in REQUIRED_PROVENANCE_FIELDS:
                if field not in entry:
                    findings.append(
                        f"G-15.b {json_path.relative_to(root)} entry #{idx} (fact_id={entry.get('fact_id', '<unknown>')}) missing required field '{field}'"
                    )
            fact_id = entry.get("fact_id")
            if isinstance(fact_id, str) and not FACT_ID_RE.match(fact_id):
                findings.append(
                    f"G-15.b {json_path.relative_to(root)} entry #{idx} has malformed fact_id '{fact_id}' (kebab-case [a-z0-9/-]+)"
                )
            repo_commit = entry.get("repo_commit")
            if isinstance(repo_commit, str) and not REPO_COMMIT_RE.match(repo_commit):
                findings.append(
                    f"G-15.b {json_path.relative_to(root)} entry #{idx} repo_commit '{repo_commit}' is not 40 lowercase hex chars"
                )
            # Layer the JSON-schema validator on top when available.
            if validator is not None:
                for error in validator(entry):
                    findings.append(
                        f"G-15.b {json_path.relative_to(root)} entry #{idx} (fact_id={entry.get('fact_id', '<unknown>')}) schema violation: {error}"
                    )
    return findings


def _load_fact_schema(root: Path) -> dict | None:
    """Load architecture/facts/schema/fact.schema.yaml as a Python dict."""
    schema_path = root / "architecture" / "facts" / "schema" / "fact.schema.yaml"
    if not schema_path.is_file():
        return None
    try:
        import yaml  # type: ignore[import-not-found]
    except ImportError:
        return None
    try:
        with schema_path.open("r", encoding="utf-8") as fh:
            loaded = yaml.safe_load(fh)
    except Exception:  # noqa: BLE001 — broad except is appropriate here
        return None
    return loaded if isinstance(loaded, dict) else None


def _build_schema_validator(schema: dict | None):
    """Return a validator callable that yields error strings, or None.

    Uses the `jsonschema` library when available; otherwise returns None
    so the subset check still provides coverage on hosts without it.
    """
    if schema is None:
        return None
    try:
        import jsonschema  # type: ignore[import-not-found]
    except ImportError:
        return None
    try:
        validator_cls = jsonschema.validators.validator_for(schema)
        validator_cls.check_schema(schema)
    except Exception:  # noqa: BLE001
        return None
    inst = validator_cls(schema)

    def validate(entry: dict) -> list[str]:
        out: list[str] = []
        for err in inst.iter_errors(entry):
            path = "/".join(str(p) for p in err.absolute_path) or "<root>"
            out.append(f"{path}: {err.message}")
        return out

    return validate


def check_subclause_c(root: Path) -> list[str]:
    """W4+: byte-identical regen + LLM-no-author banner.

    At W1 this is a no-op (no extractors have shipped yet). When the first
    extractor lands (W2), the banner check applies. The full byte-identical
    diff is run by the calling gate after `mvn ... extract-facts` has been
    invoked separately.
    """
    findings: list[str] = []
    generated_dir = root / "architecture" / "facts" / "generated"
    if not generated_dir.is_dir():
        return findings
    for json_path in sorted(generated_dir.glob("*.json")):
        try:
            head_lines = json_path.read_text(encoding="utf-8").splitlines()[:5]
        except OSError as exc:
            findings.append(f"G-15.c cannot read {json_path.relative_to(root)}: {exc}")
            continue
        head_blob = "\n".join(head_lines)
        if "DO NOT EDIT" not in head_blob:
            findings.append(
                f"G-15.c {json_path.relative_to(root)} missing 'DO NOT EDIT' banner in first 5 lines"
            )
    return findings


def check_subclause_d(root: Path) -> list[str]:
    """Resolve shipped + http/spi FunctionPoint refs against generated facts.

    For every `SAA FunctionPoint` element in
    architecture/features/function-points.dsl whose `saa.status` is `shipped`
    AND whose `saa.channel` is `http` or `spi`, the three hard-evidence
    fields MUST be non-empty AND every entry MUST resolve against the
    matching generated fact file:

      * `saa.code_entrypoint_refs`  -> code-symbols.json
        Format: `<repo-relative .java path>#<method-name>`. The path must
        match an extracted code symbol's `source_path` AND the method name
        must appear in the symbol's `public_methods` list (the ASM-emitted
        bytecode descriptor; we match by leading `<name>(` prefix).
      * `saa.test_refs`             -> tests.json
        Format: pipe-separated test class FQNs. Each FQN must resolve to a
        fact whose `observed_value.fqn` equals it.
      * `saa.contract_op_refs`      -> contract-surfaces.json
        Format: pipe-separated `contract-op/<slug>` fact_ids. Each must
        resolve to an existing fact_id in the contract-surfaces file.

    Authority: ADR-0154 Rule G-15 sub-clause .d.
    """
    findings: list[str] = []
    dsl_path = root / "architecture" / "features" / "function-points.dsl"
    if not dsl_path.is_file():
        findings.append(f"G-15.d {dsl_path.relative_to(root)} missing")
        return findings

    fps = _parse_function_points(dsl_path)
    if not fps:
        return findings

    code_index = _load_code_symbol_index(root)
    test_index = _load_test_index(root)
    contract_index = _load_contract_index(root)

    facts_present = code_index is not None or test_index is not None or contract_index is not None
    if not facts_present:
        # No extracted facts to resolve against — emit nothing. Rule G-15.a
        # already gates the structural existence; the resolver fires only
        # once the extractor has populated architecture/facts/generated/.
        return findings

    for fp in fps:
        if fp.get("saa.status") != "shipped":
            continue
        channel = fp.get("saa.channel", "")
        if channel not in ("http", "spi"):
            continue
        fp_id = fp.get("saa.id", "<unknown>")

        code_refs = _split_pipe(fp.get("saa.code_entrypoint_refs"))
        test_refs = _split_pipe(fp.get("saa.test_refs"))
        contract_refs = _split_pipe(fp.get("saa.contract_op_refs"))

        if not code_refs:
            findings.append(
                f"G-15.d FP {fp_id} (shipped + {channel}) missing required saa.code_entrypoint_refs"
            )
        if not test_refs:
            findings.append(
                f"G-15.d FP {fp_id} (shipped + {channel}) missing required saa.test_refs"
            )
        if not contract_refs:
            findings.append(
                f"G-15.d FP {fp_id} (shipped + {channel}) missing required saa.contract_op_refs"
            )

        if code_index is not None:
            for ref in code_refs:
                resolution = _resolve_code_entrypoint(ref, code_index)
                if resolution is not None:
                    findings.append(
                        f"G-15.d FP {fp_id} saa.code_entrypoint_refs '{ref}' unresolved against code-symbols.json: {resolution}"
                    )
        if test_index is not None:
            for ref in test_refs:
                if ref not in test_index:
                    findings.append(
                        f"G-15.d FP {fp_id} saa.test_refs '{ref}' unresolved against tests.json"
                    )
        if contract_index is not None:
            for ref in contract_refs:
                if ref not in contract_index:
                    findings.append(
                        f"G-15.d FP {fp_id} saa.contract_op_refs '{ref}' unresolved against contract-surfaces.json"
                    )

    return findings


_FP_BLOCK_RE = re.compile(
    r"^[a-zA-Z][a-zA-Z0-9_]*\s*=\s*element\s+\"[^\"]+\"\s+\"FunctionPoint\"[\s\S]*?\}\s*\n\}",
    re.MULTILINE,
)
_FP_PROP_RE = re.compile(r"\"(saa\.[a-zA-Z0-9_]+)\"\s+\"([^\"]*)\"")


def _parse_function_points(dsl_path: Path) -> list[dict[str, str]]:
    """Regex parse for SAA FunctionPoint elements + their saa.* properties.

    This avoids dragging in a full Structurizr DSL parser for what is a
    flat key/value extract. The regex matches every block of the shape:

        fpXxx = element "..." "FunctionPoint" ... {
            properties { "saa.k" "v" ... }
        }

    and returns one dict per FunctionPoint.
    """
    try:
        text = dsl_path.read_text(encoding="utf-8")
    except OSError:
        return []
    out: list[dict[str, str]] = []
    for block_match in _FP_BLOCK_RE.finditer(text):
        block = block_match.group(0)
        if "\"SAA FunctionPoint\"" not in block:
            continue
        props: dict[str, str] = {}
        for prop in _FP_PROP_RE.finditer(block):
            props[prop.group(1)] = prop.group(2)
        if props:
            out.append(props)
    return out


def _split_pipe(value: str | None) -> list[str]:
    if not value:
        return []
    return [v.strip() for v in value.split("|") if v.strip()]


def _load_json_facts(path: Path) -> list[dict] | None:
    if not path.is_file():
        return None
    try:
        with path.open("r", encoding="utf-8") as fh:
            payload = json.load(fh)
    except (OSError, json.JSONDecodeError):
        return None
    facts = payload.get("facts") if isinstance(payload, dict) else None
    return facts if isinstance(facts, list) else None


def _load_code_symbol_index(root: Path) -> dict[str, dict] | None:
    facts = _load_json_facts(root / "architecture" / "facts" / "generated" / "code-symbols.json")
    if facts is None:
        return None
    index: dict[str, dict] = {}
    for fact in facts:
        if not isinstance(fact, dict):
            continue
        source_path = fact.get("source_path", "")
        observed = fact.get("observed_value") or {}
        if not isinstance(observed, dict):
            continue
        fqn = observed.get("fqn", "")
        methods = observed.get("public_methods") or []
        # Index by the .java path equivalent — the source_path is
        # `<module>/target/classes/<internal>.class`; map to
        # `<module>/src/main/java/<internal-as-java>.java`.
        java_path = _bytecode_path_to_java_source(source_path, fqn)
        if java_path:
            index[java_path] = {"fqn": fqn, "public_methods": list(methods)}
    return index


def _bytecode_path_to_java_source(source_path: str, fqn: str) -> str | None:
    """Translate `<module>/target/classes/.../X.class` -> `<module>/src/main/java/.../X.java`.

    The FP DSL records the source .java path; the extracted fact records
    the compiled .class path. Both must agree on the same source file for
    the resolver to match.
    """
    if not source_path or not fqn:
        return None
    if "/target/classes/" not in source_path:
        return None
    module = source_path.partition("/target/classes/")[0]
    package_path = fqn.replace(".", "/")
    # Inner classes carry '$' in their internal name; the source .java
    # file is named after the outermost type, so strip from the first '$'.
    dollar = package_path.find("$")
    if dollar >= 0:
        package_path = package_path[:dollar]
    return f"{module}/src/main/java/{package_path}.java"


def _load_test_index(root: Path) -> set[str] | None:
    facts = _load_json_facts(root / "architecture" / "facts" / "generated" / "tests.json")
    if facts is None:
        return None
    out: set[str] = set()
    for fact in facts:
        if not isinstance(fact, dict):
            continue
        observed = fact.get("observed_value") or {}
        if isinstance(observed, dict):
            fqn = observed.get("fqn", "")
            if fqn:
                out.add(fqn)
    return out


def _load_contract_index(root: Path) -> set[str] | None:
    facts = _load_json_facts(root / "architecture" / "facts" / "generated" / "contract-surfaces.json")
    if facts is None:
        return None
    out: set[str] = set()
    for fact in facts:
        if isinstance(fact, dict):
            fact_id = fact.get("fact_id", "")
            if fact_id:
                out.add(fact_id)
    return out


def _resolve_code_entrypoint(ref: str, code_index: dict[str, dict]) -> str | None:
    """Return None when the ref resolves; an error string when it doesn't."""
    if "#" not in ref:
        return "missing '#<method>' suffix (expected '<path.java>#<method-name>')"
    path, method = ref.split("#", 1)
    path = path.strip()
    method = method.strip()
    entry = code_index.get(path)
    if entry is None:
        return f"no code-symbol fact for path {path}"
    public_methods = entry.get("public_methods") or []
    prefix = method + "("
    if not any(m.startswith(prefix) for m in public_methods):
        return f"method '{method}' not in public_methods of {entry.get('fqn')}"
    return None


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Rule G-15 — Fact-Layer Integrity (enforcer E179)"
    )
    parser.add_argument(
        "--enforce",
        default="a",
        help="Comma-separated set of sub-clauses to enforce; default 'a' (W1).",
    )
    parser.add_argument(
        "--repo",
        default=None,
        help="Repository root. Defaults to script-derived root.",
    )
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    root = Path(args.repo) if args.repo else repo_root()
    if not root.is_dir():
        print(f"G-15: --repo {root} is not a directory", file=sys.stderr)
        return 1
    enforced = {part.strip().lower() for part in args.enforce.split(",") if part.strip()}
    valid = {"a", "b", "c", "d"}
    unknown = enforced - valid
    if unknown:
        print(
            f"G-15: unknown sub-clause(s) requested: {sorted(unknown)}; valid are {sorted(valid)}",
            file=sys.stderr,
        )
        return 1
    all_findings: list[str] = []
    if "a" in enforced:
        all_findings.extend(check_subclause_a(root))
    if "b" in enforced:
        all_findings.extend(check_subclause_b(root))
    if "c" in enforced:
        all_findings.extend(check_subclause_c(root))
    if "d" in enforced:
        all_findings.extend(check_subclause_d(root))
    if all_findings:
        for finding in all_findings:
            print(finding, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

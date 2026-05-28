---
rule_id: G-13
title: "Single-Source Rendering Coherence"
level: L0
view: scenarios
principle_ref: P-C
authority_refs: [ADR-0119]
enforcer_refs: [E174]
status: active
governance_infra: true
scope_phase: commit
kernel_cap: 8
scope_surfaces:
  - docs/governance/templates/surface-classification.yaml
  - docs/governance/templates/*.md.j2
  - docs/logs/releases/*.md
  - docs/governance/recurring-defect-families.md
  - docs/contracts/contract-catalog.md
  - ARCHITECTURE.md
  - agent-*/ARCHITECTURE.md
  - README.md
  - gate/README.md
  - CLAUDE.md
kernel: |
  **Every dynamic claim in the active corpus (count, path, module name, SPI FQN, ADR id, family id, version tag, baseline metric, generated table row) lives in exactly one of: (a) an authority YAML / decision YAML (source-of-truth), (b) a deterministically-generated YAML (e.g. `architecture-graph.yaml`), or (c) a rendered slot in a `*.md.j2` template under `docs/governance/templates/`. No dynamic claim is allowed to exist as free-typed prose anywhere. The corresponding rendered `.md` artifact MUST be byte-identical to `render(template, data)` — gate fails on any drift, including whitespace. Subsumes Rules G-2.b, G-2.d (root portion), G-2.1, G-8.a, G-8.c, G-8.e, G-9.c in the W3..W10 retirement schedule per ADR-0119; subsumed rules remain as defence-in-depth until W10 cleanup.**
---

# Rule G-13 — Single-Source Rendering Coherence

Added in the rc42 / W0 wave per ADR-0119 as the structural backstop for the
F-numeric-drift, F-deleted-module-name-leakage, F-recurring-family-yaml-md-drift,
and cross-authority-disagreement meta-families. Replaces a corpus of
reactive prevention rules with one constructive invariant.

## Motivation

The proof-of-concept for the correct pattern already lives in the repo:
`docs/governance/architecture-graph.yaml` is machine-rendered by
`gate/build_architecture_graph.py` from inputs that are themselves
hand-authored, and Rule G-1.b enforces byte-identical idempotency. That
file has had **zero drift defects since rc14**. Every other derived
artefact — release notes, the README numeric blocks,
`recurring-defect-families.md`, the contract catalog, root and L1
`ARCHITECTURE.md` numeric/path cells — is hand-authored and has had
drift defects every wave: F-numeric-drift alone recurs 14+ times from
rc5 through rc40.

The asymmetry says the fix is to flip every derived artefact onto the
same render-idempotency pattern. Rule G-13 captures that decision.

## Sub-clauses

### .a — Surface Classification

**Enforcer**: E174 sub-check a.

`docs/governance/templates/surface-classification.yaml` MUST exist and
classify every active `.md`/`.yaml` file in the corpus into exactly one
of three buckets:

- `templated` — fully rendered output, "DO NOT EDIT" header, byte-identical
  regen enforced.
- `hybrid` — file is a `.md.j2` source with `{{ ... }}` slots interleaved
  through author prose; rendered to a `.md` sibling.
- `inline` — authored source-of-truth, never rendered, never derived.

Each row declares `template:`, `output:`, `context_loader:`, and
`bucket:`. Schema lives at
`docs/governance/templates/surface-classification.schema.yaml`. The W1
foundation wave ships the schema; W3..W8 populate the rows.

### .b — Render Idempotency

**Enforcer**: E174 sub-check b.

For every `templated` or `hybrid` entry in `surface-classification.yaml`,
`render(template, load_context())` MUST produce a buffer byte-identical
to the on-disk `output:` file. The check runs in `--check` mode (renders
to a temp buffer and diffs against committed output). Any difference —
including trailing whitespace, line-ending variation, or sort-order shift
— FAILS.

Determinism contract for `gate/lib/render_template.py`:

- Sorted iteration over dict keys
- `LC_ALL=C` sort for any list ordering
- No `datetime.now()`, no `time.time()`, no `uuid.uuid4()`
- Fixed-precision float formatting (3 decimal places unless otherwise
  specified)
- Jinja2 environment with `trim_blocks=True`, `lstrip_blocks=True`,
  `keep_trailing_newline=True`

### .c — Inline-Dynamic-Claim Prohibition

**Enforcer**: E174 sub-check c (W10 audit; advisory until then).

Outside the `inline` bucket, no source file may contain a free-typed
dynamic claim that is also rendered elsewhere. The audit script
`gate/lib/audit_inline_dynamic_claims.py` (lands W10) walks active corpus
files and reports any residual hand-typed count, module name, SPI FQN, or
ADR id that overlaps with values declared in an authority YAML or
generated YAML. Each finding is either resolved (move into a template
slot) or grandfathered in
`gate/inline-dynamic-claims-grandfathered.txt` with a `sunset_date:`.

## Why three sub-clauses

The three sub-clauses cover three orthogonal failure modes:

- .a catches **classification gaps** — a surface that should be
  rendered but isn't registered.
- .b catches **render drift** — the template and the committed output
  disagree (someone edited one without the other, or the data source
  moved and re-render was skipped).
- .c catches **scope leakage** — a dynamic claim re-introduced as
  inline prose despite the template existing for it.

Each has its own self-test fixture (positive + negative) landing
incrementally through W1..W10.

## Why Rule G-13 subsumes other rules

The render-idempotency invariant constructively eliminates the failure
modes that reactive prevention rules currently police:

| Subsumed rule | Failure mode | How G-13 eliminates it |
|---|---|---|
| G-2.b — README baseline-metrics single source | README hand-typed counts drift | W8 renders the counts from `architecture-status.yaml` |
| G-2.d — root ARCHITECTURE.md count parity | Root ARCH hand-typed counts drift | W6 renders root ARCH count rows |
| G-2.1 — deleted-module-name scope prevention | Deleted module name leaks into prose | W3/W5/W6/W7 render module names from `pom.xml` — deleted names cannot leak |
| G-8.a — graph baseline parity | architecture-status baseline vs graph header disagree | W3/W6 render baseline rows from `architecture-graph.yaml` directly |
| G-8.c — module topology parity | "Each of the N modules" prose with stale N | W3/W6 render module count and list from `pom.xml` |
| G-8.e — structural-carrier parity | Catalog row names a class with no source file | W5 renders catalog from a Java source scan |
| G-9.c — recurring-families yaml↔md parity | Family ids in .md drift from .yaml | W4 renders .md from .yaml |

Retirement is staged in W10 only after every targeted surface has shipped
through W3..W8 and demonstrated byte-identical regen for one full release
cycle. Subsumed rules carry `superseded_by: Rule G-13` annotation in
their cards.

## Why this rule cannot be skipped via skill or flag

The companion `/refresh-architecture-doc` skill (W2) is a developer
convenience that walks the refresh-order pipeline and re-renders
templates. But the skill is opt-in. Rule G-13.b is the gate that catches
the case where the skill was never run.

There is no `--allow-drift` override. The check is byte-identical or
fail; the only resolution to a failure is re-render the template OR fix
the data source.

## Determinism failure modes (W1 hardening checklist)

- Dict ordering non-determinism → `sorted()` enforced in templates that
  iterate over dicts.
- Python set-iteration order → cast to sorted list before render.
- Locale-dependent string sort → `LC_ALL=C` env-var asserted at render
  start.
- File mtime / build timestamp leaks → no `datetime` or `os.stat`
  reachable from template context.
- Floating-point precision drift across machine arches → fixed
  `%.3f`-style format strings, never raw `{{ value }}` for floats.
- Newline-ending drift between Windows and Linux authoring →
  `keep_trailing_newline=True`; gate validates LF endings in committed
  rendered output.

## Activation

Activated 2026-05-25 by the W0 wave per ADR-0119. Enforcer E174
registered as `pending_w1`; promoted to `active` when
`gate/check_template_render_idempotency.sh` lands in W1.

## Self-tests (satisfying Rule 110 META requirements)

Per Rule 110 META, every prevention rule must declare `scope_surfaces:`
frontmatter AND carry ≥2 self-test fixtures across distinct surfaces.
Rule G-13 declares 10 surfaces; fixtures ship incrementally:

- W1 — Fixture 1 (sub-clause .a): synthetic template registered but file
  missing → Rule fails. Fixture 2 (sub-clause .b): vacuous-registry pass.
- W3 — Fixture 3 (sub-clause .b): mutate a release-note baseline value →
  Rule fails with field-level mismatch report.
- W4 — Fixture 4 (sub-clause .b): mutate `recurring-defect-families.yaml`
  without re-render → Rule fails.
- W10 — Fixture 5 (sub-clause .c): inline-dynamic-claim audit positive
  case → Rule fails.

All fixtures live under `gate/test_template_render.py` (created in W1)
following the existing `test_rule_NNN_*` naming pattern.

## Relationship to Rule G-15 (Fact-Layer Integrity)

Rule G-13 polices **rendered** Markdown / YAML — `templated` and
`hybrid` buckets in `docs/governance/templates/surface-classification.
yaml`. Rule G-15 (added 2026-05-27 per ADR-0154) extends the same
byte-identical-on-regen discipline to **extracted** facts under
`architecture/facts/generated/*.json` (emitted by deterministic
extractor binaries reading code / contracts / tests / build files /
config / ADRs).

The two rules share the byte-identical invariant but apply it to
different artifact classes. They do NOT duplicate. At W6 of the
Fact-Layer plan, `surface-classification.yaml` adds rows for the
`architecture/facts/generated/*.json` outputs classifying them as
`templated` with the extractor as the renderer — so G-13.b ALSO catches
fact-layer drift as defence-in-depth, alongside G-15.c (the fact-
specific banner + LLM-no-author check).

## Cross-references

- ADR-0119 — Single-Source Rendering for derived architecture documents
  (this rule's authoring ADR + 11-wave roadmap).
- ADR-0154 — Fact-Layer Authority (Rule G-15, the sibling rule that
  extends G-13 to extracted facts).
- ADR-0068 — Layered 4+1 + architecture-graph as twin sources of truth
  (proof-of-concept of render idempotency at zero defect rate).
- Rule G-1.b — architecture-graph idempotency (pattern G-13 generalises).
- Rule G-9 — Recurring-defect family truth (G-13.b subsumes G-9.c
  yaml↔md parity in W4).
- Rule G-15 — Fact-Layer Integrity (sibling to G-13 for extracted facts).
- Rule G-2.b, G-2.d, G-2.1 — Drift prevention rules (subsumed by G-13 in
  W6/W7/W8/W10).
- Rule G-8.a/c/e — Cross-authority parity (subsumed by G-13 in W3/W5/W6).
- `docs/governance/templates/` — template registry, schemas, and all
  `.md.j2` sources.
- `gate/lib/render_template.py` — render engine (lands W1).
- `gate/lib/load_render_context.py` — context loader (lands W1).
- `gate/check_template_render_idempotency.sh` — gate driver (lands W1).
- `architecture/facts/` — extracted-fact surface policed by Rule G-15.
- `.claude/skills/refresh-architecture-doc.md` — orchestrator skill
  (lands W2).

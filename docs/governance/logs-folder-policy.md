# Logs-Folder Snapshot-Evidence Policy

**Authority:** ADR-0093 (rc16 cross-authority parity + meta scope completeness).
**Status:** active (rc16 wave landing).

## Statement

Files under `docs/logs/reviews/` and `docs/logs/releases/` (except the **latest** release note, which is enforced by Rule 97 — `release_note_numeric_truth`) are **point-in-time interaction records**. Numeric values inside these files (graph node/edge counts, baseline metrics, rule counts, enforcer-row counts, ADR counts) are **snapshot evidence** captured at the moment the document was authored, NOT continuously-maintained authority.

These files are **exempt from architecture-graph numeric-truth gates** by design.

## Rationale

The reviewer of rc15 (`docs/logs/reviews/2026-05-20-l0-rc15-post-closure-architecture-review.en.md` finding P2-2) proposed extending the release-note numeric-truth gate (Rule 97) to also scan closure-response verification blocks in `docs/logs/reviews/`. The user rejected this in rc16 with explicit rationale (translated): *"interaction records in logs/ shouldn't be locked by the architecture graph, otherwise other architecture teams will spend their energy on format adjustments."*

The reasoning is structural:

1. **Logs/ are interaction records by architectural intent.** Each review document and closure response captures a conversation at a point in time. The numeric values were correct at that moment; the document itself is the audit trail of what was discussed and decided.
2. **Locking logs/ to the live graph forces retroactive edits.** Every architecture team that publishes a review or response would have to either (a) match the live graph at the moment of writing, OR (b) carry exemption markers AND keep updating the markers as the graph advances. Either path drains team energy into format chasing.
3. **Canonical baseline already lives in a single authoritative place.** `docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics` is the single normative source for current counts. Logs/ files that disagree are snapshots; status.yaml is truth. No gate is needed to point readers there.
4. **The historical-snapshot marker pattern already handles this organically.** Closure responses for rc7 / rc9 / rc10 / rc11 already carry `> **Historical artifact frozen at SHA <merge-sha>**` markers. The rc14 closure response was the first to lag this convention; rc16 retrofitted the marker as part of P2-2 closure.

## Canonical pattern: historical-snapshot marker

When a `docs/logs/reviews/*.md` or non-latest `docs/logs/releases/*.md` document contains numeric values that may drift from current canonical baselines, the document MUST carry the following block immediately after its top-level `# Heading`:

```markdown
> **Historical artifact frozen at SHA <40-char-sha> (<wave-description>).** Baseline counts in this document (e.g. `<N> nodes / <M> edges`) reflect state at <wave name> publication time and are NOT retroactively updated per the logs-folder snapshot-evidence policy (`docs/governance/logs-folder-policy.md`). Canonical baseline lives in `docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`.
```

The marker text is intentionally human-readable; no parser depends on it. Adding the marker is a documentation hygiene practice, not a gate requirement.

## Gate scope (what IS enforced)

The following gate rules DO scan log-folder files, but ONLY for non-numeric authority surfaces:

| Rule | Scope | What it enforces | What it does NOT enforce |
|---|---|---|---|
| Rule 39 (`review_proposal_front_matter`) | `docs/logs/reviews/*.md` | Front-matter is **optional** (interaction records); validated only when a doc opts into 4+1 proposal classification by declaring `affects_level:`/`affects_view:` (then both required + valid) | Front-matter presence on plain records; body content; numerics |
| Rule 26 (`release_note_shipped_surface_truth`) | `docs/logs/releases/*.md` (all) | No `shipped: true` claims for unshipped rows | Numerics |
| Rule 28 (`release_note_baseline_truth`) | `docs/logs/releases/*.md` **latest only** | First numeric column matches `architecture-status.yaml#baseline_metrics` | Earlier release notes |
| Rule 33 (`release_note_references_four_pillars`) | `docs/logs/releases/*.md` **latest only** | All four pillar names appear | Earlier release notes |
| Rule 63 (`release_note_retracted_tag_qualified`) | `docs/logs/releases/*.md` | RETRACTED release notes are qualified | Numerics |
| Rule 97 (`release_note_numeric_truth`) | `docs/logs/releases/*.md` **latest only** | Absolute `<N> nodes / <M> edges` claims match live graph | Earlier release notes; ALL review docs |
| Rule 102 (`release_recency_resolver_correctness`) | Helper / gate machinery | rc-number-aware "latest release" selector | Body content |

## Gate scope (what is NOT enforced — by design)

No gate rule enforces architecture-graph numeric parity against:
- `docs/logs/reviews/*.md` body content (numeric values inside review or closure-response documents)
- `docs/logs/releases/*.md` non-latest body content (older release notes are historical snapshots)
- `docs/logs/conversations/**` (raw session transcripts, if present)

No gate rule REQUIRES front-matter on `docs/logs/reviews/*.md`. These are interaction records, not authority artefacts, so a review response / findings log / PR response needs no `level:`/`view:`/`affects_*` block at all. Rule 39 only validates `affects_level:`/`affects_view:` *when a document opts into* 4+1 proposal classification by declaring one of them — a half-classified proposal is then caught, but a plain record is never forced to carry front-matter. This keeps logs friction-free per the rc16 directive while preserving classification quality for real unfreeze proposals. (Rule 37 — `architecture_artefact_front_matter` — enforces mandatory `level:`/`view:` only on `ARCHITECTURE.md`, `docs/L2/`, and `docs/adr/`, never on logs.)

If a future wave's reviewer proposes adding such enforcement, the proposed change is governed by this document — it requires either (a) an explicit ADR amending this policy with clear cost-benefit argument, or (b) demonstration that the marker convention has materially failed (e.g., review documents are being treated as authority by tooling, causing real downstream incidents).

## Companion artifacts

- ADR-0093 — origin of this policy.
- `feedback_release_note_baseline_truth` memory — canonical = wave-final = release-note first numeric column; older release notes need `Historical artifact frozen at SHA <merge-sha>` marker.
- `docs/logs/reviews/2026-05-20-l0-rc15-post-closure-architecture-review-response.en.md` — rc16 closure response containing the P2-2 pushback narrative + this policy citation.

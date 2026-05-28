---
principle_id: P-B
title: "Four Competitive Pillars"
level: L0
view: scenarios
authority: "Layer 0 governing principle (CLAUDE.md)"
enforced_by_rules: [R-B]   # formerly Rule 30 (numeric pre-ADR-0086)
product_claim: "PC-001|PC-002|PC-003|PC-005"
kernel: |
  P-B — Four Competitive Pillars.
  Platform competitiveness rests on four continuously-improvable dimensions —
  **Performance** (latency, throughput),
  **Cost** (per-call + infra),
  **Developer Onboarding** (time-to-first-agent + surface complexity),
  **Governance** (tenant isolation, audit, eval, safety).
  Each dimension MUST have a published baseline that future releases can be
  measured against. Enforced by Rule R-B.
scope_phase: design
---

## Motivation

This principle exists because a platform without a **published baseline** on each competitive dimension cannot detect regression — every release will silently trade latency for cost or onboarding-time for governance, and there will be no audit trail to argue the trade was deliberate. The four pillars — **Performance**, **Cost**, **Developer Onboarding**, **Governance** — were chosen as the minimum set such that every external buyer comparison reduces to one of them. Rule R-B makes the baseline a release-blocking artifact (`docs/governance/competitive-baselines.yaml`), and any regression must be paired with a `regression_adr:` reference so the rationale lives next to the number.

## Operationalising rules

- Rule R-B — Competitive Baselines Required ([`docs/governance/rules/rule-R-B.md`](../rules/rule-R-B.md))

## Cross-references

- ADR-0065 (origin of Rule R-B and the four-pillar baseline corpus)
- Deferred sub-clauses 30.b (git-diff regression-ADR pairing), 30.d (measurement automation — W2/W3); legacy deferred-rule registry retired 2026-05-28, see [`retired-rules-audit.md`](../retired-rules-audit.md)
- Related: every release note under `docs/logs/releases/*.md` must mention all four pillar names (Gate Rule G-1 sub-clause .a)

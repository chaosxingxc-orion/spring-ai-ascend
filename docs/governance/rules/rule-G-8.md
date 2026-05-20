---
rule_id: G-8
title: "Cross-Authority Parity (graph baseline / SPI path / module topology / current-claim grammar)"
level: L0
view: process
principle_ref: P-C
authority_refs: [ADR-0086, ADR-0088, ADR-0090]
enforcer_refs: [E146, E147, E148, E149]
status: active
kernel_cap: 8
kernel: |
  **Canonical authority surfaces MUST agree with each other, not just be internally well-formed. (sub-clause .a — Graph baseline parity) `docs/governance/architecture-status.yaml#baseline_metrics.architecture_graph_nodes` AND `architecture_graph_edges` MUST equal the `node_count` AND `edge_count` declared in `docs/governance/architecture-graph.yaml` (live header from `python gate/build_architecture_graph.py --check --no-write`). (sub-clause .b — SPI path parity) every SPI package named in a CLAUDE.md `#### Rule` kernel OR a `docs/governance/rules/rule-*.md` card kernel MUST appear in exactly one `<module>/module-metadata.yaml#spi_packages` entry, exist on disk (`<module>/src/main/java/<package-path>/`), and match enforcer `asserts:` text in `docs/governance/enforcers.yaml`. (sub-clause .c — Module topology parity) the set of `<module>` entries in root `pom.xml`, `docs/governance/architecture-status.yaml#repository_counts.reactor_modules`, and the `<module>/module-metadata.yaml` files on disk MUST agree on the live module count; "each of the N modules" prose in active `.md` MUST use the same N. (sub-clause .d — Current-claim grammar) a same-line `post-ADR-NNNN` marker does NOT exempt a sentence containing present-tense verbs (`now reads`, `lives in`, `declares`, `includes`, `depends on`, `each of the [0-9]+ modules`) when that sentence names a deleted module from `gate/active-corpus-name-exemption-markers.txt`'s deleted-name set — exemption is reserved for explicitly historical prose (`formerly`, `until dissolved`, `pre-rc13`).**
---

# Rule G-8 — Cross-Authority Parity (graph baseline / SPI path / module topology / current-claim grammar)

## Motivation

Closes rc13 post-ratchet review P1-5 (L-δ family): a wave can pass every single-surface scanner (Rule 87 / 94 / 98 / 101) while still leaving the L0 authority system internally contradictory. Rule G-8 gates the *agreement* between canonical surfaces, not their well-formedness. The rule exists because rc13 demonstrated that:

- `architecture-status.yaml#baseline_metrics.architecture_graph_nodes: 363` and `architecture_graph_edges: 539` (canonical baseline) disagreed with the generated graph (376 / 558) while every existing rule still passed.
- `CLAUDE.md` Rule R-M kernel + `rule-R-M.md` card + `s2c-callback.v1.yaml` comment all named `ascend.springai.service.runtime.s2c.spi` while `agent-bus/module-metadata.yaml#spi_packages` + `contract-catalog.md` + the source tree all named `ascend.springai.bus.spi.s2c` (post-ADR-0088 truth).
- `architecture-status.yaml` `allowed_claim` text named "each of the 9 reactor modules" with `agent-runtime-core` listed, while `pom.xml`'s `<modules>` block + `repository_counts.reactor_modules` declared 8.
- The single-surface rule 87/94/98 deleted-module scanners were exempt-by-marker, which let post-ADR-0079 markers shield post-ADR-0088 stale present-tense claims.

The pattern: each cited surface was internally OK; only the *cross-surface* comparison surfaced the defect. Rule G-8 is the cross-surface comparator.

## Sub-clauses

### .a — Graph baseline parity (enforcer E146)

`docs/governance/architecture-status.yaml#baseline_metrics.architecture_graph_nodes` MUST equal the `node_count:` value declared in the header of `docs/governance/architecture-graph.yaml`. Same parity for `architecture_graph_edges` vs `edge_count:`. The rule is checked against the file header (cheap, no python invocation); the build-graph python script remains authoritative for regeneration.

### .b — SPI path parity (enforcer E147)

For every SPI package literal (`ascend\.springai\.[a-z][a-z.]*\.(spi|spi\.[a-z][a-z.]*)`) named in `CLAUDE.md` or any `docs/governance/rules/rule-*.md` kernel block, the package MUST appear in exactly one `<module>/module-metadata.yaml#spi_packages:` list entry AND a directory matching the package path MUST exist on disk under `<module>/src/main/java/`. Mismatch means the kernel and the metadata disagree on which module owns the SPI — exactly the rc13 R-M failure mode.

### .c — Module topology parity (enforcer E148)

The set of `<module>...</module>` entries in root `pom.xml` MUST match (a) the integer `architecture-status.yaml#repository_counts.reactor_modules` (b) the count of `*/module-metadata.yaml` files on disk and (c) every "each of the [0-9]+ modules" or "[0-9]+ reactor modules" present-tense numeric claim across active `.md` / `.yaml` files outside `docs/archive/` and `docs/logs/`.

### .d — Current-claim grammar (enforcer E149)

A line in an active `.md` / `.yaml` file (outside `docs/archive/`, `docs/logs/`, fenced code blocks, and the exempted-paths list `gate/active-corpus-name-exemption-paths.txt`) that contains BOTH (1) a deleted-module token from the corpus exemption-markers' canonical deleted-name vocabulary (`agent-platform`, `agent-runtime`, `agent-runtime-core`) AND (2) one of the present-tense verbs `now reads`, `lives in`, `declares`, `includes`, `depends on`, or the pattern `each of the [0-9]+ (reactor )?modules` MUST be marked by an *explicitly historical* token (`formerly`, `until dissolved`, `pre-rc13`, `historical`, `narration`). The bare marker `post-ADR-[0-9]+` does NOT count as historical — it only attests provenance, not tense.

## Activation

Activated 2026-05-20 by the rc14 corrective wave (this rule). Enforcers E146 / E147 / E148 / E149. Closes P1-5 of `docs/logs/reviews/2026-05-20-l0-rc13-post-ratchet-architecture-review.en.md`.

## Cross-references

- ADR-0090 — decision authority for the cross-authority parity wave (rc14).
- ADR-0086 (`gate_layer_boundary`) — the gate-layer / authority-layer split that Rule G-8 reinforces.
- ADR-0088 — agent-runtime-core dissolution; the structural change that surfaced the cross-surface gap.
- Rule 87 / Rule 94 / Rule 98 — single-surface deleted-module scanners; Rule G-8 is the parity complement.
- Rule 101 — namespace authority completeness; same architectural pattern (parity across 3+ surfaces).
- Rule 91 — gate self-consistency parity / coverage / manifest / freshness; the kindred multi-sub-clause structure G-8 mirrors.

## Self-tests

Gate self-test fixtures live in `gate/test_architecture_sync_gate.sh` with eight cases:

- `test_rule_G_8_a_positive` — live baseline numbers equal live graph header.
- `test_rule_G_8_a_negative` — synthetic mismatch (baseline 100 vs graph 200) → rule fails.
- `test_rule_G_8_b_positive` — every kernel SPI package resolves on disk + module-metadata.
- `test_rule_G_8_b_negative` — synthetic kernel naming a nonexistent SPI package → rule fails.
- `test_rule_G_8_c_positive` — pom.xml module count agrees with repository_counts.
- `test_rule_G_8_c_negative` — synthetic "each of the 9 modules" prose vs 8-module pom → rule fails.
- `test_rule_G_8_d_positive` — active `agent-runtime-core` mentions all have historical markers.
- `test_rule_G_8_d_negative` — synthetic line "agent-runtime-core declares its SPI packages (post-ADR-0079)" → rule fails (post-ADR-NNNN alone is not historical).

---
rule_id: G-12
title: Whitebox Quality Baseline
level: L0
view: development
status: active
governance_infra: true
kernel_cap: 700
kernel: |
  **Mature static-analysis tools form the first whitebox quality baseline: Maven MUST run SpotBugs, PMD, and Checkstyle through the `quality` profile; gate MUST interpret their reports using project semantics. High-confidence SpotBugs correctness/safety findings and low-dispute Checkstyle style/comment findings block. PMD maintainability findings are review triggers in v1. Generated code, third-party code, build outputs, docs examples, and fixtures are excluded or downgraded rather than hard-gated.**
---

# Rule G-12 — Whitebox Quality Baseline

## Kernel

Mature static-analysis tools form the first whitebox quality baseline: Maven MUST run SpotBugs, PMD, and Checkstyle through the `quality` profile; gate MUST interpret their reports using project semantics. High-confidence SpotBugs correctness/safety findings and low-dispute Checkstyle style/comment findings block. PMD maintainability findings are review triggers in v1. Generated code, third-party code, build outputs, docs examples, and fixtures are excluded or downgraded rather than hard-gated.

## Operationalisation

- Maven profile `quality` executes SpotBugs, PMD, and Checkstyle and writes XML reports under module `target/` directories.
- Gate Rule 121 validates report presence and interprets findings into hard failures or review-trigger summaries.
- CI MUST run `./mvnw -Pquality verify` before the architecture-sync gate.
- PMD complexity, length, and maintainability rules remain review triggers until a later governance decision promotes specific low-false-positive rules.

## Forbidden patterns

- Using raw tool default failure behavior as the project gate semantics.
- Promoting long-file, long-method, Javadoc coverage, line-width, magic-number, or aesthetic naming rules to hard gates in v1.
- Adding CVE, SBOM, Sonar, CodeQL, or broad supply-chain scanning under this rule.
- Hard-gating generated sources, third-party code, build outputs, docs examples, test fixtures, or gate self-test intentional bad examples.

## Enforcers

- E169 — `gate/check_architecture_sync.sh#whitebox_quality_reports`

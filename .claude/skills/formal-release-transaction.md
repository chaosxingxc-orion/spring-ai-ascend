---
name: formal-release-transaction
description: |
  Use this skill when preparing, reviewing, or publishing a formal L0 release
  note. It enforces the release transaction workflow: freeze a commit, generate
  evidence, validate authority refresh, separate current vs forward claims, and
  close touched recurring-defect families before a formal release claim.
scope: project
---

# /formal-release-transaction

## Purpose

This skill prevents another RC loop where cited defects are fixed but release
truth drifts across ADRs, CLAUDE.md, architecture-status, contract catalog,
OpenAPI, generated corpora, Java docs, and release notes.

## Required workflow

1. Freeze the candidate commit.
2. Generate an evidence bundle:

   ```bash
   python gate/lib/build_release_evidence.py --run-self-tests --include-maven-reports --output gate/release-ci-evidence/<release-id>.evidence.yaml
   ```

3. Validate the formal transaction:

   ```bash
   bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/<release-id>.evidence.yaml
   ```

4. Use `docs/governance/release-readiness/formal-release-note-template.en.md`
   for any release note that claims `formal_release: true`.
5. Copy generated metric values from the evidence bundle only. Do not hand-type
   ADR, rule, gate, self-test, graph, Maven, or recurring-family counts.
6. For every staged behavior, write a current-vs-forward claim:
   - current shipped behavior,
   - current verification,
   - forward behavior,
   - promotion trigger,
   - phrase that must not be claimed before promotion.
7. For every touched recurring family, write a closure record:
   - family id,
   - cited finding,
   - sibling surfaces checked,
   - closure result,
   - residual risk.
8. Regenerate or digest-check generated and shadow corpora. If a generated
   surface is not refreshed, remove it from the active authority surface or mark
   the release as not ready.

## Release decision rule

No evidence bundle means no formal release note. A corrective RC note may still
be published, but it must not claim final L0 closure.

## Files to load when needed

- `docs/governance/release-readiness/release-readiness.schema.yaml`
- `docs/governance/release-readiness/formal-release-note-template.en.md`
- `docs/governance/recurring-defect-families.yaml`
- `docs/governance/architecture-status.yaml`
- latest file from `docs/logs/releases/`

## Commands

```bash
python gate/lib/build_release_evidence.py --run-self-tests --include-maven-reports --output gate/release-ci-evidence/<release-id>.evidence.yaml
bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/<release-id>.evidence.yaml
```

Run the canonical architecture and Java verification commands after this
transaction check when preparing an actual release:

```bash
bash gate/check_parallel.sh
./mvnw clean verify
```

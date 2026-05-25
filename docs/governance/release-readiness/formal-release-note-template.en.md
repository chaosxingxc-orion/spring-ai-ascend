---
formal_release: true
evidence_bundle: gate/release-ci-evidence/<release-id>.evidence.yaml
release_candidate_commit: <full-sha>
status: formal-release-candidate
---

# <Release Name>

> This formal release note is valid only for the frozen commit and evidence
> bundle named in front matter. Do not hand-edit generated metric values.

## Release Decision

- Decision: <ship | hold | accepted-residual>
- Frozen commit: `<full-sha>`
- Evidence bundle: `gate/release-ci-evidence/<release-id>.evidence.yaml`
- Formal release validator: `bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/<release-id>.evidence.yaml`

## Generated Evidence

Paste or generate this table from `EvidenceBundle.baseline_comparison`.

| Metric | Baseline | Live | Match |
|---|---:|---:|---|
| active_engineering_rules | <generated> | <generated> | <generated> |
| active_gate_checks | <generated> | <generated> | <generated> |
| gate_executable_test_cases | <generated> | <generated> | <generated> |
| enforcer_rows | <generated> | <generated> | <generated> |
| adr_count | <generated> | <generated> | <generated> |
| maven_tests_green | <generated> | <generated> | <generated> |
| architecture_graph_nodes | <generated> | <generated> | <generated> |
| architecture_graph_edges | <generated> | <generated> | <generated> |
| recurring_defect_families | <generated> | <generated> | <generated> |

## Current-vs-Forward Claims

Every staged claim must map to a `CurrentForwardClaim` record.

| Subject | Current shipped behavior | Verified by | Forward behavior | Promotion trigger | Must not claim before |
|---|---|---|---|---|---|
| <subject> | <current> | <evidence> | <future> | <trigger> | <guardrail> |

## Recurring Family Closure

Every touched recurring family must map to a `DefectFamilyClosure` record.

| Family | Cited findings | Sibling surfaces checked | Closure result | Residual risk |
|---|---|---|---|---|
| <family-id> | <findings> | <surfaces> | <closed/accepted_residual/not_ready> | <risk text> |

## Authority Refresh

| Surface | Role | Freshness proof |
|---|---|---|
| <path> | <normative/workflow_evidence/generated/derived/historical> | <source/digest/command> |

## Verification Commands

```bash
bash gate/check_parallel.sh
./mvnw clean verify
python gate/lib/build_release_evidence.py --run-self-tests --include-maven-reports --output gate/release-ci-evidence/<release-id>.evidence.yaml
bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/<release-id>.evidence.yaml
```

## Residual Risk

State every `partial` or `monitoring` recurring family that remains relevant to
the release. If there is any accepted residual, this note must say why the
release can still proceed.

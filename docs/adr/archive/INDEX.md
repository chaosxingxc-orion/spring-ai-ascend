# Archived ADR Index

This file documents predecessor ADRs whose decisions have been consolidated into the active corpus (ADR-0068 and later) or into the Rule cards under `docs/governance/rules/`. The originals were superseded during the wave-based consolidation that culminated in the 2026-05-18 Wave 4 release; their bodies are not retained in this repository.

`docs/adr/*.yaml` files retain `extends:` / `supersedes:` references to these IDs as audit-trail metadata. Cross-referencing this index resolves them.

## Ranges

| Range | Wave / origin | Consolidated into |
|---|---|---|
| ADR-0159 | 2026-06-04 agent-runtime consolidation and service refounding draft | Archived after architecture review; current agent-runtime / agent-examples gateway sample direction is under `docs/logs/reviews/2026-06-04-agent-examples-a2a-runtime-registry-facade-proposal.cn.md` and ADR-0159 is not normative authority |
| ADR-0019 | Pre-rc3 SuspendSignal sealed-checked-variant design | ADR-0078 (s2c.spi package move) + Rule 46 kernel |
| ADR-0021–ADR-0027 | Pre-Phase-7 governance corpus (layered SPI taxonomy, atomicity contract, posture defaults) | Rules 25–32 + ADR-0068–ADR-0073 |
| ADR-0059–ADR-0067 | W2.x Phase 7 audit response (engine contract, hooks, S2C, evolution scope, sandbox subsumption) | Rules 43–48 + ADR-0068 + ADR-0079 (T2.B2 engine extraction) |

## How to read a dangling reference

When an active ADR yaml declares (for example) `extends: [ADR-0064]`, the canonical interpretation is:

1. The proposing change inherits the constraint envelope that ADR-0064 originally framed.
2. That constraint envelope now lives in one of the rule cards or active ADRs in the matching range above.
3. No predecessor-body lookup is required to evaluate the active ADR — the active text is self-contained.

## Why these are not reconstructed inline

The Wave 4 consolidation deliberately collapsed many redundant predecessor ADRs into rule cards (the `kernel:` scalar of each `rule-NN.md`) so the operating constraint surface is one source instead of many. Reconstructing the predecessor bodies would re-fragment that surface and reintroduce the drift Wave 4 was designed to eliminate. The IDs are preserved as lineage markers only.

## Policy

- New ADRs MAY reference archived predecessors in `relates_to:` or `extends:` for lineage.
- New ADRs MUST NOT reference archived predecessors in their normative body — quote the active rule card or active ADR instead.
- This index is the canonical resolution target for the Gate-Rule-69-adjacent dangling-ADR audit pattern.

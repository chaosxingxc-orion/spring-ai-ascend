---
level: L0
view: governance
status: active
authority: "documentation navigation; architecture authority remains architecture/workspace.dsl"
document_role: navigation
source_of_truth: false
---

# Documentation

This directory contains product, governance, contract, evidence, proposal, and
historical documentation. It is not the architecture-of-record. Architecture
truth starts at [`../architecture/workspace.dsl`](../architecture/workspace.dsl)
and the canonical architecture README at [`../architecture/README.md`](../architecture/README.md).

## Directory Roles

| Path | Role | Authority |
|---|---|---|
| `contracts/` | Runtime contract corpus: OpenAPI, envelopes, SPI-facing schemas, and contract catalog. | Authoritative for runtime promises once listed in `contracts/contract-catalog.md`. |
| `adr/` | Architecture Decision Records. | Authoritative for accepted decisions and their supersession history. |
| `governance/` | Rules, enforcers, status ledgers, and process controls. | Authoritative for governance when referenced by active rules. |
| `dfx/` | Design-for-X declarations by module. | Authoritative for DFX evidence status when linked from module metadata. |
| `logs/` | Review, release, and process evidence. | Evidence only; not architecture truth by itself. |
| `reviews/` | Review reports and analysis material. | Evidence or assessment; not architecture truth by itself. |
| `architecture/` | Draft delivery views, architecture proposals, and assessment material pending triage. | Draft/proposal only; must not override `architecture/`. |
| `archive/` | Historical or superseded material. | Historical only. |
| `harness/` | Harness material and validation support. | Authoritative only where referenced by accepted contracts or governance rules. |
| `competitive/` | Competitive research and comparison material. | Research/evidence only unless promoted by ADR or governance status. |

## Human Reading Path

1. Start with [`../README.md`](../README.md) for project scope and the high-level
   architecture reading path.
2. Use [`../architecture/README.md`](../architecture/README.md) for architecture
   authority, document roles, and L0/L1 navigation.
3. Use `contracts/`, `adr/`, `governance/`, and `dfx/` when a canonical
   architecture document points to them.
4. Use `docs/architecture/`, `logs/`, and `reviews/` for proposal context,
   evidence, or history only.

## AI Reading Path

1. Read generated facts first:
   [`../architecture/facts/generated/`](../architecture/facts/generated/).
2. Read the model:
   [`../architecture/workspace.dsl`](../architecture/workspace.dsl).
3. Read canonical prose:
   [`../architecture/docs/L0/ARCHITECTURE.md`](../architecture/docs/L0/ARCHITECTURE.md)
   and the relevant [`../architecture/docs/L1/`](../architecture/docs/L1/) module.
4. Read `contracts/`, `adr/`, and `governance/` only as supporting authority for
   specific claims.
5. Treat `docs/architecture/`, `logs/`, `reviews/`, and `archive/` as
   non-overriding context unless a canonical document explicitly promotes a
   specific artifact.

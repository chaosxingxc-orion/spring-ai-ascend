---
level: L0-TLD
TAG:
  - architecture-entry
  - reading-path
  - fact-governance
status: 架构事实
dependency:
  - L0-Top-Level-Design/README.md
  - L1-High-Level-Design/
  - L2-Low-Level-Design/
---

# Architecture

This directory stores accepted architecture facts for `spring-ai-ascend`.
Drafts, proposals, review notes, and archived historical material belong under
`docs/` until they are promoted.

The current branch does not keep a separate workspace authority system. Treat
the repository as two fact families:

- Architecture facts under `architecture/`.
- Code facts in source code, module metadata, tests, contracts, generated
  runtime evidence, and other verifiable implementation artifacts.

When architecture facts and code facts disagree, stop and raise the mismatch.
Do not silently treat draft material as accepted architecture.

## Directory Roles

| Path | Role | Edit Policy |
|---|---|---|
| `L0-Top-Level-Design/` | Top-level system design, 4+1 view map, module/state boundaries, constraints, governance, and vocabulary. | Accepted architecture facts only. |
| `L1-High-Level-Design/` | Module-level high-level design for accepted L0 domains. | Accepted module architecture only. |
| `L2-Low-Level-Design/` | Deep technical designs derived from accepted L0/L1 needs. | Accepted low-level design only. |

## Reading Path

1. Read `L0-Top-Level-Design/README.md`.
2. Read the relevant L0 documents in the order listed there.
3. Move to `L1-High-Level-Design/` for module-level impact.
4. Move to `L2-Low-Level-Design/` for technical designs, contracts, harnesses,
   or implementation-facing details.
5. Use `docs/` only as proposal, review, or archive context unless an
   architecture fact explicitly promotes the material.

## Promotion Rule

Draft material can be promoted into `architecture/` only after checking it
against:

- Current architecture facts.
- Code facts and module metadata.
- Accepted ADRs and contract facts.
- L0 vocabulary and state ownership rules.

Material that remains draft, proposal, review, or archive context stays in
`docs/`.

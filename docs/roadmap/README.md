# Deferred Roadmap (non-binding design intent)

This tree holds the **design records** (ADRs + L1 design docs) of capabilities that the
architecture-month designed but the running implementation (`agent-runtime`) never reached.
The dead code / SPIs / contracts for these were **deleted** (they were unused interfaces);
the **design intent is preserved here** as a non-binding roadmap.

- **Not governed.** This directory is excluded from the architecture gate's corpus scans
  (like `docs/logs/` and `docs/archive/`). Nothing here is a binding contract or a gate input.
- **Pulled, not pushed.** A capability here is implemented only when the running spine actually
  needs it — at which point its design is lifted back out, grounded, and re-bound to governance.
- Schemas (`*.v1.yaml`) for these capabilities were removed; see git history if a draft is needed.

| Subdir | Holds |
|---|---|
| `ADRs/` | ADRs for cut capabilities (`status: deferred`) |
| `L1-design/` | L1 design docs for cut modules/capabilities |

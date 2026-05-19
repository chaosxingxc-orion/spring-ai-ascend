---
rule_id: G-7
title: "Linux-First Dev Environment"
level: L0
view: scenarios
principle_ref: P-B
authority_refs: []
enforcer_refs: [E104]
status: active
kernel_cap: 8
kernel: |
  **All shell-driven operations (gates, builds, tests, generated artefacts, `git push`) MUST be verified on Linux — native, WSL2 (preferred), or WSL1 (fallback) — before merging to `main`. Git Bash for Windows is a debugging shim, not a verification environment. `docs/governance/dev-environment.md` is the canonical setup + verification guide. Measured 2026-05-18: WSL is 6–20× faster than Git Bash, AND surfaces platform-portability bugs that Win-only verification hides.**
---

## Motivation

On 2026-05-18 the team ran the gate in WSL1 Ubuntu for the first time. Two real production bugs surfaced that had been committed and pushed to `main` through multiple review cycles, invisible to Git Bash for Windows verification:

1. **Windows-absolute paths in YAML** (`D:/.claude/plans/...`) in `architecture-status.yaml`. Rule R-J.b (path existence) silently passed on Windows because `D:/...` resolves on NTFS; failed on Linux where it doesn't. Latent since PR-E1.
2. **CRLF vs LF line endings** in `gate/build_architecture_graph.py` output. Rule R-L (graph idempotency) passed on Git Bash because both reads + writes used CRLF; failed on WSL because the on-disk graph was CRLF and rebuild produced LF. Latent since Rule R-L was added.

Both bugs were the result of "verified on Windows ≠ verified". Beyond bug detection, the speedup is dramatic: same gate, same repo, **Git Bash 60ms per subprocess fork vs WSL1 13ms** (4.6× per-fork, 6–7× on actual gate wall-clock). Production runs on Linux; local dev should match.

## Details

- **WSL2** is preferred (needs Intel VT-x / AMD-V enabled in BIOS); produces ~20× speedup over Git Bash.
- **WSL1** is the fallback when BIOS virtualization cannot be enabled; ~6× speedup.
- **Git Bash** remains acceptable for one-off inspection (`git log`, `git status`, quick reads) but NEVER for verification or release artefacts.
- **Generated files** (graphs, NDJSON logs, build outputs) MUST be byte-identical between Linux and Windows builds — see Rule R-L + this rule's enforcer.
- **`git push`** should originate from a Linux shell when possible to avoid `core.autocrlf` regressions. Acceptable exception: pushing a commit that was *built and verified* in Linux, then pushed from any shell.

See `docs/governance/dev-environment.md` for full setup instructions (WSL2 enable, distro install, dev tools, repo location guidance).

## Cross-references

- Enforcer E104 — `gate/check_architecture_sync.sh#linux_first_dev_doc_present` (file existence + minimum-content check on `docs/governance/dev-environment.md`).
- Companion: Rule D-3.a (eol_policy) — LF on gate scripts; Rule G-7 generalises to all generated artefacts.
- Companion: Rule R-L (architecture_graph_idempotent) — the rule that caught the CRLF bug under WSL.
- Benchmark: `gate/lib/wsl_speed_probe.sh` — measures per-subprocess fork cost on the current shell. Run on any new dev host to verify the 5–20× WSL ratio.
- Empirical authority: PR `c6d53b9` (PR-E3.b + portability fixes) — the commit that documented the two latent bugs.

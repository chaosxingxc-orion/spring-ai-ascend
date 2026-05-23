# Contributing to spring-ai-ascend

This repository is **governance-gated**: a document-corpus consistency gate
(`gate/check_parallel.sh`) and a Maven quality gate run on every change to keep a
large, fast-moving codebase internally consistent. Most first-PR friction comes
from *not knowing the gate's expectations* — this guide front-loads them so your
PR merges on the first try.

> The canonical, always-current baseline counts (rules, enforcers, self-tests,
> graph nodes/edges) live in
> [`docs/governance/architecture-status.yaml#baseline_metrics`](docs/governance/architecture-status.yaml).
> Never hardcode these numbers anywhere else from memory — read them from there.

---

## The mergeability checklist (read this first)

1. **Rebase onto the latest `main` immediately before opening or updating a PR.**
2. **Never hand-edit generated artefacts** (`gate/rules/*.sh`, `docs/governance/architecture-graph.yaml`).
3. **Recompute governance counts against `main`** (the merge target), not your fork's branch point, and update the four baseline surfaces in lockstep.
4. **Run the gate on Linux/WSL**, not Windows Git Bash (Rule G-7).
5. **Touching a refresh signal?** Add a real `recurring-defect-families.yaml` content-diff (Rule G-9).
6. **New gate script?** Make it parallel-ready from day one (Rule G-10).
7. **Keep PRs scoped** — one concern per PR.
8. **Run `./mvnw -Pquality verify`** for production-Java changes (whitebox quality gate).

---

## 1. Environment

| Component | Expected |
|---|---|
| JDK | Temurin **21** (LTS) |
| Maven | use the wrapper: `./mvnw` (Linux/macOS) or `./mvnw.cmd` (Windows) — pinned to 3.9.x |
| Build host | **Windows** (`./mvnw.cmd`) — WSL Maven hits a Mockito/ByteBuddy self-attach failure |
| Gate host | **Linux / WSL** (Rule G-7) — Windows Git Bash mishandles symlinks and `python3` |
| Python | ≥ 3.10 with `pip install -r gate/requirements.txt` (for the architecture-graph builder) |

**China / restricted networks:** if `repo.maven.apache.org` times out on large
jars, add a domestic Central mirror to `~/.m2/settings.xml` (e.g. Aliyun
`https://maven.aliyun.com/repository/public`, `<mirrorOf>central</mirrorOf>`).
Leave `repo.spring.io/milestone` direct — Spring AI milestones are not on Central.

## 2. Branch & rebase discipline

`main` moves fast. A branch cut even hours ago can miss work that has already
landed, and re-deriving it causes conflicts.

```bash
git fetch upstream
git rebase upstream/main      # before opening AND before every update
```

Do not trust an old branch point. If your PR's base is many commits behind
`main`, rebase before asking for review.

## 3. The governance gate

The gate proves the document corpus is internally consistent at the current SHA
(names, paths, counts, contracts all agree). Run it on Linux/WSL:

```bash
bash gate/check_parallel.sh              # canonical parallel runner (~3 min on Linux)
bash gate/test_architecture_sync_gate.sh # self-tests
python3 gate/build_architecture_graph.py # regenerate the gitignored graph first
```

`gate/check_architecture_sync.sh` is the **canonical monolith**; the parallel
runner executes the same rules. A green run prints `GATE: PASS`.

## 4. Generated artefacts — never hand-edit

| Artefact | Generated from | Regenerate with |
|---|---|---|
| `gate/rules/*.sh` | `gate/check_architecture_sync.sh` (the monolith) | `bash gate/lib/extract_rules.sh` |
| `docs/governance/architecture-graph.yaml` | principle/enforcer/status/ADR inputs | `python3 gate/build_architecture_graph.py` |

Edit the **source** (the monolith, the inputs) and regenerate. A PR that
hand-edits `gate/rules/*.sh` will be overwritten on the next regen and will
conflict with every other PR. (This is Rule G-5's "the canonical monolith is
canonical".)

## 5. Numeric baselines — recompute against `main`, lockstep

If your change adds/removes a gate rule, an enforcer, a self-test, an engineering
rule, or graph nodes/edges, recompute the value **against the merge target**:

- `active_gate_checks` = `# Rule N — slug` header count in the monolith
- `active_engineering_rules` = `#### Rule` count in `CLAUDE.md`
- `enforcer_rows` = enforcer rows in `docs/governance/enforcers.yaml`
- `architecture_graph_nodes` / `_edges` = `python3 gate/build_architecture_graph.py --check --no-write`

Then update these four surfaces **in the same commit**, or numeric-truth gates fail:
`architecture-status.yaml#baseline_metrics`, its `allowed_claim`, `README.md`,
and `gate/README.md`. (A common first-PR bug is computing counts against a stale
fork base — they must match `main` post-merge.)

## 6. Defect-family freshness (Rule G-9)

When a commit touches an **architecture-refresh signal** — a new
`docs/adr/*.yaml`, a `baseline_metrics` change, a new `docs/logs/releases/*.md`,
a `#### Rule` heading change in `CLAUDE.md`, or an edited `docs/governance/rules/*.md`
card — it MUST also make a **real content change** to
`docs/governance/recurring-defect-families.yaml` (and keep
`recurring-defect-families.md` in id + `cleanup_status` parity). A no-op edit
(whitespace, a bare `last_updated:` bump) does not satisfy freshness.

Because `main` requires **squash-merge**, do all of this in your PR's commits;
the squash collapses them into one commit whose families-diff the gate compares
against `main`. Routine CI workflow bumps (`.github/workflows/`) are intentionally
**not** refresh signals.

## 7. Phase contracts & skills

`CLAUDE.md` is a kernel index; rule bodies load on demand via phase skills. When
working with an AI agent in this repo, invoke the matching phase skill at the
start (e.g. `/impl-mode` for production code, `/verify-mode` for gate/build work).
See the "Phase Entry" table in [`CLAUDE.md`](CLAUDE.md).

## 8. Code quality (whitebox gate)

Production-Java changes are checked by SpotBugs + PMD + Checkstyle via the Maven
`quality` profile:

```bash
./mvnw.cmd -Pquality verify     # generates target/*.xml reports; CI runs this before the gate
```

SpotBugs high-confidence findings and Checkstyle `severity=error` findings are
**blocking**; PMD findings are review triggers. Config lives under `config/`.

## 9. PR scope & merge

- **One concern per PR.** If you spot an unrelated latent bug while implementing,
  open it as its own small PR so it can land independently (Rule D-2 — surgical changes).
- `main` uses **squash-merge** (required for the G-9 freshness first-parent semantics).
- CI must pass: "Maven build + integration tests" and "Quickstart smoke (/v1/health)".

## 10. Where things live

| Path | What |
|---|---|
| `CLAUDE.md` | Governing principles + engineering-rule kernel index |
| `docs/governance/rules/`, `docs/governance/principles/` | Full rule / principle cards |
| `docs/governance/architecture-status.yaml` | Canonical baseline metrics (single source of truth) |
| `docs/adr/` | Architecture Decision Records |
| `docs/logs/reviews/` | Review responses / interaction records (front-matter optional) |
| `docs/logs/releases/` | Release notes |
| `gate/` | The architecture-sync gate (`gate/README.md` explains it) |
| `config/` | SpotBugs / PMD / Checkstyle rulesets |

Thanks for contributing!

---
level: L1
view: scenarios
status: active
authority: "ADR-0157 (EngineeringFrame Ontology / dual-track) + ADR-0161 (Card-over-DSL) + ADR-0154 (Fact-Layer Authority)"
---

# `architecture/mappings/` — explicit queryable understanding map

This directory holds the **explicit, queryable dual-track understanding map**: a
single readable projection that joins the three axes the architecture is built
on, so an AI (or a human) can answer *"what is this, why does it exist, and what
proves it"* about any FunctionPoint in one hop.

It is the surface named at
[`docs/governance/ai-reading-path.yaml`](../../docs/governance/ai-reading-path.yaml)
**step 5** (`demand_to_behavior_mapping`):
> *"Explicit queryable dual-track map (value/structure/join/evidence/decision/
> governance axes per FunctionPoint)."*

## The three axes it joins

| Axis | Chain | Upstream authority |
|---|---|---|
| **Value** | ProductClaim → Requirement (ISO 29148) → Feature → FunctionPoint | `architecture/features/features.dsl` (`requires`) |
| **Structure** | Module → EngineeringFrame → FunctionPoint | `architecture/features/engineering-frames.dsl` (`anchors`) |
| **Evidence** | FunctionPoint → Contract → Generated Fact → Gate | `architecture/facts/generated/*.json` |

Plus the derived **Feature → traverses → EngineeringFrame** reconciliation
(value-crosses-structure), per ADR-0157 §2.

## This is a READABLE INTERPRETATION LAYER, not an authority

The map **invents no ID and no relationship**. Every ID it carries is declared
upstream and is verified to resolve there. The authority cascade — and the
conflict-resolution order — is:

```
generated facts  >  DSL  >  Frame Card / prose  >  this map
(architecture/facts/generated/*.json)
   > architecture/features/{engineering-frames,features,function-points}.dsl
      > architecture/docs/L1/frames/*.md
         > architecture/mappings/ai-understanding-map.yaml   (lowest)
```

If the map and an upstream surface disagree, the upstream surface wins and the
map is corrected. The map never edits or overrides the DSL or the facts.

> **Boundary note.** This directory is **authored interpretation**. The reconcile
> step owns the shared authority surfaces (`architecture-status.yaml`, the gate
> wiring, `enforcers.yaml`, the profile, `engineering-frames.dsl`, and everything
> under `architecture/facts/generated/`). Editing those is out of scope here.

## File inventory

| File | Role | Authored or generated |
|---|---|---|
| [`ai-understanding-map.yaml`](ai-understanding-map.yaml) | The structured, queryable map — modules, frames, features, per-FunctionPoint facets, and the traversal reconciliation | AUTHORED (interpretation) |
| [`ai-understanding-map.md`](ai-understanding-map.md) | The human/RAG-readable prose rendering of the same content | AUTHORED (interpretation) |
| [`schema/ai-understanding-map.schema.yaml`](schema/ai-understanding-map.schema.yaml) | JSON/YAML schema — the structural contract for the YAML map | AUTHORED (`inline` source under Rule G-13) |
| `README.md` | this file | AUTHORED |

The `.md` and `.yaml` are kept consistent (same IDs, same statuses). The schema
is the shape contract for the YAML.

## Per-FunctionPoint facets (the queryable unit)

Each `function_points[]` record in the YAML carries six facets:

- **value** — `required_by_features` + `requirement` (which value-thread drives it)
- **structure** — `anchored_by_frames` + `owner_module` (its structural home)
- **join** — `channel` + `actor` + `trigger` (where value meets structure)
- **evidence** — `contract_op` / `code_symbol` / `test` **generated fact IDs**
- **decision** — `source_adr` (verbatim from the DSL) + `resolution` (fact / disk / unresolved)
- **governance** — optional rules/enforcers pointers (absence is not a defect)

Empty evidence/value arrays mean *"no fact / no value-thread of that kind for
this FunctionPoint yet"* — the platform is mid-build, and absence is recorded
honestly rather than invented.

## ID formats (mirrored from the fact layer)

| Kind | Format | Resolves in |
|---|---|---|
| Feature | `FEAT-<SCREAMING-KEBAB>` | `features.dsl` |
| EngineeringFrame | `EF-<SCREAMING-KEBAB>` | `engineering-frames.dsl` / `features.dsl` |
| FunctionPoint | `FP-<SCREAMING-KEBAB>` | `function-points.dsl` |
| Module | short name | `genModule_*` / SAA Module `saa.owner` |
| Code symbol | `code-symbol/<kebab-fqn>` | `architecture/facts/generated/code-symbols.json` |
| Test | `test/<kebab-fqn>` | `architecture/facts/generated/tests.json` |
| Contract op | `contract-op/<kebab-op-id>` | `architecture/facts/generated/contract-surfaces.json` |
| ADR | `adr/<4-digit>-<kebab-slug>` | `architecture/facts/generated/adrs.json` (ADR-0068+) |

## Validation recipe

The map is verifiable against the schema **and** against the upstream authority
chain (every cited ID must resolve; the value/structure facets must be
symmetric with the `requires`/`anchors` edges; the traversal reconciliation must
re-derive). Run the validation on Linux/WSL per Rule G-7:

```bash
python3 - <<'PY'
import json, yaml, pathlib, copy, jsonschema
root = pathlib.Path(".")
m = root/"architecture/mappings/ai-understanding-map.yaml"
s = root/"architecture/mappings/schema/ai-understanding-map.schema.yaml"
data = yaml.safe_load(m.read_text(encoding="utf-8"))
sch = copy.deepcopy(yaml.safe_load(s.read_text(encoding="utf-8")))
for k in ("schema_version","spec_url"): sch.pop(k, None)
jsonschema.validate(data, sch)
# every FEAT/EF/FP id must match the DSL; every fact id must resolve in
# architecture/facts/generated/*.json; the derivation join must recompute.
print("schema OK — extend with the DSL/fact cross-checks before promotion")
PY
```

A full cross-check (DSL ID parity, fact-ID resolution, facet symmetry,
derivation re-derivation) is the gate the reconcile step wires when it flips this
surface from `presence: planned` to `present` in
[`docs/governance/ai-reading-path.yaml`](../../docs/governance/ai-reading-path.yaml).

## Where this sits in the reading path

| Step | Surface | This map's relation |
|---|---|---|
| 4 | `architecture/features/engineering-frames.dsl` + Frame Cards | **structure axis source** this map mirrors |
| 5 | `architecture/features/features.dsl` + `function-points.dsl` + **this map** | the explicit join this map *is* |
| 6 | `docs/contracts/` + `architecture/facts/generated/` + gate | the **evidence** this map cites by fact ID |

## Authority

- ADR-0157 — EngineeringFrame Ontology (dual-track value/structure axes; `anchors` + `traverses`)
- ADR-0158 — transport-agnostic EnginePort boundary (EF-ENGINE-PORT / EF-ORCHESTRATION-SPI)
- ADR-0161 — Card-over-DSL + EngineeringFrame package-cluster anchor (readable layers invent nothing)
- ADR-0156 — Product authority + traceability chain
- ADR-0154 — Fact-Layer Authority (evidence cited as fact IDs; facts never hand-edited)
- `docs/governance/ai-reading-path.yaml` — step 5 declares this surface's role
- `architecture/profile/relationship-types.yaml` — `requires` / `anchors` / `traverses` vocabulary

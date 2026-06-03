# Asset Disposition — Count Deltas

Branch: `governance/ai-native-asset-disposition` · 2026-06-04

Provenance ledger only. `architecture-status.yaml#baseline_metrics` is reconciled
at **W-R by MEASUREMENT** (canonical counters in `gate/lib/build_release_evidence.py`:
`count_gate_rules`, `count_enforcers`, `count_adrs`, graph regen), **never hand-summed**.
This file exists so the reconcile is auditable, not authoritative.

## Confirmed pre-existing drift already on `main`
The team retired Rule 82 (`baseline_metrics_single_source`, the baseline cop), so
hand-maintained counts drifted unchecked and #127 merged carrying them:

| metric | baseline_metrics says | canonical measures | method |
|---|---|---|---|
| active_gate_checks | 35 | **32** | `count_gate_rules` (awk) |
| enforcer_rows | 88 | **41** | `count_enforcers` |

These are independent of the disposition waves below; both reconciled at W-R.

## Per-wave deltas (appended as executed)
- W2 true-orphan archive: artifacts not baseline-counted → delta 0
- W3 near-orphan delink+archive: delta 0
- W4 semi-coupled retire: (records on execution — principle/card retirements)
- W5/W7/W9 design+doc: additive → delta 0
- W8 shared-logic: phase_contracts 5→1, phase_loading_skills 6→1

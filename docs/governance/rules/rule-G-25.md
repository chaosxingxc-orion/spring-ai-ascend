---
rule_id: G-25
title: "Tier-1 Non-English / Mojibake Lint"
level: L1
view: scenarios
principle_ref: P-C
authority_refs: [ADR-0156]
enforcer_refs: [E190]
status: active
scope_phase: design
kernel_cap: 8
governance_infra: true
scope_surfaces:
  - gate/always-loaded-budget.txt
  - gate/lib/check_tier1_non_english.py
kernel: |
  Every file with a non-zero byte ceiling in `gate/always-loaded-budget.txt` (the always-loaded Tier-1 set) MUST be free of (a) CJK code points `[U+4E00..U+9FFF]` and (b) common UTF-8/GBK mojibake markers (`U+FFFD` plus the literal double-decode sequences). Fails closed. The checker MUST report line:col + byte offset ONLY and MUST NOT echo the offending non-English text into its output, so the gate log never embeds non-English source.
---

# Rule G-25 — Tier-1 Non-English / Mojibake Lint

## What

The always-loaded Tier-1 set is injected into every model context. The
collaboration kernel mandates English-only instructions. This rule guards the
Tier-1 surfaces against CJK ideographs and mojibake (corrupted UTF-8/GBK
double-decodes).

## Why

Closes the 2026-05-29 review finding P1-3. Non-English text or mojibake in an
always-loaded surface is ingested by the model every session, degrading
instruction fidelity and leaking corrupted bytes. The Tier-1 set is the highest
blast-radius surface, so it is checked first and fails closed.

## How it works

`gate/lib/check_tier1_non_english.py` reads the non-zero-budget entries from
`gate/always-loaded-budget.txt`, then scans each surface for CJK code points and
mojibake markers. A missing budget file, a missing surface, or a budget that
yields zero in-scope files is fail-closed.

## Reporting discipline

The checker emits `NON-ENGLISH: <relpath>:<line>:<col> byte=<offset>
kind=cjk|mojibake`. It deliberately never prints the offending characters, so
the gate log stays English-only — a requirement called out by the review.

## Test fixtures

  - INVALID: a synthetic always-loaded file with a CJK char fails.
  - INVALID: a synthetic file with a U+FFFD marker fails.
  - VALID  : clean English passes.

## Cross-references

  - CLAUDE.md kernel — "Translate all instructions into English before any model call."
  - Rule G-19 — Auto-Load Tier Integrity (defines the Tier-1 budget)

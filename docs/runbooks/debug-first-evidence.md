# Evidence-First Debug Sequence

**Authority:** [`docs/governance/rules/rule-D-3.md`](../governance/rules/rule-D-3.md). **Audience:** humans and AI agents debugging a failed Run, a regressed Maven test, a failed gate self-test, or any Rule D-5 self-audit finding.

## Why this runbook exists

When an integration test fails, the natural reflex is to open `ARCHITECTURE.md`, find the constraint that describes the failing surface, and reason from the contract. This makes specs into a Narrative Shield: the AI cites the contract to defend the broken code. Rule D-3.b re-orders the steps — **evidence first, spec consultation afterwards** — so the spec is interpreted against the failure rather than the other way around.

## The sequence (6 steps, in order)

### Step 1 — Failing test FQN + first 5 stack frames

```bash
# Locate the failure in the latest Maven report.
find . -name "TEST-*.xml" -newer pom.xml | xargs grep -l "<failure" | head
```

Open the file. Record verbatim:

- The fully-qualified test class + method: `com.huawei.ascend.service.runtime.runs.RunStateMachineLibraryTest#illegal_transition_throws`.
- The first 5 stack frames (the ones that name your source code, not framework internals).

If the failure is from a gate self-test, the FQN form is `bash:test_rule79_runbook_present`. Capture the corresponding `FAIL [...]` line from `bash gate/test_architecture_sync_gate.sh`.

### Step 2 — Trace ID (W3C, 32 lowercase hex)

```bash
# For a real failed Run, the trace id is on the request log line.
grep -E '"runId":"<uuid>"' agent-service/log/app.json | tail -1 | jq -r '.traceId'
```

For library-mode tests with no tracing, substitute the JUnit method id (`#<methodName>`). Capture verbatim.

### Step 3 — MDC slice

For a Run-driven failure, capture the MDC dictionary from the structured log line *immediately preceding* the failure:

```json
{
  "runId":     "<uuid>",
  "tenantId":  "<tenant>",
  "fromStatus": "RUNNING",
  "toStatus":   "SUSPENDED",
  "attemptId":  1
}
```

For a test-only failure (no Run involved), substitute `expected:` vs `actual:` from the assertion. Verbatim.

### Step 4 — Raw error message

The literal exception message including line numbers. Do not paraphrase. If the message is long, capture the full first paragraph + the inner-most `caused by` line.

### Step 5 — RunStatus transition history

When the failure involves a Run, list every transition that preceded the failure:

```sql
SELECT updated_at, status, attempt_id
FROM run_history
WHERE run_id = '<uuid>'
ORDER BY updated_at ASC;
```

For W0 in-memory runtime (no SQL), iterate `InMemoryRunRegistry.findByRunId(runId)` and print the result.

If the failure surface has no Run (a unit test, a gate script), capture the conceptual analogue: the test's setup steps in order, or the gate script's per-rule pass/fail trace from `gate/log/runs/<sha>_<ts>/per-rule.ndjson`.

### Step 6 — THEN consult specs

Only after steps 1–5 are recorded in the finding may you read:

- `ARCHITECTURE.md` — system boundary, §4 constraints.
- `docs/governance/rules/*.md` — engineering rules.
- `docs/adr/*.yaml` — decision records.
- `CLAUDE.md` — governing principles.

When you cite a constraint, you MUST quote the evidence from steps 1-5 that maps to it. A finding that names a constraint without quoting the evidence violates Rule D-5 (self-audit ship gate) and Rule D-3.b (evidence-first sequence).

## Template (paste into the finding doc)

```markdown
## Evidence (Rule D-3.b)

1. **Failing FQN / location**: <FQN#method or bash:test_X>
2. **Trace ID / method id**: <32-hex or #methodName>
3. **MDC slice / expected-vs-actual**:
   ```
   <verbatim MDC json or assertion diff>
   ```
4. **Raw error**:
   ```
   <verbatim exception text + stack head>
   ```
5. **Transition history / setup trace**:
   ```
   <RunStatus chronology or test-setup chronology>
   ```

## Interpretation (only after evidence is captured)

<spec citations + reasoning>
```

## What this runbook is NOT

- It is not a prohibition on reading specs. The spec is consulted in step 6.
- It is not a substitute for Rule D-1 (Root-Cause + Strongest-Interpretation). Rule D-3.b produces the evidence; Rule D-1 chooses which interpretation of the evidence survives.
- It is not a recipe for fixing the bug. It is a recipe for ensuring the bug is *seen* before it is *explained*.

## Cross-references

- [`docs/governance/rules/rule-D-3.md`](../governance/rules/rule-D-3.md) — the rule card with the kernel statement and gate enforcement details.
- [`docs/governance/rules/rule-D-1.md`](../governance/rules/rule-D-1.md) — Root-Cause + Strongest-Interpretation; the reasoning that runs on top of the evidence captured here.
- [`docs/governance/rules/rule-D-5.md`](../governance/rules/rule-D-5.md) — Self-Audit ship gate; Rule D-3.b evidence is a precondition for any Rule D-5 finding.
- [`docs/logs/reviews/spring-ai-ascend-beyond-sdd-en.md`](../logs/reviews/spring-ai-ascend-beyond-sdd-en.md) — the architecture review that prompted Rule D-3.b.
- [`docs/logs/reviews/spring-ai-ascend-beyond-sdd-response.en.md`](../logs/reviews/spring-ai-ascend-beyond-sdd-response.en.md) — response to the review explaining the accept/reject decisions.

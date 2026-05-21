---
level: L0
view: scenarios
status: draft
authority: "Derived from trustworthy prompt design, threat model, and module responsibility review"
---

# AI Risk Control Map

## Purpose

This document maps AI-specific risk families to L0 ownership, L1 module
controls, and L2 evidence. It prevents AI risk from remaining generic guidance.

## Risk Families

| Risk | L0 System Rule | L1 Control Owner | L2 Evidence |
|---|---|---|---|
| Prompt injection | Untrusted context cannot modify policy, permissions, system prompts, tool scope, or release verdicts | `agent-service`, `agent-middleware` | input classification tests, prompt boundary tests, hook enforcement tests |
| Tool poisoning | Tool metadata/output is untrusted until validated and authorized | `agent-middleware`, sandbox future owner | tool allowlist tests, permission-envelope checks, audit assertions |
| Context leakage | Tenant/model/tool/memory context must not cross scope without explicit delegation | `agent-service`, graph memory starter, `agent-bus` | tenant isolation tests, redaction tests, memory scope tests |
| Model/provider fallback bypass | Fallback cannot weaken policy, audit, tenant, budget, or safety controls | `agent-execution-engine`, `agent-service` | engine strict-match tests, fallback-denial tests |
| Hallucinated code/config | Model-generated config/code is proposal data, not authority | governance/review flow | review checklist, schema validation, no direct privileged write |
| Cost exhaustion | Run, tenant, skill, model, and tool usage must have budgets or saturation behavior | `agent-service`, `agent-bus`, future skill scheduler | quota tests, saturation tests, metrics |
| Feedback poisoning | Evolution input must be opt-in, classified, retained, and poison-checked | `agent-evolve` | export-control tests, poisoning regression corpus |
| Unsafe memory write | Business knowledge ownership must remain client-side unless delegated | graph memory starter, `agent-service` | DelegationGrant tests, placeholder preservation tests |
| Audit omission | High-risk AI decisions must emit immutable or tamper-evident audit | `agent-service`, `agent-middleware` | audit event tests, release evidence |

## Module Exposure Map

| Module | AI Risk Exposure | Required Control |
|---|---|---|
| `agent-client` | untrusted user intent, business rules, cursor/cancellation | preserve context without gaining policy authority |
| `agent-bus` | cross-plane request/callback routing, mailbox/backpressure | preserve tenant/correlation, prevent ordering and callback drift |
| `agent-service` | admission, Run lifecycle, model/tool orchestration, memory SPI | classify input, enforce tenant, audit state transitions, fail closed |
| `agent-execution-engine` | engine selection, envelope matching, orchestration SPI | strict matching, no unsafe fallback, typed failure mapping |
| `agent-middleware` | policy/audit/telemetry hooks | mandatory high-risk hook path, deterministic outcome |
| `agent-evolve` | offline feedback and improvement | opt-in, retention, poison detection, no production-path authority |
| `graphmemory-starter` | optional memory adapter | disabled by default, tenant-scoped, ownership/delegation-aware |

## Required Red-Team Scenarios

1. Prompt asks the system to ignore tenant, policy, or tool restrictions.
2. Tool output includes instructions to modify system prompt or credentials.
3. Model output attempts to create shell/SQL/file actions from raw prompt text.
4. Context from tenant A attempts to enter tenant B memory or trace.
5. Model fallback tries to bypass engine strict matching or audit.
6. Repeated Run creation attempts to exhaust idempotency, queue, token, or skill
   budgets.
7. Evolution export contains poisoned or unauthorized business facts.

## Release Rule

If an L2 change introduces one of these risk families and lacks a test,
red-team note, or explicit deferred trigger, the release verdict should be
`BLOCKED_AI_EVIDENCE_GAP`.


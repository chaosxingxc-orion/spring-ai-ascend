---
review_kind: post-corrective-architecture-review
reviewer_role: "senior Java microservices + agent-platform architect (independent)"
target_release: docs/logs/releases/2026-05-25-l0-rc51-agentic-completeness.en.md
target_commit_reviewed: 165e0b6e8b66547d2483e0a7029a36a13a856ed1
target_ci_run: https://github.com/chaosxingxc-orion/spring-ai-ascend/actions/runs/26408720888
verdict: do-not-close-rc
affects_level: L0
affects_view: development, logical, process
blocking_findings:
  - P0-1
  - P0-2
  - P0-3
  - P0-4
related_release_notes:
  - docs/logs/releases/2026-05-25-l0-rc49-agentic-contract-surface-corrective.en.md
  - docs/logs/releases/2026-05-25-l0-rc50-codegraph-nodegraph-supplement.en.md
  - docs/logs/releases/2026-05-25-l0-rc51-agentic-completeness.en.md
related_adrs:
  - ADR-0129
  - ADR-0132
  - ADR-0133
  - ADR-0134
  - ADR-0135
related_rules:
  - Rule D-9
  - Rule R-D
  - Rule G-2
  - Rule G-8
  - Rule G-9
---

# L0 rc51 Agentic-Completeness Architecture Review

## 1. Executive verdict

**Do not close the RC yet.** The rc51 wave substantially improves the
agentic contract surface, and the latest GitHub CI run is green at
`165e0b6e8b66547d2483e0a7029a36a13a856ed1`. However, the current L0
baseline still has blocking gaps in the contract, authority, and
constraint system. The main issue is not "missing more components" in
the abstract. The issue is that several newly added rc51 contracts do
not compose cleanly with each other, while the governance gates report
green even when the release transaction and rule text are not actually
closed.

The architecture team should treat this as an **rc52 corrective wave**,
not as a formal release closure.

## 2. Review scope and method

I reviewed the current repository state at main commit
`165e0b6e8b66547d2483e0a7029a36a13a856ed1` with the following scope:

- Latest release logs: rc49, rc50, and rc51.
- Latest review response: rc49 corrective response and rc50 post-closure
  senior-architect review.
- New rc51 ADRs: ADR-0129 through ADR-0135.
- New/updated contract YAML: `model-streaming.v1.yaml`,
  `structured-output.v1.yaml`, `prompt-template.v1.yaml`,
  `chat-advisor.v1.yaml`, `memory-store.v1.yaml`, and
  `model-invocation.v1.yaml`.
- Java SPI surfaces for model, advisor, memory, prompt, skill, agent,
  planning, retrieval, and vector contracts.
- Governance authority surfaces: `CLAUDE.md`,
  `docs/contracts/contract-catalog.md`, root/module `ARCHITECTURE.md`,
  `docs/governance/architecture-status.yaml`, rule cards, and the
  executable architecture-sync gate.

Verification performed:

```bash
bash gate/check_architecture_sync.sh
/usr/local/bin/gh run list --branch main --limit 5 --json databaseId,headSha,status,conclusion,displayTitle,url
```

Observed result: the architecture-sync gate passes, and GitHub CI is
green for run `26408720888`. The findings below therefore focus on
latent contract and governance defects that the current green gates do
not catch.

## 3. What rc51 got right

The strategic direction is sound:

- The platform remains Spring AI canonical without exposing Spring AI
  types as the public SPI.
- `SkillKind` remains unified; no unnecessary `Tool` / `Skill` split was
  introduced.
- `AgentSession` as a projection over `(tenantId, conversationId)` is a
  good non-SPI decision.
- The new prompt, structured-output, advisor, streaming, and
  conversation-memory surfaces are the right category of L0 contract
  work.

I did not find a reason to reject the overall L0 agent-platform
direction. The blockers are in closure quality, cross-contract
composition, and authority enforcement.

## 4. P0 findings

### P0-1 - rc51 is not a valid formal release closure

**Evidence**

- `docs/logs/releases/2026-05-25-l0-rc51-agentic-completeness.en.md:1-22`
  has no `formal_release: true` and no `evidence_bundle` front-matter.
- The same note claims a shipped decision, but says a follow-on rc52 will
  bind formal evidence at lines 41-46.
- The baseline table still says `Maven tests | >=423 | pending mvn verify`
  at line 97.
- The residual-risk section says no accepted residual blocks
  publication, while also saying the candidate SHA and evidence bundle
  are intentionally deferred to rc52 at lines 320-323.
- `gate/release-ci-evidence/2026-05-25-l0-rc51-agentic-completeness.evidence.yaml:3-11`
  records commit `3b2986cdd14c7b5d9988f17f649558cb254d3df5`,
  `dirty: true`, `formal_release: false`, and `evidence_bundle: null`.
- The reviewed main tip is `165e0b6e8b66547d2483e0a7029a36a13a856ed1`,
  not `3b2986cdd14c7b5d9988f17f649558cb254d3df5`.

**Why this blocks closure**

A release note cannot both close the RC and defer the formal release
transaction to the next RC. This reopens the same defect family that
rc49 was meant to close: evidence is narrative-first rather than
candidate-commit-first.

**Required correction**

- Publish an rc52 formal corrective release note with:
  - `formal_release: true`
  - an `evidence_bundle` front-matter field
  - a `release_candidate_commit` equal to the actual frozen candidate
    commit
  - generated evidence with `repository.dirty: false`
  - `latest_release.formal_release: true`
  - `latest_release.evidence_bundle` pointing back to the rc52 evidence
    bundle
- Remove all `pending`, `to be run`, and `deferred to rc52` language from
  any current release note that claims shipment.
- Strengthen `gate/lib/check_formal_release_transaction.py` so it fails
  when the provided evidence bundle records a dirty repository, a commit
  different from the latest release front-matter candidate, or a latest
  release that is not formal.

### P0-2 - Streaming and advisor contracts do not compose

**Evidence**

- `ModelGateway` now has both `invoke(...)` and `stream(...)`
  (`agent-middleware/src/main/java/com/huawei/ascend/middleware/model/spi/ModelGateway.java:31-68`).
- `ChatAdvisor` only intercepts `ModelGateway.invoke(...)`
  (`agent-middleware/src/main/java/com/huawei/ascend/middleware/advisor/spi/ChatAdvisor.java:40-52`).
- ADR-0132 explicitly admits the residual: streaming-aware advisors are
  deferred and may need a sibling SPI
  (`docs/adr/0132-chat-advisor-spi.yaml:97-104`).
- The rc51 release note nevertheless marks both streaming and
  ChatAdvisor gaps closed (`docs/logs/releases/2026-05-25-l0-rc51-agentic-completeness.en.md:72-75`)
  and claims Audience B no longer imports Spring AI directly at
  lines 304-310.

**Why this blocks closure**

The most important chat user experience is streaming chat with memory,
redaction, retrieval augmentation, policy checks, cost attribution, and
response shaping. rc51 closes `invoke(...)` advisors and streaming as
separate primitives, but does not define how advisors wrap streaming.
At W2, implementers must either:

- bypass `ChatAdvisor` for streaming, breaking the stated Audience B
  extension model, or
- invent an implementation-local streaming advisor contract, breaking L0
  portability.

This is not over-design. It is a missing composition rule between two
contracts that rc51 itself introduced as L0 closure items.

**Required correction**

Choose one L0 contract shape and document it consistently:

- Option A: add a streaming advisor method, for example
  `default Stream<ModelResponseChunk> aroundStream(AdvisedRequest,
  StreamingAdvisorChain)`, with default throw at L0.
- Option B: declare that `ChatAdvisor.aroundCall(...)` applies only to
  non-streaming calls and add a separate explicit `StreamingChatAdvisor`
  SPI.
- Option C: make `ModelGateway.stream(...)` internally lower to the same
  advisor chain and define terminal-response mutation rules.

The chosen shape must update ADR-0129, ADR-0132, `chat-advisor.v1.yaml`,
`model-streaming.v1.yaml`, Java SPI, quickstart, and carrier tests in
one wave.

### P0-3 - `ConversationMemory extends MemoryStore<String, ConversationTurn>` is self-contradictory

**Evidence**

- `ConversationMemory` says the key is a conversation id and the value
  is an ordered list of message turns
  (`agent-middleware/src/main/java/com/huawei/ascend/middleware/memory/spi/ConversationMemory.java:8-12`).
- The actual type is `MemoryStore<String, ConversationTurn>`
  (`ConversationMemory.java:34`).
- Inherited `MemoryReader.read(String tenantId, K key)` therefore returns
  `Optional<ConversationTurn>` for a conversation id
  (`MemoryReader.java:15-24`).
- Inherited `MemoryWriter.write(String tenantId, K key, V value, ...)`
  therefore overwrites a single `ConversationTurn` for a conversation id
  (`MemoryStore.java:18-21` plus `MemoryWriter` contract).
- The new ergonomic methods operate on `List<Message>`, not
  `List<ConversationTurn>` (`ConversationMemory.java:50` and
  `ConversationMemory.java:64`).

**Why this blocks closure**

The type hierarchy says "conversation id -> one turn"; the prose says
"conversation id -> ordered turn history"; the ergonomic methods say
"conversation id -> messages without observedAt/tokenCount." A W2
implementation can satisfy the Java type while violating the prose, or
satisfy the prose while treating inherited `read/write` as unusable.

That is a public SPI ambiguity, not an implementation detail. It will
fragment memory implementations and make conformance testing impossible.

**Required correction**

Pick one semantic model:

- If the store key is the conversation id, make the value a conversation
  window, for example `MemoryStore<String, ConversationWindow>` where
  `ConversationWindow` owns `List<ConversationTurn>`.
- If the value is a single turn, make the key a turn key, for example
  `ConversationTurnKey(tenantId, conversationId, sequence)` or
  `(conversationId, turnId)`, and define scan ordering.
- If conversation memory is not naturally a `MemoryStore<K,V>`, do not
  extend `MemoryStore`; instead make it a dedicated SPI that composes a
  private backing store.

Also align `addMessages`, `getMessagesUpToBudget`, and
`summariseAndCompact` on whether the public unit is `Message`,
`ConversationTurn`, or a window object.

### P0-4 - Tool-loop termination depends on an unenforced string enum

**Evidence**

- `docs/contracts/model-invocation.v1.yaml:36-43` says
  `finishReason` is an enum with values `stop`, `length`,
  `tool_calls`, `content_filter`, `other`.
- `docs/contracts/model-invocation.v1.yaml:80-98` uses that enum to
  define tool-call loop termination.
- `ModelResponse` exposes `finishReason` as a plain `String` and only
  checks non-null (`agent-middleware/src/main/java/com/huawei/ascend/middleware/model/spi/ModelResponse.java:27-41`).

**Why this blocks closure**

ADR-0134's loop semantics depend on exact finish-reason values. If the
Java carrier accepts arbitrary strings, the loop contract is not actually
portable: one adapter can emit `tool_calls`, another can emit
`TOOL_CALLS`, `function_call`, `stop_sequence`, or provider-native text,
and all still compile.

**Required correction**

- Introduce `ModelFinishReason` as an enum or sealed status type in the
  model SPI.
- Change `ModelResponse.finishReason` to that type, or add a strict
  factory/parser that normalizes provider-native values before they
  cross the SPI boundary.
- Update `model-invocation.v1.yaml`, ADR-0121, ADR-0134, tests, and
  Spring AI adapter shells accordingly.

## 5. P1 findings

### P1-1 - SPI-purity authority text and actual SPI imports conflict

**Evidence**

- The contract catalog says all SPI packages import only `java.*` plus
  same-spi-package siblings
  (`docs/contracts/contract-catalog.md:16-24`).
- Root architecture says SPI packages import `java.*`,
  same-spi-package siblings, and a documented cross-SPI allowlist, but
  lists only two allowlist entries
  (`ARCHITECTURE.md:346-358`).
- `agent-middleware/ARCHITECTURE.md:73-79` still says
  `com.huawei.ascend.middleware.spi.*` imports only `java.*` and own SPI
  siblings.
- rc51 `AdvisedRequest` imports `ModelInvocation` from
  `middleware.model.spi`
  (`agent-middleware/src/main/java/com/huawei/ascend/middleware/advisor/spi/AdvisedRequest.java:1-31`).
- `ConversationMemory` imports `Message` from `middleware.model.spi`
  (`ConversationMemory.java:1-5`).

**Why this matters**

The new cross-SPI dependencies are probably architecturally legitimate,
but they are not represented in the canonical allowlist. The rule text,
catalog text, root architecture, module L1 architecture, ArchUnit test,
and code no longer describe the same constraint.

**Required correction**

- Define the actual SPI-purity rule in one place: strict same-package
  purity, or narrow cross-SPI allowlist.
- If cross-SPI imports are allowed, add explicit allowlist entries for
  advisor -> model and memory -> model, with ADR authority and a size cap.
- Update `SpiPurityGeneralizedArchTest` or add a gate helper so the
  allowlist is executable, not just prose.
- Refresh `docs/contracts/contract-catalog.md`, root `ARCHITECTURE.md`,
  `agent-middleware/ARCHITECTURE.md`, and templates in the same wave.

### P1-2 - Rule D-9 is stricter than its executable gate

**Evidence**

- Rule D-9 says production code and inline comments must not carry
  version metadata or ADR pointers; the rule card explicitly says ADR
  citations belong in commit messages and rule cards, not inline
  (`docs/governance/rules/rule-D-9.md:19-20` and
  `docs/governance/rules/rule-D-9.md:84-90`).
- The gate regex only catches a narrow subset:
  `per ADR-NNNN`, `rc<N> Wave <number>`, findings, and ticket references
  (`gate/check_architecture_sync.sh:6505-6586`).
- rc51 production Java contains many unblocked variants, for example
  `Authority: ADR-0132` in
  `ChatAdvisor.java:7-10`, `Authority: ADR-0129` in
  `ModelGateway.java:48-49`, and `W2 LLM gateway wave` in
  `ModelGateway.java:70-74`.
- `bash gate/check_architecture_sync.sh` still reports
  `PASS: no_version_log_metadata_in_code`.

**Why this matters**

The project has a rule that appears strict in CLAUDE/rule-card text but
is materially weaker in CI. That creates a false sense of closure and
encourages new waves to add more inline governance archaeology to Java
code.

**Required correction**

- Either relax Rule D-9 text to allow authority citations in SPI Javadocs,
  or strengthen the gate to match the current rule text.
- If authority citations are allowed only for public SPI Javadocs, record
  that as an explicit exception with a bounded pattern.
- If they are not allowed, scrub the new rc51 production code and existing
  non-grandfathered Java surfaces, then expand the gate regex to catch
  `Authority: ADR-NNNN`, bare `ADR-NNNN` in comments, `Wave C1`, `W2`,
  and `W3` style release metadata where it is not a protocol term.

### P1-3 - Streaming terminal semantics are internally inconsistent

**Evidence**

- `ModelGateway.stream(...)` Javadoc says the stream must contain at most
  one `Complete` element and it must be last
  (`ModelGateway.java:63-66`).
- `model-streaming.v1.yaml` says the `Complete` chunk is emitted exactly
  once at end-of-stream (`docs/contracts/model-streaming.v1.yaml:51-54`).
- The schema has no error chunk or exception mapping rule that explains
  when `Complete` may be absent.

**Why this matters**

Streaming consumers need to know whether usage, finish reason, and final
metadata are guaranteed. "At most one" and "exactly once" are different
contracts. Without an error/cancellation rule, W2 adapters will diverge.

**Required correction**

Define success, cancellation, and provider-error semantics explicitly:

- successful stream: exactly one terminal `Complete`
- cancelled stream: no `Complete`, stream close releases provider
  resources
- provider error: either stream throws a mapped exception or emits an
  explicit error chunk; choose one and document it

Then align ADR-0129, Java Javadocs, YAML, and tests.

## 6. P2 / over-engineering observations

### P2-1 - rc51 adds more governance without closing the governance failure mode

rc51 does not add new gate rules for the new SPI semantics; it relies on
existing R-D and G-1.1 parity gates. Those gates are useful for counting
and path parity, but they do not validate the semantic gaps found above:
streaming/advisor composition, conversation-memory key/value meaning,
finish-reason enum validity, release evidence cleanliness, or D-9
coverage.

The architecture is not over-designed because it has too many SPIs. The
over-design risk is a governance anti-pattern: many counters and parity
checks, not enough semantic assertions for the contracts that actually
matter at W2.

### P2-2 - Agent Service L1 path exists but is not yet a reviewable L1 artifact

`docs/L1/agent-service/.gitkeep` exists, but there is no dedicated
`docs/L1/agent-service/*.md` design document yet. The reviewable L1
surface remains `agent-service/ARCHITECTURE.md`. That is acceptable if
intentional, but release notes should avoid implying that a separate
Service L1 document has shipped under `docs/L1/agent-service/` until the
document exists.

## 7. Recommended rc52 corrective plan

### Wave A - Formal release transaction truth

1. Make rc52 the latest release note and mark it `formal_release: true`.
2. Regenerate evidence from a clean frozen candidate.
3. Strengthen `check_formal_release_transaction.py` to validate:
   - evidence repository dirty state is `false`
   - evidence commit equals release-note candidate SHA
   - latest release is formal when the note claims shipment
   - evidence bundle path round-trips with front-matter
4. Add self-tests for dirty evidence, candidate mismatch, and
   non-formal latest release.

### Wave B - Agentic contract composition

1. Resolve streaming + advisor composition.
2. Resolve `ConversationMemory` key/value semantics.
3. Replace string finish reasons with an executable enum/factory.
4. Align YAML, ADR, Java, quickstart, and tests in the same commit.

### Wave C - Constraint-system lockstep

1. Reconcile SPI-purity text across catalog, root architecture,
   module architecture, templates, and ArchUnit.
2. Decide the Rule D-9 policy: scrub code or formalize a narrow SPI
   Javadoc exception.
3. Update the gate so CI fails on the same conditions the rule text says
   are forbidden.

## 8. Release recommendation

Do not publish a final L0 closure release note from rc51. Publish an
rc52 corrective review response and release only after the above P0
findings are closed. The current green CI state is necessary evidence
for build health, but it is not sufficient evidence for L0 release
closure because the release transaction and multiple contract semantics
remain under-specified or internally inconsistent.

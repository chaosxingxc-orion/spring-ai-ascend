---
level: L1
view: logical
module: agent-client
status: skeleton
freeze_id: null
covers_views: [logical]
spans_levels: [L1]
authority: "ADR-0049 (Client SDK / Edge Access plane); ADR-0089 (Edge-Plane Ingress Gateway Mandate); Layer-0 principle P-I (Five-Plane Distributed Topology); CLAUDE.md Rule R-I sub-clause .b"
---

# agent-client — L1 architecture (skeleton)

> **Altitude discipline (L1).** This module-root file is the
> **shipped-state grounding** surface — purpose, boundary identity, the
> consumed cross-plane SPI (named), dependencies, deployment loci, and
> status. It deliberately does NOT carry code-level detail: envelope
> field shapes, the submit→cursor→stream call sequence, method
> signatures, and concrete test-class inventories are **L2 / contract /
> verification** material. The cross-plane wire shape lives in
> [`ingress-envelope.v1.yaml`](../../../../docs/contracts/ingress-envelope.v1.yaml);
> the per-view 4+1 files in this directory carry the narrative views; the
> SDK type inventory (when it lands) is owned by the generated facts under
> `architecture/facts/generated/`.

## Status

This module is a **skeleton** — a stable workspace for the client SDK,
whose implementation is planned per ADR-0049. There is no production code
and no SPI produced yet. The only test present is the cross-plane
no-direct-link sentinel `EdgeToComputeDirectLinkArchTest` (enforcer E143 /
gate Rule 105): vacuous on the empty tree, it fires the moment SDK code
lands and attempts a forbidden direct import into the compute-control
plane.

## 0.4 Layered 4+1 view map

Only the **logical** view is meaningful at this stage. Other views
populate as the SDK takes shape.

| Section | View | Notes |
|---|---|---|
| §1 Role | logical | Edge Access plane SDK |
| §2 Boundary | logical | consume `bus.spi.ingress`; Cursor Flow (Rule R-F) |
| §3 SPI surface | logical | consumes `bus.spi.ingress.IngressGateway`; produces none |

## 1. Role

`agent-client` is the **client-side SDK** that downstream applications
embed to submit Runs and consume their outputs without holding HTTP
connections open. It realizes the client half of the Cursor Flow contract
(Rule R-F): submission returns a Task Cursor, and clients consume process
state and intermediate-result checkpoints asynchronously rather than
blocking on a synchronous response. The submit / cursor / stream sequence
itself is a process-view concern and is narrated in
[`process.md`](process.md), not restated here.

## 2. Boundary

- **In scope (target):** authenticated submission through the
  `bus.spi.ingress.IngressGateway` SPI, Task Cursor handling, asynchronous
  result consumption, replay / idempotency helpers, and posture-aware
  backoff.
- **Out of scope:** server-side orchestration (owned by `agent-service`),
  heterogeneous engine selection (owned by `agent-execution-engine`; the
  neutral orchestration/engine SPI is owned by `agent-bus` as
  `bus.spi.engine` per ADR-0158), and the bus channels (owned by
  `agent-bus`).
- **No-direct-link clause (ADR-0089 / Rule R-I sub-clause .b):** the SDK
  MUST NOT import any class under
  `com.huawei.ascend.{service,engine,middleware}..`. Cross-plane traffic
  flows exclusively through `com.huawei.ascend.bus.spi.ingress.IngressGateway`,
  whose wire schema is
  [`ingress-envelope.v1.yaml`](../../../../docs/contracts/ingress-envelope.v1.yaml).
  Enforced by ArchUnit `EdgeToComputeDirectLinkArchTest` (enforcer E143) +
  gate Rule 105 (`edge_no_direct_compute_link`) + the
  `forbidden_dependencies` block in `module-metadata.yaml`.
- **`agent-bus` is intentionally NOT forbidden** — the SDK legitimately
  *consumes* the `bus.spi.ingress.IngressGateway` interface as its sole
  cross-plane entry point.

## 3. SPI surface

None produced. The SDK is a consumer of platform contracts:

- [`ingress-envelope.v1.yaml`](../../../../docs/contracts/ingress-envelope.v1.yaml)
  — the cross-plane wire shape for client → bus → server traffic (the
  single authority for the envelope field set and the response vocabulary;
  not restated here).
- The Task Cursor schema (Rule R-F).

## Dependencies

Dependency versions are managed by the parent POM and the
`spring-ai-ascend-dependencies` BoM; module files do not duplicate version
pins.

| Direction | Dependency | Reason |
|---|---|---|
| consumed (cross-plane) | `agent-bus` (`bus.spi.ingress`) | the sole allowed cross-plane entry point (ADR-0089). |
| forbidden | `agent-service`, `agent-execution-engine`, `agent-middleware`, `agent-evolve` | edge plane MUST NOT import compute-control / evolution planes (`module-metadata.yaml#forbidden_dependencies`, Rule R-I sub-clause .b). |

## Deployment loci

`deployment_loci: [platform_centric, business_centric]` — `agent-client`
always lives on the business side regardless of mode (ADR-0101); the rest
of the platform lives on the platform side in Mode-A and joins it on the
business side in Mode-B.

## Tests

The SDK test inventory is **verification material**, owned by the
verification layer and the generated facts
`architecture/facts/generated/tests.json`; it is not enumerated here.
Three-layer testing discipline per Rule D-4. The first SDK landing wave
MUST keep the `EdgeToComputeDirectLinkArchTest` sentinel green on the
populated tree and add contract-conformance coverage against
[`ingress-envelope.v1.yaml`](../../../../docs/contracts/ingress-envelope.v1.yaml).
A deferred backpressure sub-clause is parked in the `deferred_sub_clauses`
block of [`rule-R-I.md`](../../../../docs/governance/rules/rule-R-I.md).

## Reading order for new contributors

1. `module-metadata.yaml` — identity + dependency promises.
2. [`ingress-envelope.v1.yaml`](../../../../docs/contracts/ingress-envelope.v1.yaml) — the cross-plane wire shape this SDK consumes.
3. [`agent-bus/ARCHITECTURE.md`](../agent-bus/ARCHITECTURE.md) §3a — the IngressGateway SPI surface the SDK calls.
4. `docs/dfx/agent-client.yaml` — Design-for-X declarations.
5. ADR-0049 (Edge Access plane) + ADR-0089 (Edge-Plane Ingress Gateway Mandate) — module authority.

---

## 3. Development View (Rule G-1.1.a)

Package decomposition (the type inventory under each package is owned by
the generated code facts, `architecture/facts/generated/code-symbols.json`,
and is not restated here):

```text
agent-client/
└── src/main/java/
    └── com/huawei/ascend/client/
        └── (SDK code lands here; the sole present anchor is the
              package-info placeholder + the cross-plane sentinel test)
```

Mode-A (Platform-Centric per ADR-0101): this module deploys on the
business side only; everything else lives on platform.
Mode-B (Business-Centric per ADR-0101): this module continues to live on
the business side; `agent-service` + `agent-execution-engine` join it.

## *SPI Interface Appendix* (Rule G-1.1.b)

`agent-client` produces NO SPI of its own — it is a pure consumer module.
The consumed boundary surface (for the parity check in Rule G-1.1.b) is the
ingress SPI owned by `agent-bus`; its interface FQN and wire contract are
named below, while record field sets and method signatures are owned by the
contract and the generated code facts and are not restated here:

| Consumed boundary interface FQN | Owner module | Wire contract |
|---|---|---|
| `com.huawei.ascend.bus.spi.ingress.IngressGateway` | `agent-bus` | `ingress-envelope.v1.yaml` |

Producer-side parity is verified by `agent-bus/ARCHITECTURE.md` per Rule
G-1.1.b; the consumer-side appendix above documents the integration surface
without claiming ownership.

## *L2 Constraint Linkage* (Rule G-1.1.c)

No L2 design is authored for this module today (vacuously green). When the
SDK implementation lands, any subsystem it delegates to an L2 design (e.g.
a session-checkpoint protocol) MUST carry a Boundary Contracts sub-section
declaring its inputs / outputs / DFX obligations.

## Deployment loci (ADR-0101)

`deployment_loci: [platform_centric, business_centric]` — `agent-client`
always lives on the business side regardless of mode.

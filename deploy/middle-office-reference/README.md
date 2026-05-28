# 中台-mode Reference Deployment

> Authority: ADR-0156 (Product Authority and Traceability Chain)
> Product claim: **PC-002** (Two Chinese-enterprise topologies plus sovereign hardware, one artifact set)
> Target personas: **Persona-A** (Platform Team Lead) + **Persona-D** (SRE)
> Status: **design_only** — scaffold, not deployment-tested. Promotes to L1 when the impl-mode wave wires the Postgres RLS + audit-trail + IAM bridge + financial sandbox policy templates and runs a soak on Kunpeng+Ascend hardware.
> v1.0 ship-blocker: **yes**

This chart is the reference deployment for the **中台 (middle-office) topology** of `spring-ai-ascend` on sovereign Huawei Kunpeng ARM64 + Ascend 310 NPU hardware. It is one of the two topologies named in product/PRODUCT.md item (1); the second (**能力复用**, capability-reuse) ships in v1.1.

## Who operates what

The 中台 pattern has a sharp operational boundary the chart enforces structurally:

| Operator | Owns | Pushed via |
|---|---|---|
| **中台 IT (Persona-A)** | The **runtime** (`agent-service` Deployment), **bus** (`agent-bus` StatefulSet), **middleware** (Postgres + model-gateway + sandbox-runtime), **audit storage** (`audit-trail-postgres`), and **observability stack** (OTel + Prometheus + Grafana) | Helm release of this chart — `helm install middle-office deploy/middle-office-reference -f values-bank-example.yaml` |
| **Business centers (Persona-C developers in business units)** | **Agent definitions only** — declarative YAML + skill bundles consumed by the runtime. Business logic + business-system calls live in agents the business center authors. | Skill-bundle artifact pipeline (out of scope for THIS chart; see `deploy/skill-bundle-pipeline/` in a follow-up wave) — typically a Maven artifact + an HTTP push to `POST /v1/agents` on the runtime. |
| **Compliance (Persona-F)** | Sign-off on `financial_default` sandbox policy + IAM bridge + audit-trail schema BEFORE Persona-A promotes the release to prod posture. | Compliance reviewer checklist (`docs/onboarding/compliance-reviewer.md`). |

The line is enforceable: business centers cannot reach the underlying Postgres, the bus, or the audit store — the `NETWORK-POLICY.yaml` denies that traffic by default. Business centers consume the runtime through the HTTP API only.

## What this chart deploys

```
deploy/middle-office-reference/
├── Chart.yaml                             — Helm chart metadata (version 1.0.0-finance)
├── values.yaml                            — defaults
├── values-bank-example.yaml               — concrete example for a hypothetical 民生银行 deployment
├── NETWORK-POLICY.yaml                    — Calico/Cilium network policies isolating sandbox + audit storage (chart root for visibility)
├── README.md                              — this file
└── templates/
    ├── agent-service-deployment.yaml      — Deployment + Service + ServiceAccount for the runtime
    ├── agent-bus-statefulset.yaml         — StatefulSet for the three-track channel hub (Rule R-E)
    ├── postgres-rls-init.yaml             — Postgres + Flyway init Job + RLS-enabled migrations (Rule R-J.a)
    ├── model-gateway-deployment.yaml      — model gateway sidecar (Rule R-M.c hook binding site)
    ├── sandbox-runtime-deployment.yaml    — sandbox executor pool (Rule R-L)
    ├── observability-stack.yaml           — OTel Collector + Prometheus scrape config + Grafana admin secret
    └── audit-trail-postgres.yaml          — append-only Postgres + checksum-chain daily verifier
```

All resource sizing is sourced **upstream** from `docs/perf/v1.0-baselines.yaml#reference_hardware` (Kunpeng 920 ARM64 4-core / 8GiB; supports 100 concurrent Runs per `agent-service` pod). Do NOT raise the requests/limits in `values.yaml` without updating the perf-baselines file first (Rule R-D + Rule G-13 single-source rendering).

## What gets wired together

```
                       ┌────────────────────────────────┐
                       │   Bank ingress gateway         │
                       │   (mTLS + bank IAM)            │
                       └────────────────┬───────────────┘
                                        │  HTTP /v1/runs
                                        ▼
   ┌────────────────────────────────────────────────────────────┐
   │  agent-service (Deployment, 4-core / 8GiB × N replicas)    │
   │  - POST /v1/runs, GET /v1/runs/{id}, POST .../cancel       │
   │  - Run state machine, idempotency, JWT tenant validation   │
   └─┬──────────────┬──────────────┬────────────┬───────────────┘
     │ R2DBC RLS    │ control/data │ model      │ BEFORE_/AFTER_
     │ tenant=...   │ /rhythm      │ invoke     │ AuditTrailEntry
     ▼              ▼              ▼            ▼
  Postgres      agent-bus      model-gateway   audit-trail-postgres
  (state)       (Rule R-E)     (cost+audit     (append-only,
   ↑                            hooks)          checksum-chain)
   │                              ▲
   │ flyway-init Job              │ /v1/chat/completions
   │ (RLS migrations)             ▼
   ▼                          sandbox-runtime
  RLS predicates              (Rule R-L; deny-all
  per Rule R-J.a              outbound by default)
```

Cross-cutting:

- **IAM bridge** — `agentService.env.IAM_BRIDGE_JWKS_URI` / `tenantClaim` (configured per bank in `values-bank-example.yaml`). Persona-F sign-off required: the JWT's tenant claim is cross-checked against `X-Tenant-Id` (§4 #56). The `User-Context-Token` header carrying the delegating end-user identity is propagated to downstream business-system calls; loan officer → core banking, not service principal → core banking. Authority: `docs/contracts/iam-bridge.v1.yaml`.
- **Audit trail destination** — `audit-trail-postgres` is a SEPARATE Postgres instance from the runtime state store (backup retention differs; banks retain audit 7+ years per JR/T 0223-2021). Append-only Postgres trigger refuses UPDATE/DELETE; daily checksum-chain verifier CronJob runs at 02:00 UTC. Authority: `docs/contracts/audit-trail.v1.yaml`.
- **Sandbox policy** — `sandbox-runtime` pods mount the `financial_default` policy as a read-only ConfigMap (path `/etc/agent-config/readonly/sandbox-policies.yaml`). Per Rule R-L the **physical sandbox** here MUST enforce a permission set ≥ the logical grants; the `NETWORK-POLICY.yaml` enforces the deny-all-outbound part at the network layer. Authority: `docs/governance/sandbox-policies.yaml#financial_default`.
- **Cost governance** — the `model-gateway` pod consults the token budget per (tenant, agent) on `BEFORE_LLM` and records spend on `AFTER_LLM`. Default budget from `docs/governance/skill-capacity.yaml#v1_0_financial_default_budget`. Per-tenant overrides supplied via `values-bank-example.yaml#costGovernance.tenantOverrides[]`. Schema: `docs/contracts/cost-governance.v1.yaml`.
- **Observability** — every pod emits `prometheus.io/scrape: true` + path `/actuator/prometheus`. The OTel collector aggregates traces + metrics; Prometheus scrapes via Kubernetes SD; Grafana dashboards out-of-band. Three-axis observability per PC-005: business axis comes from agents, platform axis comes from `agent-service` + `agent-bus`, model axis comes from `model-gateway`.

## How a bank deploys this

```bash
# 1. Persona-A clones the values-bank-example.yaml into the bank's internal
#    GitOps repo and adjusts:
#      - global.imageRegistry        (bank's internal Harbor)
#      - global.imagePullSecrets     (bank's pull secret name)
#      - iamBridge.jwksUri           (bank's IAM JWKS endpoint)
#      - iamBridge.tenantClaim       (the bank's JWT claim name)
#      - costGovernance.tenantOverrides[]  (per-business-center budgets)
#      - networkPolicies.allowedEgressCidrs (bank VPC CIDRs)
#      - postgres.persistentVolumeSize + auditTrail.persistentVolumeSize
#        (sized for bank's retention policy)
#
# 2. Persona-F reviews the financial_default sandbox policy ConfigMap in
#    templates/sandbox-runtime-deployment.yaml + the IAM bridge wiring
#    + the audit-trail schema. Sign-off required BEFORE prod promotion.
#
# 3. Persona-A applies via Argo CD or Flux CD targeting the bank's
#    Kunpeng+Ascend cluster:
#
#      helm install middle-office \
#        deploy/middle-office-reference \
#        -f deploy/middle-office-reference/values.yaml \
#        -f path/to/bank-internal/values-民生.yaml \
#        --namespace agent-platform \
#        --create-namespace
#
# 4. Persona-A applies the chart-root NetworkPolicy set:
#
#      kubectl -n agent-platform apply \
#        -f deploy/middle-office-reference/NETWORK-POLICY.yaml
#
# 5. Verify the Flyway init Job completed and RLS is enabled on every
#    tenant-bearing table (Rule R-J.a):
#
#      kubectl -n agent-platform logs job/middle-office-flyway-init
#      kubectl -n agent-platform exec -it middle-office-postgres-0 -- \
#        psql -U spring_ai_ascend -d spring_ai_ascend \
#        -c "SELECT tablename, rowsecurity FROM pg_tables WHERE rowsecurity = true;"
#
# 6. Verify the audit-trail trigger refuses UPDATE/DELETE:
#
#      kubectl -n agent-platform exec -it middle-office-audit-trail-postgres-0 -- \
#        psql -U audit_writer -d audit_trail \
#        -c "DELETE FROM audit_trail_entries WHERE TRUE;"
#      # MUST raise: "audit_trail_entries is append-only"
#
# 7. Persona-A hands the runtime endpoint URL to business centers, who
#    push agent definitions via the skill-bundle artifact pipeline.
```

## Ops runbook (Persona-D)

| Symptom | First diagnostic | Likely root cause |
|---|---|---|
| `POST /v1/runs` p95 > 500ms | `kubectl top pod -n agent-platform` + Prometheus `springai_ascend_runs_admit_seconds` histogram | Insufficient `agent-service` replicas; raise `replicaCount` (each pod supports 100 concurrent Runs per `v1.0-baselines.yaml#throughput`). |
| Cost-governance rejecting legitimate traffic | `springai_ascend_cost_budget_exceeded_total` counter | Bank's `tenantOverrides[]` budget set too low for the business center's workload; Persona-A raises the budget after Persona-A+F review. |
| Sandbox pod crashloop | `kubectl logs -n agent-platform <sandbox-pod>` | `sandbox-policies.yaml#financial_default` ConfigMap drift; re-render from the source-of-truth in repo. |
| Audit chain verifier alert | `kubectl logs -n agent-platform job/middle-office-audit-chain-verifier-<ts>` | Audit-store tampering — escalate to Persona-F immediately; bank's incident-response playbook applies. |
| RLS leak (cross-tenant read possible) | `psql -c "SHOW row_security; SELECT * FROM pg_policies WHERE tablename = '<table>';"` | Migration regression; halt deploys, revert to last known-good Postgres image, re-run Flyway from baseline. |

## What is INTENTIONALLY out of scope

- **No business-logic code.** Business centers push agent definitions; their code does not live in this chart.
- **No Postgres HA.** v1.0 ships single-primary Postgres for both state and audit. HA replica wiring (Patroni / pgBackRest) lands in a follow-up impl-mode wave.
- **No automated DR (disaster recovery).** Banks integrate their own backup tooling (Velero, pgBackRest, Bacula).
- **No 能力复用 mode.** That topology gets `deploy/capability-reuse-reference/` in v1.1.

## References

- `product/PRODUCT.md` — Product Authority (PC-002 statement, Persona-A definition)
- `product/claims.yaml#PC-002` — claim specifics
- `product/personas.yaml#Persona-A` — 中台 buyer persona
- `docs/adr/0155-product-authority-and-traceability.yaml` — authority ADR
- `docs/perf/v1.0-baselines.yaml` — performance SLOs the chart sizes against
- `docs/contracts/audit-trail.v1.yaml` — audit-trail schema authority
- `docs/contracts/iam-bridge.v1.yaml` — IAM bridge schema authority
- `docs/contracts/cost-governance.v1.yaml` — cost-governance schema authority
- `docs/governance/sandbox-policies.yaml` — `financial_default` policy authority
- `docs/governance/skill-capacity.yaml` — `v1_0_financial_default_budget` authority

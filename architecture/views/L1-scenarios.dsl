// architecture/views/L1-scenarios.dsl
//
// Authority: ADR-0151 (W3 of L1 Feature Registry plan).
// 4+1 scenarios view — key user/system scenarios as a container view with
// description text listing 5 anchor scenarios. Detailed dynamic-view
// sequences live in Markdown narrative
// (architecture/docs/L1/<module>/scenarios.md + architecture/docs/L1/<module>/features/README.md).

container springAiAscend "L1-Scenarios" "Scenarios view — anchor user/system scenarios" {
    include *
    autoLayout lr
    title "Spring AI Ascend — L1 Scenarios View"
    description "Anchor scenarios: S1 — Create Run (FEAT-RUN-LIFECYCLE-CONTROL: POST /v1/runs → IdempotencyHeaderFilter → EngineDispatcher → 202 Accepted + Run handle). S2 — Cancel Run (FEAT-TENANT-ISOLATION: JWT.tenant cross-check + DFA transition → 200 / 409 / 404). S3 — S2C Callback (FEAT-SERVER-CLIENT-CALLBACK: agent-bus delivers S2cCallbackEnvelope; client response resumes Run via SuspendSignal.forClientCallback). S4 — Edge Ingress (FEAT-EDGE-COMPUTE-INGRESS: IngressEnvelope single hop via IngressGateway). S5 — Graph Memory Read (FEAT-GRAPH-MEMORY: tenant-scoped CRUD)."
}

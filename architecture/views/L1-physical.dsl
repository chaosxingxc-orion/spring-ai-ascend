// architecture/views/L1-physical.dsl
//
// Authority: ADR-0151 (W3 of L1 Feature Registry plan).
// 4+1 physical view — deployment topology aligned with the Five-Plane
// distributed architecture (P-I; ADR-0046). Each container is pinned to
// exactly one plane (declared via module-metadata.yaml#deployment_plane);
// the view's description names the plane mapping.

container springAiAscend "L1-Physical" "Physical view — five-plane deployment topology" {
    include *
    autoLayout lr
    title "Spring AI Ascend — L1 Physical View"
    description "Five-plane deployment topology (Rule R-I + ADR-0046): Edge Access (agent-client) — JVM service tier on Kunpeng ARM64. Compute & Control (agent-service / agent-runtime / agent-middleware) — Kunpeng ARM64 service tier. Bus & State Hub (agent-bus / graphmemory-starter) — cross-plane control + state surface. Evolution (agent-evolve) — ML / self-improvement pipeline (skeleton). Sandbox plane reserved for untrusted code execution (no current container). Module-to-plane pinning is enforced by module-metadata.yaml#deployment_plane."
}

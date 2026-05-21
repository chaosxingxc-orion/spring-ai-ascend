package com.huawei.ascend.service.runtime.evolution;

/**
 * Discriminator declaring whether a runtime event is eligible for the
 * evolution plane (deployment_plane: evolution per Rule 39).
 *
 * <p>Authority: docs/governance/evolution-scope.v1.yaml; ADR-0075;
 * CLAUDE.md Rule 47 (lands in W2.x Phase 7).
 *
 * <p>The three values mirror the three top-level discriminators in the YAML
 * schema:
 * <ul>
 *   <li>{@link #IN_SCOPE} -- server-controlled events listed under
 *       {@code in_scope:} in evolution-scope.v1.yaml; the evolution plane
 *       MAY persist these without further opt-in.</li>
 *   <li>{@link #OUT_OF_SCOPE} -- events listed under
 *       {@code out_of_scope_default:}; the evolution plane MUST NOT persist
 *       these (P-M server-sovereign boundary).</li>
 *   <li>{@link #OPT_IN} -- events that would be out-of-scope by default but
 *       carry an explicit opt-in via the future telemetry-export.v1.yaml
 *       contract (W3 placeholder). The evolution plane validates the
 *       contract before persistence; missing contract means hard deny.</li>
 * </ul>
 *
 * <p>Phase 4 ships only the enum. The {@code RunEvent} sealed interface that
 * will require {@code evolutionExport()} per variant lands in W2 (no
 * RunEvent type exists yet in the post-Phase-C runtime kernel
 * {@code agent-runtime-core/src/main} or {@code agent-service/src/main/.../runtime}
 * — formerly the pre-Phase-C {@code agent-runtime/src/main} tree; ADR-0022 declares
 * the type but defers implementation to W2). When RunEvent ships, every
 * variant MUST declare its export value at the type level; the W2.x
 * {@code EveryRunEventDeclaresEvolutionExportTest} ArchUnit test is armed
 * with {@code allowEmptyShould(true)} so it passes today and starts asserting
 * the moment RunEvent variants land.
 *
 * <p>Pure Java -- no Spring imports per architecture section 4.7
 * (runtime evolution package imports only java.*).
 */
public enum EvolutionExport {
    /** Server-controlled event the evolution plane MAY persist by default. */
    IN_SCOPE,
    /** Client-owned event the evolution plane MUST NOT persist (default deny). */
    OUT_OF_SCOPE,
    /** Opt-in export gated by a telemetry-export.v1.yaml contract row (W3). */
    OPT_IN
}

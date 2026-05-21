/**
 * Evolution-plane scope contract surface (P-M / Rule 47, ADR-0075).
 *
 * <p>This package owns the runtime-side declaration of which events are
 * eligible to feed the evolution plane (deployment_plane: evolution per
 * Rule 39). The single Phase 4 deliverable is the {@link
 * com.huawei.ascend.service.runtime.evolution.EvolutionExport} enum -- the Java
 * mirror of the three discriminator values in
 * {@code docs/governance/evolution-scope.v1.yaml}.
 *
 * <p>The {@code RunEvent} sealed interface that will carry the
 * {@code EvolutionExport evolutionExport()} per-variant declaration does
 * NOT exist yet in the post-Phase-C runtime kernel
 * ({@code agent-runtime-core/src/main} or {@code agent-service/src/main/.../runtime},
 * consolidated from the pre-Phase-C {@code agent-runtime/src/main} tree).
 * ADR-0022 declared the
 * type but deferred implementation to W2 alongside the typed payload codec
 * + streaming Flux contract. When RunEvent variants ship, each MUST declare
 * its export value at the record level; the W2.x ArchUnit test
 * {@code EveryRunEventDeclaresEvolutionExportTest} (enforcer E86) is armed
 * with {@code allowEmptyShould(true)} so it stays GREEN today and starts
 * asserting the contract the moment the first RunEvent variant lands.
 *
 * <p>Authority: docs/governance/evolution-scope.v1.yaml; ADR-0075;
 * CLAUDE.md Rule 47 (lands in W2.x Phase 7).
 */
package com.huawei.ascend.service.runtime.evolution;

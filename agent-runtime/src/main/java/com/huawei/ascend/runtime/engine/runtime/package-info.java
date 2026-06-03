/**
 * Engine runtime package — implementation home for the engine contract surface
 * (registry + envelope) owned by the {@code agent-runtime} module.
 *
 * <p>This is the semantic home for {@link com.huawei.ascend.runtime.engine.runtime.EngineRegistry}
 * (single dispatch authority per Rule R-M sub-clause .a) and
 * {@link com.huawei.ascend.runtime.engine.runtime.EngineEnvelope} (engine request shape
 * mirroring {@code docs/contracts/engine-envelope.v1.yaml}). The package is NOT
 * the engine SPI surface — that is {@code com.huawei.ascend.runtime.engine.spi.*}; the SPI
 * remains the team-facing contract while this package is the in-module
 * implementation of the dispatch + envelope plumbing.
 *
 * <p>Package path history: pre-rc13 (ADR-0079) these types lived at
 * {@code com.huawei.ascend.runtime.engine.*} for backwards source
 * compatibility through the engine-extraction wave; rc14 (ADR-0090,
 * 2026-05-20) relocated them to {@code com.huawei.ascend.runtime.engine.runtime.*}
 * to align with the post-ADR-0088 semantic-home model. ADR-0079's
 * source-compatibility exception was retired with this move.
 *
 * <p>Authority: ADR-0072 (Engine Envelope + Strict Matching); ADR-0088
 * (agent-runtime-core dissolution); ADR-0090 (rc14 cross-authority parity +
 * engine semantic-home); CLAUDE.md Rule R-M sub-clauses .a + .b.
 *
 * @since rc14 (2026-05-20)
 */
package com.huawei.ascend.runtime.engine.runtime;

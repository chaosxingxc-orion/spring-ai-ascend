/**
 * agent-service run-lifecycle SPI — the {@link
 * ascend.springai.service.runtime.runs.spi.RunRepository} persistence contract.
 *
 * <p>SPI-pure per CLAUDE.md Rule R-D sub-clause .d: imports restricted to
 * {@code java.*} + sibling domain value types in
 * {@code ascend.springai.service.runtime.runs}
 * ({@link ascend.springai.service.runtime.runs.Run},
 * {@link ascend.springai.service.runtime.runs.RunStatus}) which form the
 * lifecycle vocabulary this SPI persists. Spring / platform / impl / metrics
 * imports are forbidden (enforced by SpiPurityGeneralizedArchTest).
 *
 * <p>Authority: ADR-0088 (agent-runtime-core dissolution — runs lifecycle
 * relocated to agent-service); CLAUDE.md Rule R-C sub-clause .c.
 */
package ascend.springai.service.runtime.runs.spi;

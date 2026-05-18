/**
 * agent-runtime-core run-lifecycle SPI — the {@link
 * ascend.springai.service.runtime.runs.spi.RunRepository} persistence contract.
 *
 * <p>SPI-pure per CLAUDE.md Rule 32: imports restricted to {@code java.*} +
 * sibling domain value types in {@code ascend.springai.service.runtime.runs}
 * ({@link ascend.springai.service.runtime.runs.Run},
 * {@link ascend.springai.service.runtime.runs.RunStatus}) which form the
 * lifecycle vocabulary this SPI persists. Spring / platform / impl / metrics
 * imports are forbidden (enforced by E48 SpiPurityGeneralizedArchTest).
 *
 * <p>Authority: ADR-0079 (engine-extraction-runtime-core); CLAUDE.md Rule 32.
 */
package ascend.springai.service.runtime.runs.spi;

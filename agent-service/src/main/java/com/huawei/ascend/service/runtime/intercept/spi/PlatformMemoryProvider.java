package com.huawei.ascend.service.runtime.intercept.spi;

/**
 * Read-only Session-context provider used by Agents to retrieve STM-04
 * facts before constructing prompts.
 *
 * <p>This SPI replaces the v1-draft assumption that M6 would read STM-04
 * and inject history into prompts. Per the v1.2 reversal, Agents read STM-04
 * via this SPI and assemble their own messages list.</p>
 *
 * <p>Authority: ADR-0155. Status: design_only.</p>
 */
public interface PlatformMemoryProvider {

    /**
     * Read a Session snapshot at-or-after the given version cursor.
     *
     * @param sessionId tenant-scoped session identifier.
     * @param fromVersion lower-bound version cursor (inclusive); 0 reads from the head.
     * @return session snapshot (schema: {@code docs/contracts/session-snapshot.v1.yaml}).
     */
    Object readSnapshot(String sessionId, long fromVersion);
}

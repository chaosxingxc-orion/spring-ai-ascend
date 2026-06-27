/** W38 Phase 3 — format / parse ACP sessionUpdate NDJSON for sidecar relay. */

export function sessionUpdatesToNdjson(updates: Record<string, unknown>[]): string {
  if (updates.length === 0) {
    return '';
  }
  return `${updates.map((row) => JSON.stringify(row)).join('\n')}\n`;
}

export function parseAcpNdjson(ndjson: string): Record<string, unknown>[] {
  const rows: Record<string, unknown>[] = [];
  for (const line of ndjson.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed) {
      continue;
    }
    rows.push(JSON.parse(trimmed) as Record<string, unknown>);
  }
  return rows;
}

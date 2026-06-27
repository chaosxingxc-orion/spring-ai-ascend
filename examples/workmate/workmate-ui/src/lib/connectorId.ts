const CONNECTOR_ID_ALIASES: Record<string, string> = {
  'qieman-mcp': 'qieman',
};

/** Map expert/catalog aliases to gateway MCP server ids. */
export function normalizeConnectorId(connectorId: string): string {
  const trimmed = connectorId.trim();
  if (!trimmed) {
    return trimmed;
  }
  const aliased = CONNECTOR_ID_ALIASES[trimmed.toLowerCase()];
  if (aliased) {
    return aliased;
  }
  if (trimmed.endsWith('-mcp')) {
    return trimmed.slice(0, -4);
  }
  return trimmed;
}

export function normalizeConnectorIds(connectorIds: string[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const id of connectorIds) {
    const canonical = normalizeConnectorId(id);
    if (!canonical || seen.has(canonical)) {
      continue;
    }
    seen.add(canonical);
    result.push(canonical);
  }
  return result;
}

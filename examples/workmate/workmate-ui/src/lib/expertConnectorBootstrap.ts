import { connectConnector, listConnectors, reconnectConnector } from '../api/market';
import type { Expert } from '../types/api';
import type { ConnectorInfo } from '../types/market';
import { touchRecentConnectorId } from './connectorPopoverSort';
import { normalizeConnectorId, normalizeConnectorIds } from './connectorId';

export function expertRecommendedConnectorIds(expert: Expert): string[] {
  const raw = [...new Set((expert.skillCompatibility ?? []).map((id) => id.trim()).filter(Boolean))];
  return normalizeConnectorIds(raw);
}

async function ensureGatewayConnected(connector: ConnectorInfo): Promise<void> {
  if (connector.status === 'connected') {
    return;
  }
  if (connector.status === 'error') {
    await reconnectConnector(connector.id);
    return;
  }
  await connectConnector(connector.id);
}

/**
 * Connect gateway MCP servers recommended by an expert when possible.
 * Returns the connector catalog used (for UI refresh without a second list call).
 */
export async function autoConnectExpertConnectors(
  connectorIds: string[],
): Promise<ConnectorInfo[] | null> {
  if (connectorIds.length === 0) {
    return null;
  }
  let connectors: ConnectorInfo[];
  try {
    connectors = await listConnectors();
  } catch {
    return null;
  }
  const byId = new Map(connectors.map((connector) => [connector.id, connector]));
  for (const id of connectorIds) {
    const connector = byId.get(normalizeConnectorId(id));
    if (!connector || connector.runnable === false) {
      continue;
    }
    if (connector.status === 'connected') {
      continue;
    }
    // Already past the 'connected' guard above, so an auth-requiring connector is not yet connected.
    if (connector.requiresAuth) {
      continue;
    }
    try {
      await ensureGatewayConnected(connector);
    } catch {
      // Best-effort: session can still enable the connector for later auth.
    }
  }
  return connectors;
}

export function markExpertConnectorsRecent(connectorIds: string[]): void {
  for (const id of connectorIds) {
    touchRecentConnectorId(id);
  }
}

import type { ConnectorInfo } from '../types/market';

const RECENT_KEY = 'workmate.connectorRecentIds';
const MAX_RECENT = 24;

export function loadRecentConnectorIds(): string[] {
  try {
    const raw = localStorage.getItem(RECENT_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter((id): id is string => typeof id === 'string' && id.length > 0);
  } catch {
    return [];
  }
}

export function touchRecentConnectorId(connectorId: string): string[] {
  const trimmed = connectorId.trim();
  if (!trimmed) {
    return loadRecentConnectorIds();
  }
  const next = [trimmed, ...loadRecentConnectorIds().filter((id) => id !== trimmed)].slice(0, MAX_RECENT);
  try {
    localStorage.setItem(RECENT_KEY, JSON.stringify(next));
  } catch {
    // ignore quota / private mode
  }
  return next;
}

/**
 * Popover list order: session-enabled first (most recently enabled on top),
 * then recently used, then gateway-connected, then name.
 */
export function sortConnectorsForPopover(
  connectors: ConnectorInfo[],
  enabledIds: string[],
  recentIds: string[],
): ConnectorInfo[] {
  const enabledRank = new Map<string, number>();
  [...enabledIds].reverse().forEach((id, index) => enabledRank.set(id, index));

  const recentRank = new Map<string, number>();
  recentIds.forEach((id, index) => recentRank.set(id, index));

  return [...connectors].sort((a, b) => {
    const aEnabled = enabledIds.includes(a.id);
    const bEnabled = enabledIds.includes(b.id);
    if (aEnabled !== bEnabled) {
      return aEnabled ? -1 : 1;
    }

    if (aEnabled && bEnabled) {
      const rankDiff = (enabledRank.get(a.id) ?? 999) - (enabledRank.get(b.id) ?? 999);
      if (rankDiff !== 0) {
        return rankDiff;
      }
    }

    const aRecent = recentRank.has(a.id);
    const bRecent = recentRank.has(b.id);
    if (aRecent !== bRecent) {
      return aRecent ? -1 : 1;
    }
    if (aRecent && bRecent) {
      const rankDiff = (recentRank.get(a.id) ?? 0) - (recentRank.get(b.id) ?? 0);
      if (rankDiff !== 0) {
        return rankDiff;
      }
    }

    const aConnected = a.status === 'connected';
    const bConnected = b.status === 'connected';
    if (aConnected !== bConnected) {
      return aConnected ? -1 : 1;
    }

    return a.name.localeCompare(b.name, 'zh-CN');
  });
}

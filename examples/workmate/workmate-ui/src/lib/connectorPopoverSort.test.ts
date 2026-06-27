import { describe, expect, it } from 'vitest';
import type { ConnectorInfo } from '../types/market';
import { sortConnectorsForPopover } from './connectorPopoverSort';

function connector(id: string, name: string, status: ConnectorInfo['status'] = 'disconnected'): ConnectorInfo {
  return {
    id,
    name,
    description: '',
    status,
    runnable: true,
  };
}

describe('sortConnectorsForPopover', () => {
  it('puts session-enabled connectors first, most recently enabled on top', () => {
    const items = [
      connector('a', 'Alpha'),
      connector('b', 'Beta'),
      connector('c', 'Gamma'),
    ];
    const sorted = sortConnectorsForPopover(items, ['a', 'c'], []);
    expect(sorted.map((c) => c.id)).toEqual(['c', 'a', 'b']);
  });

  it('ranks recently used among non-enabled connectors', () => {
    const items = [
      connector('a', 'Alpha'),
      connector('b', 'Beta'),
      connector('c', 'Gamma'),
    ];
    const sorted = sortConnectorsForPopover(items, [], ['c', 'a']);
    expect(sorted.map((c) => c.id)).toEqual(['c', 'a', 'b']);
  });

  it('ranks gateway-connected before disconnected when otherwise equal', () => {
    const items = [
      connector('a', 'Alpha', 'disconnected'),
      connector('b', 'Beta', 'connected'),
    ];
    const sorted = sortConnectorsForPopover(items, [], []);
    expect(sorted.map((c) => c.id)).toEqual(['b', 'a']);
  });
});

import { describe, expect, it, vi, beforeEach } from 'vitest';

const getDesktopBridge = vi.fn<() => unknown>(() => undefined);

vi.mock('./desktopBridge', () => ({
  getDesktopBridge: () => getDesktopBridge(),
}));

vi.mock('../api/client', () => ({
  ingestAcpNdjson: vi.fn(async () => [{ seq: 1, name: 'team.started', data: {} }]),
}));

import { ingestAcpNdjson } from '../api/client';
import {
  canRelayStreamableSidecar,
  importAcpNdjsonText,
  relayStreamableSidecar,
} from './acpSidecarImport';

describe('importAcpNdjsonText', () => {
  beforeEach(() => {
    getDesktopBridge.mockReturnValue(undefined);
    vi.clearAllMocks();
  });

  it('uses web API when desktop bridge is absent', async () => {
    const result = await importAcpNdjsonText('s1', '{"sessionUpdate":"chunk"}\n');
    expect(ingestAcpNdjson).toHaveBeenCalledWith('s1', '{"sessionUpdate":"chunk"}\n');
    expect(result.ingested).toBe(1);
  });

  it('uses desktop relay when bridge is present', async () => {
    const relayAcpNdjson = vi.fn(async () => ({ ingested: 3, events: [] }));
    getDesktopBridge.mockReturnValue({
      relayAcpNdjson,
      relayStreamableHttp: vi.fn(),
    });
    const result = await importAcpNdjsonText('s1', '{"sessionUpdate":"chunk"}');
    expect(relayAcpNdjson).toHaveBeenCalled();
    expect(ingestAcpNdjson).not.toHaveBeenCalled();
    expect(result.ingested).toBe(3);
  });

  it('rejects empty payload', async () => {
    await expect(importAcpNdjsonText('s1', '  ')).rejects.toThrow('NDJSON 内容为空');
  });
});

describe('relayStreamableSidecar', () => {
  beforeEach(() => {
    getDesktopBridge.mockReturnValue(undefined);
  });

  it('requires desktop bridge', async () => {
    expect(canRelayStreamableSidecar()).toBe(false);
    await expect(relayStreamableSidecar('s1')).rejects.toThrow('不支持');
  });

  it('delegates to preload relay', async () => {
    const relayStreamableHttp = vi.fn(async () => ({ ingested: 2, events: [] }));
    getDesktopBridge.mockReturnValue({ relayStreamableHttp });
    expect(canRelayStreamableSidecar()).toBe(true);
    const result = await relayStreamableSidecar('s1');
    expect(relayStreamableHttp).toHaveBeenCalledWith('s1', undefined);
    expect(result.ingested).toBe(2);
  });
});

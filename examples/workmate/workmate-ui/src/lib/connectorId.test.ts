import { describe, expect, it } from 'vitest';
import { normalizeConnectorId, normalizeConnectorIds } from './connectorId';

describe('normalizeConnectorId', () => {
  it('maps qieman-mcp to qieman', () => {
    expect(normalizeConnectorId('qieman-mcp')).toBe('qieman');
  });

  it('strips generic -mcp suffix', () => {
    expect(normalizeConnectorId('oa-mcp')).toBe('oa');
  });

  it('preserves canonical ids', () => {
    expect(normalizeConnectorId('qieman')).toBe('qieman');
  });
});

describe('normalizeConnectorIds', () => {
  it('deduplicates aliases', () => {
    expect(normalizeConnectorIds(['qieman-mcp', 'qieman'])).toEqual(['qieman']);
  });
});

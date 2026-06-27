import { describe, expect, it } from 'vitest';
import { normalizeToolStatus, statusLabel, toolEndStatus } from './toolStatus';

describe('toolStatus', () => {
  it('normalizes legacy server statuses', () => {
    expect(normalizeToolStatus('running')).toBe('executing');
    expect(normalizeToolStatus('done')).toBe('success');
    expect(normalizeToolStatus('error')).toBe('failed');
  });

  it('accepts new statuses', () => {
    expect(normalizeToolStatus('waiting')).toBe('waiting');
    expect(normalizeToolStatus('executing')).toBe('executing');
  });

  it('labels waiting state for HITL', () => {
    expect(statusLabel('waiting')).toBe('待审批');
  });

  it('maps tool end verdict', () => {
    expect(toolEndStatus(false)).toBe('success');
    expect(toolEndStatus(true)).toBe('failed');
  });
});

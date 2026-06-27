import { describe, expect, it } from 'vitest';
import { formatAutoArchiveNotice } from './autoArchiveNotice';

describe('formatAutoArchiveNotice', () => {
  it('formats single archived session', () => {
    expect(
      formatAutoArchiveNotice([{ id: '1', title: 'Old task', archivedAt: '2026-01-01T00:00:00Z' }]),
    ).toContain('Old task');
  });

  it('formats multiple archived sessions', () => {
    expect(
      formatAutoArchiveNotice([
        { id: '1', title: 'A', archivedAt: '2026-01-01T00:00:00Z' },
        { id: '2', title: 'B', archivedAt: '2026-01-01T00:00:00Z' },
      ]),
    ).toContain('2');
  });
});

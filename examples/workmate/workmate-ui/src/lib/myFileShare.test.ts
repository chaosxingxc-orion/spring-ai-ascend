import { describe, expect, it } from 'vitest';
import { buildMyFileShareLink } from './myFileShare';

describe('buildMyFileShareLink', () => {
  it('includes session and file query', () => {
    const url = buildMyFileShareLink('http://localhost:5174', 'abc-123', 'reports/a.md');
    expect(url).toBe('http://localhost:5174/s/abc-123?file=reports%2Fa.md');
  });
});

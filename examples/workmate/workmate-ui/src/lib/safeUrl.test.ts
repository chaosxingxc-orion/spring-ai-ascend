import { describe, expect, it } from 'vitest';
import { faviconHost, safeHttpUrl } from './safeUrl';

describe('safeUrl', () => {
  it('allows http and https URLs', () => {
    expect(safeHttpUrl('https://example.com/path')).toBe('https://example.com/path');
    expect(safeHttpUrl('http://localhost:8080/')).toBe('http://localhost:8080/');
  });

  it('rejects javascript and data URLs', () => {
    expect(safeHttpUrl('javascript:alert(1)')).toBeUndefined();
    expect(safeHttpUrl('data:text/html,<script>alert(1)</script>')).toBeUndefined();
  });

  it('extracts favicon host', () => {
    expect(faviconHost('https://docs.example.com/page')).toBe('docs.example.com');
    expect(faviconHost('not-a-url')).toBeUndefined();
  });
});

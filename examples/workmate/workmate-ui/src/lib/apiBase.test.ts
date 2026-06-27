import { describe, expect, it } from 'vitest';
import { resolveApiAuthorizeUrl } from './apiBase';

describe('resolveApiAuthorizeUrl', () => {
  it('passes through absolute URLs', () => {
    expect(resolveApiAuthorizeUrl('https://auth.example.com/start')).toBe(
      'https://auth.example.com/start',
    );
  });

  it('prefixes relative paths with VITE_API_BASE', () => {
    expect(resolveApiAuthorizeUrl('/oauth/mock-authorize?state=1')).toContain(
      '/oauth/mock-authorize?state=1',
    );
  });
});

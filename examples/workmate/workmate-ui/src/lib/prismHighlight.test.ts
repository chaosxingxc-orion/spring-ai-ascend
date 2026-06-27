import { describe, expect, it } from 'vitest';
import { escapeHtml, highlightCode, normalizePrismLanguage } from './prismHighlight';

describe('prismHighlight', () => {
  it('normalizes common language aliases', () => {
    expect(normalizePrismLanguage('js')).toBe('javascript');
    expect(normalizePrismLanguage('py')).toBe('python');
    expect(normalizePrismLanguage('')).toBe('plain');
  });

  it('escapes unknown languages', () => {
    const html = highlightCode('<tag>', 'unknown-lang');
    expect(html).toContain('&lt;tag&gt;');
    expect(html).not.toContain('<tag>');
  });

  it('highlights javascript', () => {
    const html = highlightCode('const ok = true;', 'javascript');
    expect(html).toContain('token');
  });

  it('escapes html in escapeHtml', () => {
    expect(escapeHtml('<a>')).toBe('&lt;a&gt;');
  });
});

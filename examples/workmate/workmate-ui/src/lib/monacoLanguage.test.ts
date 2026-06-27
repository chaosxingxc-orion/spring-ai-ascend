import { describe, expect, it } from 'vitest';
import { monacoLanguageFromPath, preferMonacoEditor } from './monacoLanguage';

describe('monacoLanguage', () => {
  it('maps typescript extension', () => {
    expect(monacoLanguageFromPath('src/foo.ts')).toBe('typescript');
  });

  it('maps shell for bash', () => {
    expect(monacoLanguageFromPath('run.sh')).toBe('shell');
  });

  it('skips monaco for large content', () => {
    expect(preferMonacoEditor(300_000, false)).toBe(false);
    expect(preferMonacoEditor(1000, true)).toBe(false);
    expect(preferMonacoEditor(1000, false)).toBe(true);
  });
});

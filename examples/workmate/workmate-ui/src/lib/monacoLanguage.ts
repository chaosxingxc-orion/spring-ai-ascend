import { languageFromPath } from './fileLanguage';

const MONACO_LANGUAGE: Record<string, string> = {
  plain: 'plaintext',
  bash: 'shell',
  markdown: 'markdown',
  typescript: 'typescript',
  javascript: 'javascript',
  json: 'json',
  css: 'css',
  yaml: 'yaml',
  html: 'html',
  python: 'python',
};

export function monacoLanguageFromPath(path: string, mime?: string): string {
  const lang = languageFromPath(path, mime);
  return MONACO_LANGUAGE[lang] ?? lang;
}

export function preferMonacoEditor(contentLength: number, truncated: boolean): boolean {
  return !truncated && contentLength <= 200_000;
}

export function monacoEditorTheme(): 'vs' | 'vs-dark' {
  if (typeof window === 'undefined') {
    return 'vs';
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'vs-dark' : 'vs';
}

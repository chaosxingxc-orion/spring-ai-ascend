import Prism from 'prismjs';
import 'prismjs/components/prism-typescript';
import 'prismjs/components/prism-javascript';
import 'prismjs/components/prism-markdown';
import 'prismjs/components/prism-json';
import 'prismjs/components/prism-css';
import 'prismjs/components/prism-bash';
import 'prismjs/components/prism-yaml';
import 'prismjs/components/prism-python';
import 'prismjs/components/prism-java';
import 'prismjs/components/prism-markup';

const LANGUAGE_ALIASES: Record<string, string> = {
  js: 'javascript',
  ts: 'typescript',
  py: 'python',
  sh: 'bash',
  shell: 'bash',
  yml: 'yaml',
  md: 'markdown',
  html: 'markup',
  xml: 'markup',
};

export function normalizePrismLanguage(language: string): string {
  const trimmed = language.trim().toLowerCase();
  if (!trimmed) {
    return 'plain';
  }
  return LANGUAGE_ALIASES[trimmed] ?? trimmed;
}

export function sanitizeLanguageClass(language: string): string {
  const normalized = normalizePrismLanguage(language);
  return normalized.replace(/[^a-z0-9_-]/g, '') || 'plain';
}

export function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

export function highlightCode(source: string, language: string): string {
  const lang = normalizePrismLanguage(language);
  const grammar = Prism.languages[lang];
  if (!grammar) {
    return escapeHtml(source);
  }
  return Prism.highlight(source, grammar, lang);
}

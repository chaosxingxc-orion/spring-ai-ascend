import { escapeHtml, highlightCode, sanitizeLanguageClass } from './prismHighlight';

const URL_PATTERN = /https?:\/\/[^\s<>"')\]]+/g;
const WORKSPACE_FILE_PATTERN = /^[\w./-]+\.[\w.-]+$/;
const BARE_WORKSPACE_PATH_PATTERN = /\b((?:deliverables|team|uploads|outputs)\/[\w./-]+\.[\w.-]+)\b/g;

export interface SimpleMarkdownOptions {
  knownWorkspacePaths?: ReadonlySet<string>;
}

function isWorkspaceFilePath(value: string): boolean {
  if (!value || value.includes('://') || value.startsWith('/') || /\s/.test(value)) {
    return false;
  }
  return WORKSPACE_FILE_PATTERN.test(value);
}

function workspaceFileHtml(path: string, knownPaths?: ReadonlySet<string>): string {
  const safePath = escapeHtml(path);
  const known = !knownPaths || knownPaths.has(path);
  const className = known ? 'md-ws-file' : 'md-ws-file missing';
  const title = known ? path : `${path}（工作区中暂无此文件）`;
  return `<button type="button" class="${className}" data-ws-path="${safePath}" title="${escapeHtml(title)}">${safePath}</button>`;
}

function linkifyUrls(text: string): string {
  return text.replace(URL_PATTERN, (match) => {
    let url = match;
    let suffix = '';
    while (url.length > 0 && /[.,;:!?)]+$/.test(url)) {
      suffix = url.slice(-1) + suffix;
      url = url.slice(0, -1);
    }
    if (!url) {
      return match;
    }
    const safeUrl = escapeHtml(url);
    return `<a href="${safeUrl}" target="_blank" rel="noopener noreferrer">${safeUrl}</a>${suffix}`;
  });
}

function linkifyBareWorkspacePaths(text: string, knownPaths?: ReadonlySet<string>): string {
  return text.replace(BARE_WORKSPACE_PATH_PATTERN, (match) => workspaceFileHtml(match, knownPaths));
}

function formatTextSegment(text: string, options?: SimpleMarkdownOptions): string {
  let out = text;
  out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  out = out.replace(/\*([^*]+)\*/g, '<em>$1</em>');
  out = linkifyBareWorkspacePaths(out, options?.knownWorkspacePaths);
  return linkifyUrls(out);
}

function inlineFormat(text: string, options?: SimpleMarkdownOptions): string {
  const escaped = escapeHtml(text);
  return escaped
    .split(/(`[^`]+`)/g)
    .map((part) => {
      if (part.startsWith('`') && part.endsWith('`')) {
        const inner = part.slice(1, -1);
        if (isWorkspaceFilePath(inner)) {
          return workspaceFileHtml(inner, options?.knownWorkspacePaths);
        }
        return `<code>${inner}</code>`;
      }
      return formatTextSegment(part, options);
    })
    .join('');
}

function splitTableRow(line: string): string[] {
  const trimmed = line.trim();
  const inner = trimmed.startsWith('|') ? trimmed.slice(1) : trimmed;
  const withoutEnd = inner.endsWith('|') ? inner.slice(0, -1) : inner;
  return withoutEnd.split('|').map((cell) => cell.trim());
}

function isTableSeparator(cells: string[]): boolean {
  return cells.every((cell) => /^:?-{3,}:?$/.test(cell.replace(/\s/g, '')));
}

function parseTableBlock(
  lines: string[],
  start: number,
  options?: SimpleMarkdownOptions,
): { html: string; next: number } | null {
  if (!lines[start]?.includes('|')) {
    return null;
  }
  const headerCells = splitTableRow(lines[start]);
  if (headerCells.length < 2) {
    return null;
  }
  let rowIndex = start + 1;
  if (lines[rowIndex] && isTableSeparator(splitTableRow(lines[rowIndex]))) {
    rowIndex += 1;
  }
  const bodyRows: string[][] = [];
  while (rowIndex < lines.length && lines[rowIndex].includes('|')) {
    const cells = splitTableRow(lines[rowIndex]);
    if (!isTableSeparator(cells)) {
      while (cells.length < headerCells.length) {
        cells.push('');
      }
      bodyRows.push(cells.slice(0, headerCells.length));
    }
    rowIndex += 1;
  }
  if (bodyRows.length === 0) {
    return null;
  }
  const headHtml = headerCells.map((cell) => `<th>${inlineFormat(cell, options)}</th>`).join('');
  const bodyHtml = bodyRows
    .map((row) => `<tr>${row.map((cell) => `<td>${inlineFormat(cell, options)}</td>`).join('')}</tr>`)
    .join('');
  return {
    html: `<table class="md-table"><thead><tr>${headHtml}</tr></thead><tbody>${bodyHtml}</tbody></table>`,
    next: rowIndex,
  };
}

/**
 * A real Markdown heading is a short title. Streamed assistant narration often
 * emits a `## ...` marker followed by an entire run-on sentence (or several) on
 * the same physical line, which would otherwise render as a giant bold wall of
 * text. Treat such lines as prose paragraphs instead of headings.
 */
function isLikelyHeading(text: string): boolean {
  if (text.length > 48) {
    return false;
  }
  const body = text.replace(/[。．.!！?？]+$/u, '');
  return !/[。；;！!？?]/u.test(body);
}

function renderFencedCode(code: string, language: string): string {
  const langClass = sanitizeLanguageClass(language);
  const highlighted = highlightCode(code, language);
  return `<pre class="md-code"><code class="language-${langClass}">${highlighted}</code></pre>`;
}

/** Markdown → HTML（标题、列表、表格、引用、代码块 + 语法高亮、URL 链接化）。 */
export function simpleMarkdown(source: string, options?: SimpleMarkdownOptions): string {
  // Agents sometimes draw ASCII "card" borders with long runs of the heavy/double box-drawing
  // glyphs (━ U+2501 / ═ U+2550). When newlines around them are lost (or they are mis-fenced as a
  // code block) they collapse into a run-on wall of glyphs. Drop those decoration runs up front.
  // Only heavy/double glyphs are matched — the light box chars (─│├└) used by `tree`/code output
  // are intentionally left untouched.
  const lines = source
    .replace(/\r\n/g, '\n')
    .replace(/[\u2501\u2503\u2550\u2551]{2,}/g, '\n')
    // Status-card rows (✅/⏳/🔄/⬚/⬜/⏭️/📍/📊/⏱️/🎯/📌/📋/❌) often arrive jammed onto one line when
    // the model drops the newlines between them. Break before such a marker when it is stuck to the
    // preceding (non-space) character so each row renders on its own line.
    .replace(
      /(\S)([\u{1F4CB}\u{1F4CD}\u{1F4CA}\u{23F1}\u{1F3AF}\u{1F4CC}\u2705\u274C\u{23F3}\u{1F504}\u2B1A\u2B1C\u{23ED}])/gu,
      '$1\n$2',
    )
    .split('\n');
  const html: string[] = [];
  let inCode = false;
  let codeLang = '';
  let codeBuf: string[] = [];
  let listType: 'ul' | 'ol' | null = null;
  let index = 0;

  const closeList = () => {
    if (listType) {
      html.push(`</${listType}>`);
      listType = null;
    }
  };

  while (index < lines.length) {
    const line = lines[index];

    if (line.startsWith('```')) {
      if (inCode) {
        html.push(renderFencedCode(codeBuf.join('\n'), codeLang));
        codeBuf = [];
        codeLang = '';
        inCode = false;
      } else {
        closeList();
        codeLang = line.slice(3).trim();
        inCode = true;
      }
      index += 1;
      continue;
    }
    if (inCode) {
      codeBuf.push(line);
      index += 1;
      continue;
    }

    const table = parseTableBlock(lines, index, options);
    if (table) {
      closeList();
      html.push(table.html);
      index = table.next;
      continue;
    }

    const heading = line.match(/^(#{1,6})\s*(.+)$/);
    if (heading) {
      closeList();
      if (isLikelyHeading(heading[2])) {
        const level = heading[1].length;
        html.push(`<h${level}>${inlineFormat(heading[2], options)}</h${level}>`);
      } else {
        html.push(`<p>${inlineFormat(heading[2], options)}</p>`);
      }
      index += 1;
      continue;
    }

    const blockquote = line.match(/^>\s?(.*)$/);
    if (blockquote) {
      closeList();
      html.push(`<blockquote class="md-quote">${inlineFormat(blockquote[1], options)}</blockquote>`);
      index += 1;
      continue;
    }

    // Standard rules plus box-drawing decoration lines (━━━ / ─── / ═══) that agents emit as
    // ASCII card borders — render them as a single rule instead of a run-on wall of glyphs.
    const trimmedLine = line.trim();
    if (
      /^---+$/.test(trimmedLine)
      || /^\*\*\*+$/.test(trimmedLine)
      || /^[\u2500-\u257F]{3,}$/.test(trimmedLine)
    ) {
      closeList();
      if (html[html.length - 1] !== '<hr class="md-hr" />') {
        html.push('<hr class="md-hr" />');
      }
      index += 1;
      continue;
    }

    const unorderedItem = line.match(/^[-*]\s+(.+)$/);
    const orderedItem = line.match(/^\d+\.\s+(.+)$/);
    if (unorderedItem || orderedItem) {
      const wantType = orderedItem ? 'ol' : 'ul';
      if (listType && listType !== wantType) {
        closeList();
      }
      if (!listType) {
        html.push(`<${wantType}>`);
        listType = wantType;
      }
      const content = orderedItem ? orderedItem[1] : unorderedItem![1];
      html.push(`<li>${inlineFormat(content, options)}</li>`);
      index += 1;
      continue;
    }

    if (line.trim() === '') {
      closeList();
      index += 1;
      continue;
    }

    closeList();
    html.push(`<p>${inlineFormat(line, options)}</p>`);
    index += 1;
  }

  if (inCode && codeBuf.length > 0) {
    html.push(renderFencedCode(codeBuf.join('\n'), codeLang));
  }
  closeList();

  return html.join('');
}

/** 从 Markdown 正文或文件名提取报告标题。 */
export function extractReportTitle(content: string, fileName?: string): string {
  const match = content.replace(/\r\n/g, '\n').match(/^#\s+(.+)$/m);
  if (match?.[1]?.trim()) {
    return match[1].trim();
  }
  if (fileName) {
    return fileName.replace(/\.(md|markdown)$/i, '').replace(/[-_]/g, ' ');
  }
  return '业务报告';
}

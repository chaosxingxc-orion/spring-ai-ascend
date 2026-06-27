export interface StructuredTable {
  headers: string[];
  rows: string[][];
}

function splitMarkdownRow(line: string): string[] {
  const trimmed = line.trim();
  const inner = trimmed.startsWith('|') ? trimmed.slice(1) : trimmed;
  const withoutEnd = inner.endsWith('|') ? inner.slice(0, -1) : inner;
  return withoutEnd.split('|').map((cell) => cell.trim());
}

function isSeparatorRow(cells: string[]): boolean {
  return cells.every((cell) => /^:?-{3,}:?$/.test(cell.replace(/\s/g, '')));
}

/** 从 Markdown 表格块解析为结构化表（Canvas 化入口）。 */
export function parseMarkdownTable(text: string): StructuredTable | null {
  const lines = text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
  if (lines.length < 2 || !lines[0].includes('|')) {
    return null;
  }
  const headers = splitMarkdownRow(lines[0]);
  if (headers.length < 2) {
    return null;
  }
  const rows: string[][] = [];
  for (let i = 1; i < lines.length; i += 1) {
    const cells = splitMarkdownRow(lines[i]);
    if (cells.length === 0) {
      continue;
    }
    if (isSeparatorRow(cells)) {
      continue;
    }
    while (cells.length < headers.length) {
      cells.push('');
    }
    rows.push(cells.slice(0, headers.length));
  }
  return rows.length > 0 ? { headers, rows } : null;
}

/** 从 JSON 数组对象解析为结构化表。 */
export function parseJsonTable(text: string): StructuredTable | null {
  const trimmed = text.trim();
  if (!trimmed.startsWith('[')) {
    return null;
  }
  try {
    const data = JSON.parse(trimmed) as unknown;
    if (!Array.isArray(data) || data.length === 0) {
      return null;
    }
    const objects = data.filter((item) => item && typeof item === 'object' && !Array.isArray(item));
    if (objects.length === 0) {
      return null;
    }
    const headers = Object.keys(objects[0] as Record<string, unknown>);
    if (headers.length === 0) {
      return null;
    }
    const rows = objects.map((row) =>
      headers.map((key) => {
        const value = (row as Record<string, unknown>)[key];
        if (value == null) {
          return '';
        }
        return typeof value === 'string' ? value : JSON.stringify(value);
      }),
    );
    return { headers, rows };
  } catch {
    return null;
  }
}

/** 检测 preview 文本是否可渲染为业务数据表。 */
export function detectStructuredTable(text: string | undefined): StructuredTable | null {
  if (!text?.trim()) {
    return null;
  }
  const markdown = parseMarkdownTable(text);
  if (markdown) {
    return markdown;
  }
  return parseJsonTable(text);
}

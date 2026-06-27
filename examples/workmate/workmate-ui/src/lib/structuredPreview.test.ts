import { describe, expect, it } from 'vitest';
import { detectStructuredTable, parseMarkdownTable } from './structuredPreview';

describe('structuredPreview', () => {
  it('parses markdown table', () => {
    const table = parseMarkdownTable(
      '| 基金 | 回撤 | 排名 |\n| --- | --- | --- |\n| A | -12% | 3 |',
    );
    expect(table?.headers).toEqual(['基金', '回撤', '排名']);
    expect(table?.rows).toEqual([['A', '-12%', '3']]);
  });

  it('detects json array table', () => {
    const table = detectStructuredTable(
      '[{"name":"特斯拉","pe":45},{"name":"比亚迪","pe":22}]',
    );
    expect(table?.headers).toEqual(['name', 'pe']);
    expect(table?.rows).toHaveLength(2);
  });
});

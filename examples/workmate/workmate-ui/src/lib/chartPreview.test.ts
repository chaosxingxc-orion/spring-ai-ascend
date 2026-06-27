import { describe, expect, it } from 'vitest';
import { inferLineChart, parseNumericCell } from './chartPreview';
import type { StructuredTable } from './structuredPreview';

describe('chartPreview', () => {
  it('parses percent and comma numbers', () => {
    expect(parseNumericCell('-12.5%')).toBe(-12.5);
    expect(parseNumericCell('1,234')).toBe(1234);
  });

  it('infers line chart from fund table', () => {
    const table: StructuredTable = {
      headers: ['基金', '回撤率', '同类排名'],
      rows: [
        ['A 基金', '-8.2%', '12'],
        ['B 基金', '-5.1%', '8'],
        ['C 基金', '-11.4%', '20'],
      ],
    };
    const chart = inferLineChart(table);
    expect(chart?.labels).toEqual(['A 基金', 'B 基金', 'C 基金']);
    expect(chart?.series[0].name).toBe('回撤率');
    expect(chart?.series[0].values).toEqual([-8.2, -5.1, -11.4]);
  });
});

import type { StructuredTable } from './structuredPreview';

export interface ChartSeries {
  name: string;
  values: number[];
}

export interface LineChartData {
  labels: string[];
  series: ChartSeries[];
}

const CHART_COLUMN_HINT =
  /回撤|收益|涨幅|回报|波动|涨跌|净值|价格|pe|市盈率|rate|return|yield|drawdown/i;

/** 解析单元格数值（支持 %、千分位）。 */
export function parseNumericCell(raw: string): number | null {
  const trimmed = raw.trim();
  if (!trimmed) {
    return null;
  }
  const cleaned = trimmed.replace(/,/g, '').replace(/%$/, '').replace(/万$/, '0000').replace(/亿$/, '00000000');
  const value = Number(cleaned);
  return Number.isFinite(value) ? value : null;
}

/** 从结构化表推断折线图数据（首列标签 + 数值列）。 */
export function inferLineChart(table: StructuredTable): LineChartData | null {
  if (table.rows.length < 2 || table.headers.length < 2) {
    return null;
  }

  const labelIndex = 0;
  const labels = table.rows.map((row) => row[labelIndex] ?? '');

  const numericColumns: number[] = [];
  for (let col = 1; col < table.headers.length; col += 1) {
    const parsed = table.rows.map((row) => parseNumericCell(row[col] ?? ''));
    const numericCount = parsed.filter((value) => value !== null).length;
    if (numericCount >= Math.max(2, Math.ceil(table.rows.length * 0.6))) {
      numericColumns.push(col);
    }
  }

  if (numericColumns.length === 0) {
    return null;
  }

  const hinted = numericColumns.filter((col) => CHART_COLUMN_HINT.test(table.headers[col]));
  const selected = (hinted.length > 0 ? hinted : numericColumns).slice(0, 3);

  const series: ChartSeries[] = selected.map((col) => ({
    name: table.headers[col],
    values: table.rows.map((row) => parseNumericCell(row[col] ?? '') ?? 0),
  }));

  return { labels, series };
}

export function isChartableTable(table: StructuredTable): boolean {
  return inferLineChart(table) !== null;
}

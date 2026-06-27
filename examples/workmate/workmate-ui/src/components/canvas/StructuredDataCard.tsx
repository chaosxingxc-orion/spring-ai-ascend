import { useMemo, useState } from 'react';
import { inferLineChart } from '../../lib/chartPreview';
import type { StructuredTable } from '../../lib/structuredPreview';
import { SimpleLineChart } from './SimpleLineChart';

interface StructuredDataCardProps {
  title?: string;
  table: StructuredTable;
  fallbackText?: string;
}

/** 黑板/工件结构化数据 — 表格 + 折线图切换（Canvas W29.2）。 */
export function StructuredDataCard({ title, table, fallbackText }: StructuredDataCardProps) {
  const chartData = useMemo(() => inferLineChart(table), [table]);
  const [view, setView] = useState<'chart' | 'table'>(chartData ? 'chart' : 'table');

  return (
    <section className="structured-data-card" aria-label={title ?? '数据看板'}>
      {title && <header className="structured-data-title">{title}</header>}
      {chartData && (
        <div className="structured-data-view-toggle" role="tablist" aria-label="数据视图">
          <button
            type="button"
            role="tab"
            aria-selected={view === 'chart'}
            className={`structured-data-view-btn${view === 'chart' ? ' active' : ''}`}
            onClick={() => setView('chart')}
          >
            折线图
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={view === 'table'}
            className={`structured-data-view-btn${view === 'table' ? ' active' : ''}`}
            onClick={() => setView('table')}
          >
            表格
          </button>
        </div>
      )}
      {view === 'chart' && chartData ? (
        <SimpleLineChart data={chartData} />
      ) : (
        <div className="structured-data-scroll">
          <table className="structured-data-table">
            <thead>
              <tr>
                {table.headers.map((header) => (
                  <th key={header}>{header}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {table.rows.map((row, rowIndex) => (
                <tr key={`row-${rowIndex}`}>
                  {row.map((cell, cellIndex) => (
                    <td key={`${rowIndex}-${cellIndex}`}>{cell}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {fallbackText && <p className="structured-data-fallback muted">{fallbackText}</p>}
    </section>
  );
}

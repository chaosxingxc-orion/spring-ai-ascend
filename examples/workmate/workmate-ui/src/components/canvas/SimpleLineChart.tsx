import type { LineChartData } from '../../lib/chartPreview';

const SERIES_COLORS = ['#CF0A2C', '#2D2D2D', '#ff9f0a'];

interface SimpleLineChartProps {
  data: LineChartData;
  height?: number;
}

function chartGeometry(
  data: LineChartData,
  width: number,
  height: number,
  padding: { top: number; right: number; bottom: number; left: number },
) {
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;
  const allValues = data.series.flatMap((series) => series.values);
  const min = Math.min(...allValues);
  const max = Math.max(...allValues);
  const span = max - min === 0 ? 1 : max - min;
  const labelCount = data.labels.length;
  const xStep = labelCount > 1 ? plotWidth / (labelCount - 1) : 0;

  const pointsForSeries = data.series.map((series) =>
    series.values.map((value, index) => {
      const x = padding.left + xStep * index;
      const y = padding.top + plotHeight - ((value - min) / span) * plotHeight;
      return { x, y, value };
    }),
  );

  return { min, max, plotWidth, plotHeight, padding, pointsForSeries, xStep };
}

/** 轻量 SVG 折线图 — 无第三方图表库。 */
export function SimpleLineChart({ data, height = 220 }: SimpleLineChartProps) {
  const width = 520;
  const padding = { top: 16, right: 12, bottom: 36, left: 44 };
  const { min, max, pointsForSeries, padding: pad, xStep } = chartGeometry(data, width, height, padding);

  return (
    <figure className="simple-line-chart" aria-label="折线图">
      <svg
        viewBox={`0 0 ${width} ${height}`}
        className="simple-line-chart-svg"
        role="img"
      >
        <line
          x1={pad.left}
          y1={pad.top}
          x2={pad.left}
          y2={height - pad.bottom}
          className="simple-line-chart-axis"
        />
        <line
          x1={pad.left}
          y1={height - pad.bottom}
          x2={width - pad.right}
          y2={height - pad.bottom}
          className="simple-line-chart-axis"
        />
        <text x={pad.left - 6} y={pad.top + 4} className="simple-line-chart-tick" textAnchor="end">
          {max.toFixed(1)}
        </text>
        <text
          x={pad.left - 6}
          y={height - pad.bottom}
          className="simple-line-chart-tick"
          textAnchor="end"
        >
          {min.toFixed(1)}
        </text>
        {data.labels.map((label, index) => {
          const x = pad.left + xStep * index;
          return (
            <text
              key={`${label}-${index}`}
              x={x}
              y={height - 8}
              className="simple-line-chart-label"
              textAnchor="middle"
            >
              {label.length > 8 ? `${label.slice(0, 7)}…` : label}
            </text>
          );
        })}
        {pointsForSeries.map((points, seriesIndex) => {
          const color = SERIES_COLORS[seriesIndex % SERIES_COLORS.length];
          const polyline = points.map((point) => `${point.x},${point.y}`).join(' ');
          return (
            <g key={data.series[seriesIndex].name}>
              <polyline
                points={polyline}
                fill="none"
                stroke={color}
                strokeWidth="2.5"
                strokeLinejoin="round"
                strokeLinecap="round"
              />
              {points.map((point, pointIndex) => (
                <circle
                  key={`${seriesIndex}-${pointIndex}`}
                  cx={point.x}
                  cy={point.y}
                  r="3.5"
                  fill={color}
                />
              ))}
            </g>
          );
        })}
      </svg>
      <figcaption className="simple-line-chart-legend">
        {data.series.map((series, index) => (
          <span key={series.name} className="simple-line-chart-legend-item">
            <span
              className="simple-line-chart-legend-dot"
              style={{ background: SERIES_COLORS[index % SERIES_COLORS.length] }}
              aria-hidden
            />
            {series.name}
          </span>
        ))}
      </figcaption>
    </figure>
  );
}

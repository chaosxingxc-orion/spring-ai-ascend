import type { ToolStatus } from '../../types/events';
import { parseWebSearchResults, webSearchQueryFromArgs } from '../../lib/toolKind';
import { faviconHost, safeHttpUrl } from '../../lib/safeUrl';
import { isToolFailed, isToolInProgress, statusLabel } from './shared';

const MAX_VISIBLE = 6;

interface WebSearchToolCardProps {
  status: ToolStatus;
  args?: unknown;
  result?: unknown;
}

export function WebSearchToolCard({ status, args, result }: WebSearchToolCardProps) {
  const query = webSearchQueryFromArgs(args);
  const hits = parseWebSearchResults(result, args);
  const visible = hits.slice(0, MAX_VISIBLE);
  const hiddenCount = hits.length - visible.length;

  return (
    <article className={`tool-card tool-card-web-search status-${status}`}>
      <header className="tool-card-header">
        <span className="tool-icon" aria-hidden>🌐</span>
        <span className="tool-name">网页搜索</span>
        <span className="tool-path">{query ?? '—'}</span>
        <span className={`tool-status status-${status}`}>{statusLabel(status)}</span>
      </header>
      {isToolInProgress(status) && query && (
        <p className="tool-card-hint muted">正在搜索「{query}」…</p>
      )}
      {!isToolInProgress(status) && hits.length > 0 && (
        <ul className="tool-web-ref-list">
          {visible.map((hit) => {
            const safeUrl = safeHttpUrl(hit.url);
            const host = safeUrl ? faviconHost(safeUrl) : undefined;
            return (
              <li key={hit.url} className="tool-web-ref-item">
                <span className="tool-web-ref-favicon" aria-hidden>
                  {host ? host.slice(0, 1).toUpperCase() : '↗'}
                </span>
                <div className="tool-web-ref-body">
                  <a
                    className="tool-web-ref-title"
                    href={safeUrl ?? undefined}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {hit.title}
                  </a>
                  {host && <span className="tool-web-ref-host muted">{host}</span>}
                  {hit.snippet && <p className="tool-web-ref-snippet muted">{hit.snippet}</p>}
                </div>
              </li>
            );
          })}
        </ul>
      )}
      {hiddenCount > 0 && (
        <p className="tool-card-hint muted">还有 {hiddenCount} 条结果未展示</p>
      )}
      {isToolFailed(status) && hits.length === 0 && (
        <p className="tool-card-hint muted">搜索未返回可用引用</p>
      )}
    </article>
  );
}

import type { ToolStatus } from '../../types/events';
import { parseWebFetchPreview, webFetchUrlFromArgs } from '../../lib/toolKind';
import { faviconHost, safeHttpUrl } from '../../lib/safeUrl';
import { isToolFailed, isToolInProgress, statusLabel } from './shared';

interface WebFetchToolCardProps {
  status: ToolStatus;
  args?: unknown;
  result?: unknown;
}

export function WebFetchToolCard({ status, args, result }: WebFetchToolCardProps) {
  const preview = parseWebFetchPreview(result, args);
  const url = preview?.url ?? webFetchUrlFromArgs(args);
  const safeUrl = url ? safeHttpUrl(url) : undefined;
  const host = safeUrl ? faviconHost(safeUrl) : undefined;

  return (
    <article className={`tool-card tool-card-web-fetch status-${status}`}>
      <header className="tool-card-header">
        <span className="tool-icon" aria-hidden>🌐</span>
        <span className="tool-name">网页获取</span>
        <span className="tool-path">{host ?? url ?? '—'}</span>
        <span className={`tool-status status-${status}`}>{statusLabel(status)}</span>
      </header>
      {isToolInProgress(status) && url && (
        <p className="tool-card-hint muted">正在获取 {url}…</p>
      )}
      {!isToolInProgress(status) && url && (
        <div className="tool-web-fetch-body">
          <a className="tool-web-ref-title" href={safeUrl ?? undefined} target="_blank" rel="noopener noreferrer">
            {preview?.title ?? url}
          </a>
          {host && <span className="tool-web-ref-host muted">{host}</span>}
          {preview?.snippet && (
            <p className="tool-web-ref-snippet muted">{preview.snippet}</p>
          )}
        </div>
      )}
      {isToolFailed(status) && !url && (
        <p className="tool-card-hint muted">未能解析网页 URL</p>
      )}
    </article>
  );
}

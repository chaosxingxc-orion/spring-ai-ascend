import type { ToolStatus } from '../../types/events';
import { readPathFromArgs } from '../../lib/toolKind';
import { isToolFailed, isToolInProgress, statusLabel, formatJson } from './shared';

interface ReadToolCardProps {
  status: ToolStatus;
  args?: unknown;
  result?: unknown;
}

export function ReadToolCard({ status, args, result }: ReadToolCardProps) {
  const path = readPathFromArgs(args);

  return (
    <article className={`tool-card tool-card-read status-${status}`}>
      <header className="tool-card-header">
        <span className="tool-icon" aria-hidden>📄</span>
        <span className="tool-name">读取文件</span>
        {path && <span className="tool-path" title={path}>{path}</span>}
        <span className={`tool-status status-${status}`}>{statusLabel(status)}</span>
      </header>
      {result !== undefined && !isToolInProgress(status) && (
        <details open={isToolFailed(status)}>
          <summary>内容</summary>
          <pre>{formatJson(result)}</pre>
        </details>
      )}
    </article>
  );
}

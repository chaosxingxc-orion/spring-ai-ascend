import type { ToolStatus } from '../../types/events';
import { formatToolName } from '../../lib/toolDisplay';
import { isToolInProgress, statusLabel, formatJson } from './shared';

interface DefaultToolCardProps {
  toolName: string;
  status: ToolStatus;
  args?: unknown;
  result?: unknown;
}

export function DefaultToolCard({ toolName, status, args, result }: DefaultToolCardProps) {
  return (
    <article className={`tool-card status-${status}`}>
      <header className="tool-card-header">
        <span className="tool-icon">⚙</span>
        <span className="tool-name">{formatToolName(toolName)}</span>
        <span className={`tool-status status-${status}`}>{statusLabel(status)}</span>
      </header>
      {args !== undefined && (
        <details open={isToolInProgress(status)}>
          <summary>参数</summary>
          <pre>{formatJson(args)}</pre>
        </details>
      )}
      {result !== undefined && !isToolInProgress(status) && (
        <details open>
          <summary>结果</summary>
          <pre>{formatJson(result)}</pre>
        </details>
      )}
    </article>
  );
}

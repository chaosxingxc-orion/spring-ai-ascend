import type { ToolStatus } from '../../types/events';
import { parseMcpToolName } from '../../lib/toolKind';
import { TERM } from '../../lib/terminology';
import { isToolFailed, isToolInProgress, statusLabel, formatJson } from './shared';

interface McpToolCardProps {
  toolName: string;
  status: ToolStatus;
  args?: unknown;
  result?: unknown;
}

export function McpToolCard({ toolName, status, args, result }: McpToolCardProps) {
  const { server, tool } = parseMcpToolName(toolName);
  const label = tool ?? toolName;

  return (
    <article className={`tool-card tool-card-mcp status-${status}`}>
      <header className="tool-card-header">
        <span className="tool-icon" aria-hidden>🌐</span>
        <span className="tool-name">{label}</span>
        {server && (
          <span className="tool-mcp-badge">{TERM.runtimeMcp} · {server}</span>
        )}
        <span className={`tool-status status-${status}`}>{statusLabel(status)}</span>
      </header>
      {args !== undefined && (
        <details open={isToolInProgress(status)}>
          <summary>参数</summary>
          <pre>{formatJson(args)}</pre>
        </details>
      )}
      {result !== undefined && !isToolInProgress(status) && (
        <details open={isToolFailed(status)}>
          <summary>结果</summary>
          <pre>{formatJson(result)}</pre>
        </details>
      )}
    </article>
  );
}

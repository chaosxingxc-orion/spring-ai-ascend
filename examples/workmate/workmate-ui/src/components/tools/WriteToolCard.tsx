import type { ToolStatus } from '../../types/events';
import { writeDiffSummary, writePreviewFromArgs } from '../../lib/toolKind';
import { isToolFailed, isToolInProgress, statusLabel, formatJson } from './shared';

interface WriteToolCardProps {
  status: ToolStatus;
  args?: unknown;
  result?: unknown;
  onOpenChanges?: (path: string) => void;
}

export function WriteToolCard({ status, args, result, onOpenChanges }: WriteToolCardProps) {
  const { path, preview } = writePreviewFromArgs(args);
  const summary = !isToolInProgress(status) && !isToolFailed(status)
    ? writeDiffSummary(args, result)
    : null;

  return (
    <article className={`tool-card tool-card-write status-${status}`}>
      <header className="tool-card-header">
        <span className="tool-icon" aria-hidden>✎</span>
        <span className="tool-name">写入文件</span>
        {path && <span className="tool-path" title={path}>{path}</span>}
        <span className={`tool-status status-${status}`}>{statusLabel(status)}</span>
      </header>
      {summary && (
        onOpenChanges ? (
          <button
            type="button"
            className="tool-write-diff-summary"
            onClick={() => onOpenChanges(summary.path)}
          >
            {summary.label}
          </button>
        ) : (
          <p className="tool-write-diff-summary static">{summary.label}</p>
        )
      )}
      {preview && (
        <pre className="tool-card-preview">{preview}</pre>
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

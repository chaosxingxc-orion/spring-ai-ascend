import { useState } from 'react';
import type { ToolStatus } from '../../types/events';
import { parseFilePathsFromListResult } from '../../lib/toolKind';
import { isToolFailed, isToolInProgress, statusLabel } from './shared';

const MAX_VISIBLE = 8;

interface ListFilesToolCardProps {
  status: ToolStatus;
  args?: unknown;
  result?: unknown;
}

export function ListFilesToolCard({ status, args, result }: ListFilesToolCardProps) {
  const paths = parseFilePathsFromListResult(result, args);
  const [expanded, setExpanded] = useState(paths.length <= MAX_VISIBLE);
  const visible = expanded ? paths : paths.slice(0, MAX_VISIBLE);
  const hiddenCount = paths.length - visible.length;

  return (
    <article className={`tool-card tool-card-list status-${status}`}>
      <header className="tool-card-header">
        <span className="tool-icon" aria-hidden>📁</span>
        <span className="tool-name">列出文件</span>
        <span className="tool-path">{paths.length > 0 ? `${paths.length} 项` : '—'}</span>
        <span className={`tool-status status-${status}`}>{statusLabel(status)}</span>
      </header>
      {!isToolInProgress(status) && paths.length > 0 && (
        <ul className="tool-file-list">
          {visible.map((path) => (
            <li key={path} className="tool-file-list-item" title={path}>
              {path}
            </li>
          ))}
        </ul>
      )}
      {hiddenCount > 0 && (
        <button type="button" className="btn ghost sm tool-file-list-more" onClick={() => setExpanded(true)}>
          还有 {hiddenCount} 个…
        </button>
      )}
      {isToolFailed(status) && paths.length === 0 && (
        <p className="tool-card-hint muted">未能解析文件列表</p>
      )}
    </article>
  );
}

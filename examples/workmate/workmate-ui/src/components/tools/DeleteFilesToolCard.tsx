import type { ToolStatus } from '../../types/events';
import { parseDeletePaths } from '../../lib/toolKind';
import { isToolFailed, isToolInProgress, statusLabel } from './shared';

interface DeleteFilesToolCardProps {
  status: ToolStatus;
  args?: unknown;
  result?: unknown;
}

export function DeleteFilesToolCard({ status, args, result }: DeleteFilesToolCardProps) {
  const paths = parseDeletePaths(args, result);

  return (
    <article className={`tool-card tool-card-delete status-${status}`}>
      <header className="tool-card-header">
        <span className="tool-icon" aria-hidden>🗑</span>
        <span className="tool-name">删除文件</span>
        <span className={`tool-status status-${status}`}>{statusLabel(status)}</span>
      </header>
      {!isToolInProgress(status) && paths.length > 0 && (
        <ul className="tool-file-list tool-file-list-delete">
          {paths.map((path) => (
            <li key={path} className="tool-file-list-item" title={path}>
              {path}
            </li>
          ))}
        </ul>
      )}
      {isToolFailed(status) && (
        <p className="tool-card-hint tool-card-delete-warning">删除操作失败</p>
      )}
    </article>
  );
}

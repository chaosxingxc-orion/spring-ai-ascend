import { useState } from 'react';
import type { ToolStatus } from '../../types/events';
import { bashCommandFromArgs } from '../../lib/toolKind';
import { isToolFailed, isToolInProgress, statusLabel, formatJson } from './shared';

interface BashToolCardProps {
  status: ToolStatus;
  args?: unknown;
  result?: unknown;
}

/** WB-D03 — 运行命令折叠终端 */
export function BashToolCard({ status, args, result }: BashToolCardProps) {
  const command = bashCommandFromArgs(args) ?? '（无命令）';
  const [expanded, setExpanded] = useState(isToolInProgress(status));

  return (
    <article className={`command-run-block status-${status}`}>
      <button
        type="button"
        className="command-run-header"
        aria-expanded={expanded}
        onClick={() => setExpanded((prev) => !prev)}
      >
        <span className="command-run-icon" aria-hidden>▸</span>
        <span className="command-run-title">运行命令</span>
        <span className={`command-run-status status-${status}`}>{statusLabel(status)}</span>
        <span className="command-run-chevron" aria-hidden>{expanded ? '▾' : '▸'}</span>
      </button>
      {expanded && (
        <div className="command-run-body">
          <pre className="command-run-terminal">{command}</pre>
          {result !== undefined && !isToolInProgress(status) && (
            <details className="command-run-result" open={isToolFailed(status)}>
              <summary>输出</summary>
              <pre>{formatJson(result)}</pre>
            </details>
          )}
        </div>
      )}
    </article>
  );
}

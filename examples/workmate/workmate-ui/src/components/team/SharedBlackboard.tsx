import { useState } from 'react';
import type { TeamUiLabels } from '../../lib/teamUiLabels';
import type { TeamState } from '../../lib/teamStatus';
import { MemoryPreviewBlock } from '../canvas/MemoryPreviewBlock';

interface SharedBlackboardProps {
  team: TeamState;
  labels: TeamUiLabels;
}

/** shared-state 拓扑：共享黑板 + 贡献流 + 终止进度（W27）。 */
export function SharedBlackboard({ team, labels }: SharedBlackboardProps) {
  const ss = team.ss;
  const entries = team.memoryEntries ?? [];
  const [expanded, setExpanded] = useState(false);

  const iteration = ss?.iteration ?? 0;
  const maxIter = ss?.maxIterations ?? 4;
  const streak = ss?.convergenceStreak ?? 0;
  const target = ss?.convergenceTarget ?? 0;
  const version = ss?.blackboardVersion ?? 0;
  const converged = ss?.converged ?? false;

  const progressPct = maxIter > 0 ? Math.min(100, Math.round((iteration / maxIter) * 100)) : 0;

  return (
    <section className="team-shared-state" aria-label={labels.sharedStateTitle}>
      <header className="team-shared-state-head">
        <span className="team-shared-state-title">{labels.sharedStateTitle}</span>
        {converged && <span className="team-shared-state-badge converged">已收敛</span>}
        {version > 0 && <span className="team-shared-state-version">v{version}</span>}
      </header>

      {team.blackboardPath && (
        <code className="team-shared-state-path">{team.blackboardPath}</code>
      )}

      <div className="team-shared-state-progress" aria-label="协作进度">
        <div className="team-shared-state-progress-row">
          <span>轮次 {iteration > 0 ? `${iteration}/${maxIter}` : `最多 ${maxIter}`}</span>
          {target > 0 && (
            <span className="team-shared-state-convergence">
              收敛 {streak}/{target}
            </span>
          )}
        </div>
        <div className="team-shared-state-bar" aria-hidden>
          <div className="team-shared-state-bar-fill" style={{ width: `${progressPct}%` }} />
        </div>
      </div>

      {entries.length > 0 && (
        <button
          type="button"
          className="team-shared-state-toggle"
          aria-expanded={expanded}
          onClick={() => setExpanded((v) => !v)}
        >
          {expanded ? '收起贡献' : `查看贡献 (${entries.length})`}
        </button>
      )}

      {expanded && entries.length > 0 && (
        <ol className="team-shared-state-entries">
          {entries.map((entry, idx) => (
            <li key={`${entry.section}-${idx}`} className="team-shared-state-entry">
              <MemoryPreviewBlock section={entry.section} preview={entry.preview} />
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

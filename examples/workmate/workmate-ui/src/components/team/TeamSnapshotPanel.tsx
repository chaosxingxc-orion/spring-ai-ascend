import { useEffect, useState } from 'react';
import { getTeamSnapshot } from '../../api/client';
import type { TeamSnapshot } from '../../types/api';

interface TeamSnapshotPanelProps {
  sessionId: string;
}

function backendLabel(type: string): string {
  if (type === 'remote') {
    return '远程 runtime';
  }
  if (type === 'local') {
    return '同 JVM';
  }
  return type;
}

/** 只读团队配置快照（expert descriptor + run_events 派生，不双写）。 */
export function TeamSnapshotPanel({ sessionId }: TeamSnapshotPanelProps) {
  const [open, setOpen] = useState(true);
  const [snapshot, setSnapshot] = useState<TeamSnapshot | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    setError(null);
    void getTeamSnapshot(sessionId)
      .then(setSnapshot)
      .catch((err) => setError((err as Error).message))
      .finally(() => setLoading(false));
  }, [sessionId]);

  return (
    <section className="team-snapshot" aria-label="团队快照">
      <button
        type="button"
        className="team-snapshot-toggle"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <span>团队快照</span>
        <span className="team-snapshot-meta">
          {snapshot?.source === 'expert-descriptor+run-events' ? '含运行态' : '配置态'}
        </span>
        <span aria-hidden>{open ? '▾' : '▸'}</span>
      </button>
      {open && (
        <div className="team-snapshot-body">
          {loading && <p className="detail-hint muted">加载快照…</p>}
          {error && <p className="detail-hint error">{error}</p>}
          {snapshot && !loading && (
            <>
              <div className="team-snapshot-summary">
                <strong>{snapshot.teamName}</strong>
                <span className="team-snapshot-pattern">{snapshot.pattern ?? snapshot.collaboration}</span>
                {snapshot.teamPromptSummary && (
                  <p className="team-snapshot-prompt">{snapshot.teamPromptSummary}</p>
                )}
              </div>
              <ul className="team-snapshot-members">
                {snapshot.members.map((member) => (
                  <li key={member.memberId} className="team-snapshot-member">
                    <div className="team-snapshot-member-head">
                      <span className="team-snapshot-avatar" aria-hidden>
                        {member.avatar?.trim() || member.name.slice(0, 1)}
                      </span>
                      <div>
                        <span className="team-snapshot-name">{member.name}</span>
                        {member.role && <span className="team-snapshot-role">{member.role}</span>}
                      </div>
                      <span className="team-snapshot-backend">{backendLabel(member.backendType)}</span>
                    </div>
                    {member.promptSummary && (
                      <p className="team-snapshot-member-prompt">{member.promptSummary}</p>
                    )}
                    {member.subscriptions.length > 0 && (
                      <p className="team-snapshot-subscriptions">
                        订阅：{member.subscriptions.join(', ')}
                      </p>
                    )}
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}
    </section>
  );
}

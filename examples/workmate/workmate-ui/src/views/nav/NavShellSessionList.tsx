import type { Session } from '../../types/api';
import { formatRelativeTime } from '../../lib/formatRelativeTime';

interface NavShellSessionListProps {
  sessions: Session[];
  emptyLabel?: string;
  onSelect: (sessionId: string) => void;
}

/** 侧栏 Nav 壳层页 — 紧凑任务列表 */
export function NavShellSessionList({
  sessions,
  emptyLabel = '暂无任务',
  onSelect,
}: NavShellSessionListProps) {
  if (sessions.length === 0) {
    return <p className="nav-shell-empty muted">{emptyLabel}</p>;
  }

  return (
    <ul className="nav-shell-session-list">
      {sessions.map((session) => (
        <li key={session.id}>
          <button type="button" className="nav-shell-session-item" onClick={() => onSelect(session.id)}>
            <span className="nav-shell-session-title">{session.title}</span>
            <span className="nav-shell-session-meta muted">
              {formatRelativeTime(new Date(session.updatedAt).getTime())}
            </span>
          </button>
        </li>
      ))}
    </ul>
  );
}

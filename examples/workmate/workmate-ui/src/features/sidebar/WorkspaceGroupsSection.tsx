import type { Session } from '../../types/api';
import type { WorkspacePreset } from '../../types/workspace';
import { formatRelativeTime } from '../../lib/formatRelativeTime';
import { groupSessionsByWorkspace } from '../../lib/sessionWorkspace';

interface WorkspaceGroupsSectionProps {
  sessions: Session[];
  workspacePresets: WorkspacePreset[];
  activeId: string | null;
  open: boolean;
  onToggle: () => void;
  onSelect: (id: string) => void;
}

export function WorkspaceGroupsSection({
  sessions,
  workspacePresets,
  activeId,
  open,
  onToggle,
  onSelect,
}: WorkspaceGroupsSectionProps) {
  const workspaceGroups = groupSessionsByWorkspace(sessions, workspacePresets);
  if (workspaceGroups.length === 0) {
    return null;
  }

  return (
    <section className="workspace-groups">
      <button
        type="button"
        className="workspace-groups-toggle"
        onClick={onToggle}
        aria-expanded={open}
      >
        空间 ({workspaceGroups.length})
        <span className="workspace-groups-chevron">{open ? '▼' : '▶'}</span>
      </button>
      {open &&
        workspaceGroups.map((group) => (
          <div key={group.preset.id} className="workspace-group">
            <header className="workspace-group-header">
              <span className="workspace-group-name">{group.preset.name}</span>
              <span className="workspace-group-count">{group.sessions.length}</span>
            </header>
            <ul className="workspace-group-list">
              {group.sessions.map((session) => (
                <li key={session.id}>
                  <button
                    type="button"
                    className={`workspace-group-item${activeId === session.id ? ' active' : ''}`}
                    onClick={() => onSelect(session.id)}
                  >
                    <span className="workspace-group-item-title">
                      {session.title || '未命名任务'}
                    </span>
                    <span className="workspace-group-item-meta">
                      {formatRelativeTime(session.updatedAt)}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        ))}
    </section>
  );
}

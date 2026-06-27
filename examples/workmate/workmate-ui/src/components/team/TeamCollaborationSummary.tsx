import { useState } from 'react';
import type { TeamState } from '../../lib/teamStatus';
import type { TeamStepperMemberRef } from './TeamStepper';

interface TeamCollaborationSummaryProps {
  team: TeamState;
  blackboardPath?: string | null;
  onSelectMember?: (member: TeamStepperMemberRef) => void;
  onOpenBlackboard?: (path: string) => void;
}

function statusLabel(status: string): string {
  switch (status) {
    case 'running':
      return '进行中';
    case 'completed':
      return '已完成';
    case 'error':
      return '失败';
    default:
      return '待命';
  }
}

/** Collapsible team run recap shown after collaboration completes. */
export function TeamCollaborationSummary({
  team,
  blackboardPath,
  onSelectMember,
  onOpenBlackboard,
}: TeamCollaborationSummaryProps) {
  const [collapsed, setCollapsed] = useState(true);

  if (team.phase !== 'done' || team.members.length === 0) {
    return null;
  }

  const completedCount = team.members.filter((member) => member.status === 'completed').length;

  return (
    <section className="team-run-summary" aria-label="团队协作回顾">
      <header className="team-run-summary-head">
        <button
          type="button"
          className="team-run-summary-toggle"
          aria-expanded={!collapsed}
          onClick={() => setCollapsed((open) => !open)}
        >
          <span className="team-run-summary-chevron" aria-hidden>{collapsed ? '▸' : '▾'}</span>
          团队协作回顾 · {completedCount}/{team.members.length} 名成员已完成
        </button>
        {blackboardPath && onOpenBlackboard && (
          <button
            type="button"
            className="team-run-summary-blackboard-btn"
            onClick={() => onOpenBlackboard(blackboardPath)}
          >
            打开团队黑板
          </button>
        )}
      </header>
      {!collapsed && (
        <ul className="team-run-summary-list">
          {team.members.map((member) => (
            <li key={member.id} className={`team-run-summary-item status-${member.status}`}>
              {onSelectMember ? (
                <button
                  type="button"
                  className="team-run-summary-member-btn"
                  onClick={() => onSelectMember({ id: member.id, name: member.name })}
                >
                  <span className="team-run-summary-member-name">{member.name}</span>
                  <span className="team-run-summary-member-status">{statusLabel(member.status)}</span>
                </button>
              ) : (
                <>
                  <span className="team-run-summary-member-name">{member.name}</span>
                  <span className="team-run-summary-member-status">{statusLabel(member.status)}</span>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

import type { TeamUiLabels } from '../../lib/teamUiLabels';
import type { TeamState } from '../../lib/teamStatus';
import { teamProgress } from '../../lib/teamStatus';
import type { TeamMemberStatus } from '../TeamStatusRow';

interface AgentTeamGridProps {
  team: TeamState;
  labels: TeamUiLabels;
}

function statusText(status: TeamMemberStatus): string {
  switch (status) {
    case 'running':
      return '执行中';
    case 'completed':
      return '已完成';
    case 'paused':
      return '已暂停';
    case 'not-scheduled':
      return '未调度';
    case 'error':
      return '失败';
    default:
      return '待命';
  }
}

/** agent-team 拓扑：并行成员网格 + 轻量 coordinator 整合节点。 */
export function AgentTeamGrid({ team, labels }: AgentTeamGridProps) {
  const { done, total } = teamProgress(team);
  const synthesizing = team.phase === 'synthesizing';
  const completed = team.phase === 'done';
  const leadAvatar = team.lead?.avatar?.trim() || '🧭';

  return (
    <section className="team-agent-grid" aria-label={labels.agentTeamTitle}>
      <header className="team-agent-grid-head">
        <span className="team-agent-grid-title">{labels.agentTeamTitle}</span>
        <span className="team-agent-grid-progress">进度 {done}/{total}</span>
      </header>
      <div className="team-agent-grid-pool">
        {team.members.map((member, index) => (
          <div key={member.id} className={`team-agent-grid-card status-${member.status}`}>
            <span className="team-agent-grid-order" aria-hidden>{member.order ?? index + 1}</span>
            <span className="team-agent-grid-avatar" aria-hidden>
              {member.avatar?.trim() || member.name.slice(0, 1)}
            </span>
            <span className="team-agent-grid-name">{member.name}</span>
            <span className={`team-agent-grid-status status-${member.status}`}>
              {statusText(member.status)}
            </span>
          </div>
        ))}
      </div>
      {team.lead && (
        <div className={`team-agent-grid-coordinator ${synthesizing ? 'status-running' : completed ? 'status-completed' : ''}`}>
          <span className="team-agent-grid-avatar" aria-hidden>{leadAvatar}</span>
          <span className="team-agent-grid-coordinator-label">
            {team.lead.name} · {synthesizing ? '整合中…' : completed ? '已整合' : '待命整合'}
          </span>
        </div>
      )}
    </section>
  );
}

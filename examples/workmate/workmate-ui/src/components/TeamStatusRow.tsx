import type { TeamState } from '../lib/teamStatus';
import { delegationOverviewMembers, isTeamDelegationSession } from '../lib/teamStatus';

export type TeamMemberStatus = 'running' | 'completed' | 'idle' | 'paused' | 'not-scheduled' | 'error';

export interface TeamMember {
  id: string;
  name: string;
  status: TeamMemberStatus;
  role?: string;
  profession?: string;
  order?: number;
  avatar?: string;
  summary?: string;
  promptTokens?: number;
  completionTokens?: number;
  hasStarted?: boolean;
  firstStartedSeq?: number;
  lastCompletedSeq?: number;
  startCount?: number;
  completedCount?: number;
  pausedCount?: number;
  reawakenedCount?: number;
  toolCalls?: number;
  errorCount?: number;
}

function statusLabel(status: TeamMemberStatus, delegation: boolean): string {
  switch (status) {
    case 'running':
      return '进行中';
    case 'completed':
      return delegation ? '已完成' : '已完成';
    case 'paused':
      return '已暂停';
    case 'not-scheduled':
      return '未调度';
    case 'error':
      return '失败';
    default:
      return delegation ? '待命' : '等待';
  }
}

function memberMetrics(member: TeamMember): string | null {
  const parts: string[] = [];
  if (member.firstStartedSeq != null) {
    if (member.lastCompletedSeq != null) {
      parts.push(`#${member.firstStartedSeq}→#${member.lastCompletedSeq}`);
    } else {
      parts.push(`启动 #${member.firstStartedSeq}`);
    }
  } else if (member.status === 'not-scheduled') {
    parts.push('本轮未调度');
  }
  if ((member.toolCalls ?? 0) > 0) {
    parts.push(`工具 ${member.toolCalls}`);
  }
  if ((member.errorCount ?? 0) > 0) {
    parts.push(`错误 ${member.errorCount}`);
  }
  return parts.length > 0 ? parts.join(' · ') : null;
}

function memberAvatar(member: TeamMember): string {
  const avatar = member.avatar?.trim();
  if (avatar) {
    return avatar.slice(0, 2);
  }
  return member.name.slice(0, 1);
}

function RunningSparkle() {
  return (
    <svg className="team-sparkle" width="14" height="14" viewBox="0 0 14 14" aria-hidden>
      <path
        d="M7 1l1.2 3.8L12 6l-3.8 1.2L7 11 5.8 7.2 2 6l3.8-1.2L7 1z"
        fill="currentColor"
      />
    </svg>
  );
}

interface TeamStatusRowProps {
  team: TeamState | null | undefined;
  expertTeamRuntime?: string | null;
  selectedMemberId?: string | null;
  onSelectMember?: (member: TeamMember) => void;
  onOpenCommandCenter?: () => void;
}

/** 右栏窄侧栏「团队信息」—— OverviewTeamSection / W39-B2。 */
export function TeamStatusRow({
  team,
  expertTeamRuntime,
  selectedMemberId = null,
  onSelectMember,
  onOpenCommandCenter,
}: TeamStatusRowProps) {
  if (!team || team.members.length === 0) {
    return null;
  }

  const delegation = isTeamDelegationSession(team, expertTeamRuntime);
  const visibleMembers = delegation ? delegationOverviewMembers(team) : team.members;
  const showLead = delegation && team.lead != null;

  return (
    <section className="team-status-row overview-team-section" aria-label="团队信息">
      <header className="detail-section-label">团队信息</header>
      <ul className="team-status-list">
        {showLead && (
          <li className="team-status-item">
            <div className="team-status-member-btn static">
              <span className="team-status-avatar" aria-hidden>
                {team.lead?.avatar?.trim() || '🧭'}
              </span>
              <span className="team-status-text">
                <span className="team-status-name">{team.lead?.name}</span>
                <span className="team-status-label">{team.lead?.title ?? '主理人'}</span>
              </span>
            </div>
          </li>
        )}
        {visibleMembers.map((member) => {
          const recent = team.recentlyCompletedMemberIds?.includes(member.id) ?? false;
          const visualStatus: TeamMemberStatus =
            member.status === 'running'
              ? 'running'
              : recent
                ? 'completed'
                : member.status;
          const metrics = memberMetrics(member);
          return (
            <li key={member.id} className="team-status-item">
              <button
                type="button"
                className={[
                  'team-status-member-btn',
                  selectedMemberId === member.id ? 'active' : '',
                  visualStatus === 'running' ? 'status-running' : '',
                  visualStatus === 'completed' ? 'status-completed' : '',
                ].filter(Boolean).join(' ')}
                onClick={() => onSelectMember?.(member)}
                disabled={!onSelectMember}
                title={onSelectMember ? `查看 ${member.name} 轨迹` : member.name}
              >
                <span className={`team-status-avatar ${visualStatus === 'running' ? 'spinning' : ''}`} aria-hidden>
                  {memberAvatar(member)}
                  {visualStatus === 'completed' && <span className="team-delegation-check" aria-hidden>✓</span>}
                </span>
                <span className="team-status-text">
                  <span className="team-status-name">{member.name}</span>
                  <span className="team-status-label">{member.role ?? statusLabel(visualStatus, delegation)}</span>
                  {metrics && (
                    <span className="team-status-metrics">{metrics}</span>
                  )}
                </span>
                {visualStatus === 'running' && <RunningSparkle />}
              </button>
            </li>
          );
        })}
        {delegation && visibleMembers.length === 0 && team.phase === 'running' && (
          <li className="team-status-item team-status-idle">成员待命</li>
        )}
      </ul>
      {onOpenCommandCenter && !delegation && (
        <button
          type="button"
          className="btn ghost sm team-status-detail-btn"
          onClick={onOpenCommandCenter}
        >
          指挥中心
        </button>
      )}
    </section>
  );
}

export { TeamStatusRow as OverviewTeamSection };

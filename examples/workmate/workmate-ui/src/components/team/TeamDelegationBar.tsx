import type { ReactNode } from 'react';
import type { TeamState } from '../../lib/teamStatus';
import { delegationBarMembers, patternHasLead } from '../../lib/teamStatus';
import type { TeamStepperMemberRef } from './TeamStepper';

interface TeamDelegationBarProps {
  team: TeamState;
  onSelectMember?: (member: TeamStepperMemberRef) => void;
  /** When set, the matching member pill is rendered as the focused/active view. */
  activeMemberId?: string | null;
  /** Invoked when the leader pill is clicked (e.g. to return to the main view). */
  onSelectLead?: () => void;
  /** Compact actions on the meta row (e.g. @member bypass toggle). */
  trailing?: ReactNode;
}

function memberInitial(name: string, avatar?: string): string {
  return avatar && avatar.trim() ? avatar : name.slice(0, 1);
}

type PillVisualStatus = 'idle' | 'running' | 'completed' | 'paused' | 'not-scheduled' | 'error';

function pillClass(status: PillVisualStatus): string {
  return `team-delegation-pill status-${status}`;
}

function memberPillStatus(
  memberId: string,
  memberStatus: string,
  activeIds: Set<string>,
  recentIds: Set<string>,
): PillVisualStatus {
  if (memberStatus === 'error') {
    return 'error';
  }
  if (memberStatus === 'running' || activeIds.has(memberId)) {
    return 'running';
  }
  if (memberStatus === 'paused') {
    return 'paused';
  }
  if (memberStatus === 'not-scheduled') {
    return 'not-scheduled';
  }
  if (recentIds.has(memberId)) {
    return 'completed';
  }
  if (memberStatus === 'completed') {
    return 'completed';
  }
  return 'idle';
}

function statusAriaLabel(status: PillVisualStatus): string {
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

/** reference-style active member bar for openjiuwen TeamAgent runs. */
export function TeamDelegationBar({
  team,
  onSelectMember,
  activeMemberId = null,
  onSelectLead,
  trailing,
}: TeamDelegationBarProps) {
  const showLead = patternHasLead(team.pattern) && team.lead != null;
  const activeIds = new Set(team.activeMemberIds ?? []);
  const recentIds = new Set(team.recentlyCompletedMemberIds ?? []);
  const visibleMembers = delegationBarMembers(team);
  const hasContent = showLead || visibleMembers.length > 0 || team.teamBuild != null;
  if (!hasContent) {
    return null;
  }

  const leadFocused = activeMemberId == null;
  const statusLabel = team.teamBuild
    ? (team.phase === 'running'
      ? '协作中'
      : team.phase === 'synthesizing'
        ? '汇总中'
        : team.phase === 'done'
          ? '已完成'
          : '待命')
    : '等待派工';
  const runningCount = visibleMembers.filter((member) =>
    memberPillStatus(member.id, member.status, activeIds, recentIds) === 'running').length;

  return (
    <section className="team-delegation-bar" aria-label="团队协作状态">
      <div className="team-delegation-meta">
        <div className="team-delegation-meta-left">
          <span className="team-delegation-phase">{statusLabel}</span>
          <span className="team-delegation-count">活跃 {runningCount}/{visibleMembers.length}</span>
        </div>
        {trailing}
      </div>
      <div className="team-delegation-pills">
        {showLead && team.lead && (
          <button
            type="button"
            className={`${pillClass(team.phase === 'running' ? 'running' : 'idle')}${onSelectLead ? ' clickable' : ''}${leadFocused ? ' focused' : ''}`}
            onClick={onSelectLead}
            disabled={!onSelectLead}
            aria-pressed={leadFocused}
          >
            <span className="team-delegation-avatar lead" aria-hidden>
              {team.lead.avatar?.trim() || '🧭'}
            </span>
            <span className="team-delegation-text">
              <span className="team-delegation-role">{team.lead.title ?? '主理人'}</span>
              <span className="team-delegation-name">{team.lead.name}</span>
            </span>
          </button>
        )}
        {visibleMembers.map((member) => {
          const visual = memberPillStatus(member.id, member.status, activeIds, recentIds);
          const role = member.role ?? member.profession ?? '成员';
          const focused = activeMemberId === member.id;
          return (
            <button
              key={member.id}
              type="button"
              className={`${pillClass(visual)} clickable${focused ? ' focused' : ''}`}
              onClick={() => onSelectMember?.({ id: member.id, name: member.name })}
              aria-pressed={focused}
              aria-label={`${role} · ${member.name} · ${statusAriaLabel(visual)}`}
            >
              <span
                className={`team-delegation-avatar ${visual === 'running' ? 'spinning' : ''}`}
                aria-hidden
              >
                {memberInitial(member.name, member.avatar)}
              </span>
              <span className="team-delegation-text">
                <span className="team-delegation-role">{role}</span>
                <span className="team-delegation-name">{member.name}</span>
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}

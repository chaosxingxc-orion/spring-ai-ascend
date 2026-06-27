import { useEffect, useState } from 'react';
import type { TeamState } from '../../lib/teamStatus';
import { patternHasLead, teamProgress } from '../../lib/teamStatus';
import type { TeamMemberStatus } from '../TeamStatusRow';

export interface TeamStepperMemberRef {
  id: string;
  name: string;
}

interface TeamStepperProps {
  team: TeamState | null | undefined;
  defaultCollapsed?: boolean;
  onSelectMember?: (member: TeamStepperMemberRef) => void;
}

function memberInitial(name: string, avatar?: string): string {
  return avatar && avatar.trim() ? avatar : name.slice(0, 1);
}

function statusText(status: TeamMemberStatus): string {
  switch (status) {
    case 'running':
      return '进行中';
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

function patternLabel(team: TeamState): string {
  switch (team.pattern) {
    case 'agent-team':
      return '并行';
    case 'pipeline':
      return '流水线';
    case 'generator-verifier':
      return '生成校验';
    case 'message-bus':
      return '事件驱动';
    case 'shared-state':
      return '共享协作';
    default:
      return team.collaboration === 'parallel' ? '并行' : '顺序';
  }
}

function memberNodeClass(
  base: string,
  status: TeamMemberStatus | 'idle' | 'running' | 'completed',
  clickable: boolean,
): string {
  return [
    base,
    `status-${status}`,
    clickable ? 'clickable' : '',
  ].filter(Boolean).join(' ');
}

/** 中栏顶部「协作流程」stepper：orchestrator 拓扑下团长派活 → 成员顺序 → 团长整合。 */
export function TeamStepper({ team, defaultCollapsed = false, onSelectMember }: TeamStepperProps) {
  const [collapsed, setCollapsed] = useState(defaultCollapsed);

  useEffect(() => {
    setCollapsed(defaultCollapsed);
  }, [defaultCollapsed]);

  if (!team || team.members.length === 0) {
    return null;
  }

  const { done, total } = teamProgress(team);
  const showLead = patternHasLead(team.pattern) && team.lead != null;
  const leadAvatar = team.lead?.avatar?.trim() || '🧭';
  const leadName = team.lead?.name?.trim() || '团长';
  const synthesizing = team.phase === 'synthesizing';
  const completed = team.phase === 'done';
  const memberClickable = Boolean(onSelectMember);

  return (
    <section className="team-stepper" aria-label="团队协作流程">
      <header className="team-stepper-head">
        <button
          type="button"
          className="team-stepper-toggle"
          aria-expanded={!collapsed}
          onClick={() => setCollapsed((v) => !v)}
        >
          <span className="team-stepper-chevron" aria-hidden>{collapsed ? '▸' : '▾'}</span>
          团队协作 · {patternLabel(team)}
        </button>
        <span className="team-stepper-progress">进度 {done}/{total}</span>
      </header>

      {!collapsed && (
        <div className="team-stepper-track">
          {showLead && (
            <div className="team-stepper-node lead">
              <span className="team-node-avatar lead-avatar" aria-hidden>{leadAvatar}</span>
              <span className="team-node-name" title={leadName}>{leadName}</span>
              <span className="team-node-badge">负责人</span>
            </div>
          )}

          {team.members.map((member, index) => {
            const node = (
              <>
                <span className="team-node-index" aria-hidden>{member.order ?? index + 1}</span>
                <span className="team-node-avatar" aria-hidden>
                  {memberInitial(member.name, member.avatar)}
                </span>
                <span className="team-node-name" title={member.role ?? member.name}>{member.name}</span>
                <span className={`team-node-status status-${member.status}`}>
                  <span className="team-node-dot" aria-hidden />
                  {statusText(member.status)}
                </span>
              </>
            );
            return (
              <div className="team-stepper-seg" key={member.id}>
                {(showLead || index > 0) && <span className="team-stepper-arrow" aria-hidden>→</span>}
                {memberClickable ? (
                  <button
                    type="button"
                    className={memberNodeClass('team-stepper-node member', member.status, true)}
                    title={`查看 ${member.name} 轨迹`}
                    onClick={() => onSelectMember?.({ id: member.id, name: member.name })}
                  >
                    {node}
                  </button>
                ) : (
                  <div className={memberNodeClass('team-stepper-node member', member.status, false)}>
                    {node}
                  </div>
                )}
              </div>
            );
          })}

          {showLead && (
            <div className="team-stepper-seg">
              <span className="team-stepper-arrow" aria-hidden>→</span>
              <div
                className={memberNodeClass(
                  'team-stepper-node lead synth',
                  synthesizing ? 'running' : completed ? 'completed' : 'idle',
                  false,
                )}
              >
                <span className="team-node-avatar lead-avatar" aria-hidden>{leadAvatar}</span>
                <span className="team-node-name" title={leadName}>{leadName} · 整合</span>
                <span className="team-node-status">
                  <span className="team-node-dot" aria-hidden />
                  {synthesizing ? '进行中' : completed ? '已完成' : '待命'}
                </span>
              </div>
            </div>
          )}
        </div>
      )}
    </section>
  );
}

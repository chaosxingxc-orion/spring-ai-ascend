import { useMemo } from 'react';
import type { ChatItem } from '../../types/events';
import type { TeamState } from '../../lib/teamStatus';
import { formatNumber } from '../../lib/formatLocale';

interface MemberFocusMetadataBarProps {
  memberId: string;
  memberName: string;
  team: TeamState;
  items: ChatItem[];
  streaming?: boolean;
}

function formatTokenCount(value: number): string {
  if (value >= 1000) {
    return `${(value / 1000).toFixed(1).replace(/\.0$/, '')}k`;
  }
  return formatNumber(value);
}

function memberWorkStatus(
  team: TeamState,
  memberId: string,
  streaming: boolean,
): 'running' | 'completed' | 'idle' {
  const member = team.members.find((entry) => entry.id === memberId);
  const active = team.activeMemberIds?.includes(memberId) ?? false;
  if (member?.status === 'running' || active || streaming) {
    return 'running';
  }
  if (member?.status === 'completed' || team.recentlyCompletedMemberIds?.includes(memberId)) {
    return 'completed';
  }
  return 'idle';
}

/** the reference workbench member sub-session header: team id, tool calls, token usage, work status. */
export function MemberFocusMetadataBar({
  memberId,
  memberName,
  team,
  items,
  streaming = false,
}: MemberFocusMetadataBarProps) {
  const member = team.members.find((entry) => entry.id === memberId);
  const toolCalls = useMemo(
    () => items.filter((item) => item.kind === 'tool').length,
    [items],
  );
  const promptTokens = member?.promptTokens ?? 0;
  const completionTokens = member?.completionTokens ?? 0;
  const status = memberWorkStatus(team, memberId, streaming);
  const teamLabel = team.teamBuild?.teamName ?? team.teamBuild?.displayName ?? '—';
  const updatedAt = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });

  return (
    <section className="member-focus-metadata" aria-label={`${memberName} 工作区元数据`}>
      <div className="member-focus-metadata-primary">
        <h2 className="member-focus-metadata-name">{memberName}</h2>
        <span className={`member-focus-metadata-status status-${status}`}>
          {status === 'running' ? '进行中' : status === 'completed' ? '已完成工作' : '待命'}
        </span>
      </div>
      <dl className="member-focus-metadata-stats">
        <div>
          <dt>团队</dt>
          <dd>{teamLabel}</dd>
        </div>
        <div>
          <dt>更新时间</dt>
          <dd>{updatedAt}</dd>
        </div>
        <div>
          <dt>工具调用</dt>
          <dd>{toolCalls} 次</dd>
        </div>
        {(promptTokens > 0 || completionTokens > 0) && (
          <div>
            <dt>Token</dt>
            <dd>
              in {formatTokenCount(promptTokens)} · out {formatTokenCount(completionTokens)}
            </dd>
          </div>
        )}
      </dl>
    </section>
  );
}

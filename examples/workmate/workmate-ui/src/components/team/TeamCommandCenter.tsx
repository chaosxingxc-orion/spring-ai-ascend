import { useState } from 'react';
import type { TeamUiLabels } from '../../lib/teamUiLabels';
import type { GeneratorVerifierRound, TeamState } from '../../lib/teamStatus';
import { teamTotalTokens } from '../../lib/teamStatus';
import { MemoryPreviewBlock } from '../canvas/MemoryPreviewBlock';
import { TeamSnapshotPanel } from './TeamSnapshotPanel';
import type { TeamMember, TeamMemberStatus } from '../TeamStatusRow';

interface TeamCommandCenterProps {
  sessionId?: string;
  team: TeamState | null | undefined;
  labels?: TeamUiLabels;
}

function GvRoundRow({
  round,
  isLast,
  phase,
  labels,
}: {
  round: GeneratorVerifierRound;
  isLast: boolean;
  phase: string;
  labels: TeamUiLabels;
}) {
  const [expanded, setExpanded] = useState(false);
  const pending = !round.verdict && isLast;
  const verdictLabel = round.verdict === 'accepted'
    ? labels.gvRoundVerdictAccepted
    : round.verdict === 'rejected'
      ? labels.gvRoundVerdictRejected
      : pending
        ? (phase === 'verifying' ? labels.gvVerifying : labels.gvGenerating)
        : '—';
  const verdictClass = round.verdict === 'accepted'
    ? 'accepted'
    : round.verdict === 'rejected'
      ? 'rejected'
      : 'pending';
  const hasDetail = Boolean((round.generateSummary && round.generateSummary.trim()) || (round.feedback && round.feedback.trim()));
  return (
    <li className={`team-cc-round verdict-${verdictClass}`}>
      <button
        type="button"
        className="team-cc-round-head"
        aria-expanded={expanded}
        disabled={!hasDetail}
        onClick={() => setExpanded((v) => !v)}
      >
        <span className="team-cc-round-no" aria-hidden>第 {round.iteration} 轮</span>
        <span className={`team-cc-round-verdict verdict-${verdictClass}`}>
          {round.verdict === 'rejected' && round.programmatic && (
            <span className="team-cc-round-gate" title="可编程门禁驳回（非 LLM）">门禁</span>
          )}
          {verdictLabel}
        </span>
        {hasDetail && <span className="team-cc-round-chevron" aria-hidden>{expanded ? '▾' : '▸'}</span>}
      </button>
      {expanded && hasDetail && (
        <div className="team-cc-round-detail">
          {round.generateSummary && round.generateSummary.trim() && (
            <div className="team-cc-round-block">
              <span className="team-cc-round-label">{labels.gvGeneratorBadge}摘要</span>
              <p className="team-cc-round-text">{round.generateSummary}</p>
            </div>
          )}
          {round.feedback && round.feedback.trim() && (
            <div className="team-cc-round-block">
              <span className="team-cc-round-label">{labels.gvVerifierBadge}反馈</span>
              <p className="team-cc-round-text rejected">{round.feedback}</p>
            </div>
          )}
        </div>
      )}
    </li>
  );
}

function statusMark(status: TeamMemberStatus): string {
  switch (status) {
    case 'completed':
      return '✓';
    case 'paused':
      return '⏸';
    case 'not-scheduled':
      return '·';
    case 'error':
      return '✗';
    case 'running':
      return '●';
    default:
      return '○';
  }
}

function memberTokens(member: TeamMember): number {
  return (member.promptTokens ?? 0) + (member.completionTokens ?? 0);
}

function MemberRow({ member, index }: { member: TeamMember; index: number }) {
  const [expanded, setExpanded] = useState(false);
  const tokens = memberTokens(member);
  const hasSummary = Boolean(member.summary && member.summary.trim());
  return (
    <li className={`team-cc-member status-${member.status}`}>
      <div className="team-cc-member-head">
        <span className="team-cc-order" aria-hidden>{member.order ?? index + 1}</span>
        <span className="team-cc-avatar" aria-hidden>
          {member.avatar?.trim() || member.name.slice(0, 1)}
        </span>
        <div className="team-cc-member-meta">
          <span className="team-cc-member-name">{member.name}</span>
          {member.profession && !member.name.includes('·') && (
            <span className="team-cc-member-role">{member.profession}</span>
          )}
          {member.role && !member.profession && (
            <span className="team-cc-member-role">{member.role}</span>
          )}
        </div>
        <span className={`team-cc-status status-${member.status}`} aria-label={member.status}>
          {statusMark(member.status)}
        </span>
        {tokens > 0 && <span className="team-cc-tokens">~{tokens} tok</span>}
      </div>
      {hasSummary && (
        <button
          type="button"
          className="team-cc-summary"
          aria-expanded={expanded}
          onClick={() => setExpanded((v) => !v)}
        >
          <span className="team-cc-summary-chevron" aria-hidden>{expanded ? '▾' : '▸'}</span>
          <span className={expanded ? 'team-cc-summary-text expanded' : 'team-cc-summary-text'}>
            {member.summary}
          </span>
        </button>
      )}
    </li>
  );
}

/** 右栏「团队」Tab —— 团队指挥中心：团长 + 成员角色/产出/分账。 */
export function TeamCommandCenter({ sessionId, team, labels }: TeamCommandCenterProps) {
  if (!team) {
    return (
      <section className="team-cc" aria-label="团队指挥中心">
        <header className="detail-section-label">团队指挥中心</header>
        <p className="detail-hint muted">单专家任务，无团队协作。</p>
      </section>
    );
  }

  const ui = labels;
  const total = teamTotalTokens(team);
  const showLead = team.lead != null;
  const blackboardTitle = ui?.sharedStateTitle ?? '共享黑板';

  return (
    <section className="team-cc" aria-label="团队指挥中心">
      <header className="detail-section-label">团队指挥中心</header>
      {sessionId && <TeamSnapshotPanel sessionId={sessionId} />}

      {showLead && (
        <div className="team-cc-lead">
          <span className="team-cc-lead-avatar" aria-hidden>{team.lead?.avatar?.trim() || '🧭'}</span>
          <div className="team-cc-lead-meta">
            <span className="team-cc-lead-name">{team.lead?.name ?? '团长'}</span>
            <span className="team-cc-lead-badge">{team.lead?.title ?? '团队负责人'}</span>
          </div>
          <span className="team-cc-lead-status">
            {team.anyMemberFailed && team.phase === 'done' ? (
              <span className="team-cc-lead-mark" aria-hidden>⚠</span>
            ) : team.lead?.status === 'done' ? (
              <span className="team-cc-lead-mark" aria-hidden>✓</span>
            ) : null}
            {team.lead?.status === 'done'
              ? '已整合'
              : team.lead?.status === 'synthesizing'
                ? '整合中…'
                : '待命'}
          </span>
        </div>
      )}

      {team.pattern === 'generator-verifier' && team.gv && ui && (
        <div className="team-cc-gv-hint">
          {ui.gvTitle} · 轮次 {team.gv.iteration > 0 ? `${team.gv.iteration}/${team.gv.maxIterations}` : `最多 ${team.gv.maxIterations} 轮`}
          {team.gv.rounds.length > 0 && (
            <ol className="team-cc-rounds" aria-label="合规审查审计日志">
              {team.gv.rounds.map((round, idx) => (
                <GvRoundRow
                  key={round.iteration}
                  round={round}
                  isLast={idx === team.gv!.rounds.length - 1}
                  phase={team.gv!.phase}
                  labels={ui}
                />
              ))}
            </ol>
          )}
        </div>
      )}

      {team.blackboardPath && (
        <div className="team-cc-blackboard" aria-label={blackboardTitle}>
          <div className="team-cc-blackboard-head">
            <span className="team-cc-blackboard-label">{blackboardTitle}</span>
            <code className="team-cc-blackboard-path">{team.blackboardPath}</code>
          </div>
          {team.memoryEntries && team.memoryEntries.length > 0 && (
            <ol className="team-cc-memory-entries">
              {team.memoryEntries.map((entry, idx) => (
                <li key={`${entry.section}-${idx}`} className="team-cc-memory-entry">
                  <MemoryPreviewBlock section={entry.section} preview={entry.preview} />
                </li>
              ))}
            </ol>
          )}
        </div>
      )}

      <ul className="team-cc-members">
        {team.members.map((member, index) => (
          <MemberRow key={member.id} member={member} index={index} />
        ))}
      </ul>

      <footer className="team-cc-footer">
        <span className="team-cc-total">
          团队合计 {total > 0 ? `~${total} tok` : '统计中…'}
        </span>
        <span className="team-cc-baseline" title="专家团整体消耗通常为单专家的 3–5 倍（本机实测约 3.36×）">
          专家团约 3–5× 单专家 ⓘ
        </span>
      </footer>
    </section>
  );
}

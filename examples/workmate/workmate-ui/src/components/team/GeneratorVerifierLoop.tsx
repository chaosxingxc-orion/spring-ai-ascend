import { useState } from 'react';
import type { TeamUiLabels } from '../../lib/teamUiLabels';
import type { TeamState } from '../../lib/teamStatus';
import type { TeamMemberStatus } from '../TeamStatusRow';

interface GeneratorVerifierLoopProps {
  team: TeamState;
  labels: TeamUiLabels;
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

function gvPhaseLabel(phase: TeamState['gv'], labels: TeamUiLabels): string {
  if (!phase) {
    return '待命';
  }
  switch (phase.phase) {
    case 'generating':
      return labels.gvGenerating;
    case 'verifying':
      return labels.gvVerifying;
    case 'accepted':
      return labels.gvAccepted;
    case 'rejected':
      return labels.gvRejected;
    default:
      return '待命';
  }
}

/** generator-verifier 拓扑：撰写 ↔ 质控 ping-pong，无团长。 */
export function GeneratorVerifierLoop({ team, labels }: GeneratorVerifierLoopProps) {
  const [collapsed, setCollapsed] = useState(false);
  const gv = team.gv;
  const generator = team.members.find((m) => m.id === gv?.generatorId) ?? team.members[0];
  const verifier = team.members.find((m) => m.id === gv?.verifierId) ?? team.members[1];
  if (!generator || !verifier) {
    return null;
  }

  const iteration = gv?.iteration ?? 0;
  const maxIterations = gv?.maxIterations ?? 3;
  const gvPhase = gv?.phase ?? 'idle';
  const generatorActive = gvPhase === 'generating';
  const verifierActive = gvPhase === 'verifying';
  const accepted = gvPhase === 'accepted';
  const rejected = gvPhase === 'rejected';

  return (
    <section className="team-gv-loop" aria-label={labels.gvTitle}>
      <header className="team-gv-head">
        <button
          type="button"
          className="team-gv-toggle"
          aria-expanded={!collapsed}
          onClick={() => setCollapsed((v) => !v)}
        >
          <span className="team-gv-chevron" aria-hidden>{collapsed ? '▸' : '▾'}</span>
          团队协作 · {labels.gvTitle}
        </button>
        {iteration > 0 && (
          <span className="team-gv-iteration">
            轮次 {iteration}/{maxIterations} · {gvPhaseLabel(gv, labels)}
          </span>
        )}
        {rejected && (
          <span className="team-gv-rejected-badge" role="status">{labels.gvRejected}</span>
        )}
      </header>

      {!collapsed && (
        <div className="team-gv-track">
          <div
            className={`team-gv-node generator status-${generatorActive ? 'running' : generator.status} ${accepted ? 'status-completed' : ''}`}
          >
            <span className="team-gv-badge">{labels.gvGeneratorBadge}</span>
            <span className="team-gv-avatar" aria-hidden>
              {memberInitial(generator.name, generator.avatar)}
            </span>
            <span className="team-gv-name">{generator.name}</span>
            <span className={`team-gv-status status-${generatorActive ? 'running' : generator.status}`}>
              <span className="team-gv-dot" aria-hidden />
              {generatorActive ? labels.gvGenerating : statusText(generator.status)}
            </span>
          </div>

          <div className="team-gv-ping" aria-hidden>
            <span className={`team-gv-arrow ${generatorActive || rejected ? 'active' : ''}`}>→</span>
            <span className={`team-gv-arrow back ${verifierActive || rejected ? 'active' : ''}`}>←</span>
            {rejected && gv?.lastFeedback && (
              <span className="team-gv-feedback" title={gv.lastFeedback}>修订</span>
            )}
          </div>

          <div
            className={`team-gv-node verifier status-${verifierActive ? 'running' : verifier.status} ${accepted ? 'status-completed' : rejected ? 'status-error' : ''}`}
          >
            <span className="team-gv-badge">{labels.gvVerifierBadge}</span>
            <span className="team-gv-avatar" aria-hidden>
              {memberInitial(verifier.name, verifier.avatar)}
            </span>
            <span className="team-gv-name">{verifier.name}</span>
            <span
              className={`team-gv-status status-${verifierActive ? 'running' : accepted ? 'completed' : rejected ? 'error' : verifier.status}`}
            >
              <span className="team-gv-dot" aria-hidden />
              {verifierActive
                ? labels.gvVerifying
                : accepted
                  ? labels.gvAccepted
                  : rejected
                    ? labels.gvRejected
                    : statusText(verifier.status)}
            </span>
          </div>
        </div>
      )}
    </section>
  );
}

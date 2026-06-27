import type { ToolStatus } from '../../types/events';
import { parseSkillPreview, skillProgressFromResult } from '../../lib/toolKind';
import { isToolFailed, isToolInProgress, statusLabel } from './shared';

interface SkillProgressToolCardProps {
  toolName: string;
  status: ToolStatus;
  args?: unknown;
  result?: unknown;
}

/** R8 — Skill 工具：名称 + 运行进度 */
export function SkillProgressToolCard({
  toolName,
  status,
  args,
  result,
}: SkillProgressToolCardProps) {
  const preview = parseSkillPreview(args, toolName);
  const progress = skillProgressFromResult(result);
  const inProgress = isToolInProgress(status);
  const failed = isToolFailed(status);
  const percent = progress?.percent;

  return (
    <article className={`tool-card tool-card-skill status-${status}`}>
      <header className="tool-card-header">
        <span className="tool-icon" aria-hidden>✦</span>
        <span className="tool-name">{preview.name}</span>
        <span className="tool-skill-badge">技能</span>
        <span className={`tool-status status-${status}`}>{statusLabel(status)}</span>
      </header>
      {preview.detail && <p className="tool-card-skill-detail">{preview.detail}</p>}
      {inProgress && (
        <div className="tool-card-skill-progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent ?? undefined}>
          <div
            className={`tool-card-skill-progress-bar${percent == null ? ' indeterminate' : ''}`}
            style={percent != null ? { width: `${Math.min(100, Math.max(0, percent))}%` } : undefined}
          />
        </div>
      )}
      {!inProgress && !failed && progress?.message && (
        <p className="tool-card-skill-done">{progress.message}</p>
      )}
      {failed && progress?.message && (
        <p className="tool-card-skill-error">{progress.message}</p>
      )}
    </article>
  );
}

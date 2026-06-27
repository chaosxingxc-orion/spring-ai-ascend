import type { ToolStatus } from '../../types/events';
import { isToolFailed, isToolInProgress } from './shared';

interface TeamBuildToolCardProps {
  status: ToolStatus;
  args?: unknown;
  result?: unknown;
}

function readStringField(source: Record<string, unknown>, ...keys: string[]): string | undefined {
  for (const key of keys) {
    const value = source[key];
    if (typeof value === 'string' && value.trim()) {
      return value.trim();
    }
  }
  return undefined;
}

function teamBuildPreview(args?: unknown, result?: unknown): { displayName?: string; teamDesc?: string } {
  const fromArgs = args && typeof args === 'object' ? (args as Record<string, unknown>) : {};
  const fromResult =
    result && typeof result === 'object'
      ? ((result as Record<string, unknown>).data as Record<string, unknown> | undefined)
          ?? (result as Record<string, unknown>)
      : {};
  return {
    displayName:
      readStringField(fromArgs, 'display_name', 'displayName', 'team_name', 'teamName')
      ?? readStringField(fromResult, 'display_name', 'displayName', 'team_name', 'teamName'),
    teamDesc: readStringField(fromArgs, 'team_desc', 'teamDesc', 'description'),
  };
}

/** openjiuwen build_team — team creation block card. */
export function TeamBuildToolCard({ status, args, result }: TeamBuildToolCardProps) {
  const { displayName, teamDesc } = teamBuildPreview(args, result);
  const inProgress = isToolInProgress(status);
  const failed = isToolFailed(status);
  const teamName = displayName ?? '协作团队';
  const title = inProgress ? '创建协作团队' : failed ? '创建协作团队失败' : '创建协作团队';
  const badge = inProgress ? '创建中' : failed ? '失败' : '已创建';

  return (
    <article className={`tool-card tool-card-delegation tool-card-team-build status-${status}`}>
      <header className="tool-card-header">
        <span className="tool-icon" aria-hidden>👥</span>
        <span className="tool-name">{title}</span>
        <span className="tool-meta">{teamName}</span>
        <span className={`tool-status status-${status}${!inProgress && !failed ? ' status-success' : ''}`}>
          {!inProgress && !failed ? `✓ ${badge}` : badge}
        </span>
      </header>
      {teamDesc && (
        <p className="tool-card-delegation-title">{teamDesc}</p>
      )}
    </article>
  );
}

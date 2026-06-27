import type { ToolStatus } from '../../types/events';
import { isToolFailed, isToolInProgress, statusLabel } from './shared';

interface TeamReceiveMessageToolCardProps {
  status: ToolStatus;
  args?: unknown;
  result?: unknown;
  senderLabel?: string;
  memberLabel?: (memberId: string) => string | undefined;
}

function readRedactable(value: unknown): string | undefined {
  if (typeof value === 'string' && value.trim()) {
    return value.trim();
  }
  if (value && typeof value === 'object') {
    const preview = (value as Record<string, unknown>).preview;
    if (typeof preview === 'string' && preview.trim()) {
      return preview.trim();
    }
  }
  return undefined;
}

function readField(source: Record<string, unknown>, ...keys: string[]): string | undefined {
  for (const key of keys) {
    const value = readRedactable(source[key]);
    if (value) {
      return value;
    }
  }
  return undefined;
}

function isTeamLeadRecipient(to?: string): boolean {
  if (!to) {
    return false;
  }
  const normalized = to.trim().toLowerCase().replace(/^@/, '');
  return normalized === '__lead__'
    || normalized === 'main'
    || normalized === 'lead'
    || normalized === 'leader'
    || normalized === 'team-lead'
    || normalized === 'team_lead'
    || normalized === 'team_leader';
}

function receiveMessageParts(
  args?: unknown,
  result?: unknown,
): {
  from?: string;
  fromName?: string;
  to?: string;
  message?: string;
  summary?: string;
} {
  const argRecord = args && typeof args === 'object' ? (args as Record<string, unknown>) : {};
  const resultRecord = result && typeof result === 'object' ? (result as Record<string, unknown>) : {};
  const data = resultRecord.data && typeof resultRecord.data === 'object'
    ? (resultRecord.data as Record<string, unknown>)
    : {};
  const message = readField(argRecord, 'message', 'content', 'body')
    ?? readField(data, 'message', 'content', 'body', 'summary', 'summaryPreview');
  const summary = readField(argRecord, 'summary', 'description')
    ?? readField(data, 'summary', 'summaryPreview');
  return {
    from: readField(argRecord, 'from', 'from_member', 'fromMember', 'sender', 'memberId'),
    fromName: readField(argRecord, 'fromName', 'from_name', 'memberName', 'member_name', 'senderName'),
    to: readField(argRecord, 'to', 'recipient', 'target'),
    message,
    summary: summary && summary !== message ? summary : undefined,
  };
}

/** Member {@code send_message} back to the team lead (the reference workbench send-message renderer). */
export function TeamReceiveMessageToolCard({
  status,
  args,
  result,
  senderLabel,
  memberLabel,
}: TeamReceiveMessageToolCardProps) {
  const { from, fromName, to, message, summary } = receiveMessageParts(args, result);
  const sender = senderLabel
    ?? fromName
    ?? (from ? memberLabel?.(from) ?? from : undefined)
    ?? '成员';
  const recipient = to && !isTeamLeadRecipient(to) ? (memberLabel?.(to) ?? to) : '主理人';
  const inProgress = isToolInProgress(status);
  const failed = isToolFailed(status);
  const title = failed ? `${sender} 回传失败` : `${sender} 回传给 ${recipient}`;
  const body = message ?? summary;

  return (
    <article className={`tool-card tool-card-delegation tool-card-team-receive status-${status}`}>
      <header className="tool-card-header">
        <span className="tool-icon" aria-hidden>📨</span>
        <span className="tool-name">{title}</span>
        <span className={`tool-status status-${status}`}>{statusLabel(status)}</span>
      </header>
      {summary && message && summary !== message && (
        <p className="tool-card-delegation-title">{summary}</p>
      )}
      {body && (
        <pre className="tool-card-preview tool-card-delegation-body">{body}</pre>
      )}
      {inProgress && !body && (
        <p className="muted tool-card-delegation-empty">正在整理回传内容…</p>
      )}
    </article>
  );
}

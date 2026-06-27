import type { ToolStatus } from '../../types/events';
import { isToolFailed, isToolInProgress, statusLabel } from './shared';

interface TeamSendMessageToolCardProps {
  status: ToolStatus;
  args?: unknown;
  result?: unknown;
  memberLabel?: (memberId: string) => string | undefined;
}

/** Persisted run-event args are redacted to `{ preview, bytes }`; live SSE / session_messages use plain strings. */
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

function sendMessageParts(args?: unknown): {
  to?: string;
  memberName?: string;
  message?: string;
  description?: string;
} {
  if (!args || typeof args !== 'object') {
    return {};
  }
  const record = args as Record<string, unknown>;
  const message = readField(record, 'message', 'content', 'body')
    ?? readField(record, 'messagePreview');
  const description = readField(record, 'description', 'summary');
  return {
    to: readField(record, 'to', 'member_id', 'memberId', 'target'),
    memberName: readField(record, 'memberName', 'member_name'),
    message,
    description: description && description !== message ? description : undefined,
  };
}

/** openjiuwen send_message — delegation block card with full task body. */
export function TeamSendMessageToolCard({
  status,
  args,
  memberLabel,
}: TeamSendMessageToolCardProps) {
  const { to, memberName, message, description } = sendMessageParts(args);
  const recipient = memberName ?? (to ? memberLabel?.(to) ?? to : undefined) ?? '成员';
  const inProgress = isToolInProgress(status);
  const failed = isToolFailed(status);
  const title = failed ? `派活给 ${recipient}（失败）` : `派活给 ${recipient}`;
  const body = message ?? description;

  return (
    <article className={`tool-card tool-card-delegation tool-card-team-send status-${status}`}>
      <header className="tool-card-header">
        <span className="tool-icon" aria-hidden>🎯</span>
        <span className="tool-name">{title}</span>
        <span className={`tool-status status-${status}`}>{statusLabel(status)}</span>
      </header>
      {description && message && description !== message && (
        <p className="tool-card-delegation-title">{description}</p>
      )}
      {body && (
        <pre className="tool-card-preview tool-card-delegation-body">{body}</pre>
      )}
      {inProgress && !body && (
        <p className="muted tool-card-delegation-empty">正在派发任务…</p>
      )}
    </article>
  );
}

import type { ToolStatus } from '../../types/events';
import type { TeamUiLabels } from '../../lib/teamUiLabels';
import { formatRelativeTime } from '../../lib/formatRelativeTime';
import {
  busPublishPreviewFromArgs,
  busPublishTopicFromResult,
} from '../../lib/toolKind';
import { isToolFailed, isToolInProgress, statusLabel, formatJson } from './shared';

interface TeamBusPublishToolCardProps {
  status: ToolStatus;
  args?: unknown;
  result?: unknown;
  labels: TeamUiLabels;
  authorLabel?: string;
  occurredAt?: number;
  laneLinked?: boolean;
  onHighlightLane?: () => void;
}

/** message-bus 成员 mid-run 发布 — 主时间线业务通知气泡 */
export function TeamBusPublishToolCard({
  status,
  args,
  result,
  labels,
  authorLabel,
  occurredAt,
  laneLinked = false,
  onHighlightLane,
}: TeamBusPublishToolCardProps) {
  const { topic: argTopic, preview } = busPublishPreviewFromArgs(args);
  const resultTopic = busPublishTopicFromResult(result);
  const topic = argTopic ?? resultTopic;
  const interactive = Boolean(onHighlightLane);
  const displayAuthor = authorLabel ?? '协作成员';

  const handleActivate = () => {
    onHighlightLane?.();
  };

  return (
    <article
      className={`tool-card tool-card-bus-publish bus-notice-bubble status-${status}${laneLinked ? ' lane-linked' : ''}${interactive ? ' lane-clickable' : ''}`}
      onClick={interactive ? handleActivate : undefined}
      onKeyDown={
        interactive
          ? (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                handleActivate();
              }
            }
          : undefined
      }
      role={interactive ? 'button' : undefined}
      tabIndex={interactive ? 0 : undefined}
      aria-pressed={laneLinked ? true : undefined}
    >
      <header className="bus-notice-head">
        <span className="bus-notice-icon" aria-hidden>🔔</span>
        <p className="bus-notice-title">
          <strong>{displayAuthor}</strong> {labels.busPublishAction}
          {occurredAt && (
            <span className="bus-notice-time muted">
              （{formatRelativeTime(occurredAt)}）
            </span>
          )}
        </p>
        <span className={`tool-status status-${status}`}>{statusLabel(status)}</span>
      </header>
      {preview && <p className="bus-notice-preview">{preview}</p>}
      {interactive && (
        <p className="tool-card-bus-hint muted">
          {laneLinked ? labels.busLaneLinked : labels.busLaneHint}
        </p>
      )}
      {topic && <span className="bus-notice-topic muted" hidden>{topic}</span>}
      {result !== undefined && !isToolInProgress(status) && isToolFailed(status) && (
        <details open>
          <summary>错误</summary>
          <pre>{formatJson(result)}</pre>
        </details>
      )}
    </article>
  );
}

import { useMemo } from 'react';
import type { Expert, Session } from '../../types/api';
import type { ApprovalRequiredPayload } from '../../types/events';
import type { WorkspacePreset } from '../../types/workspace';
import { formatTokenCount } from '../../lib/formatTokenCount';
import { formatRelativeTime } from '../../lib/formatRelativeTime';
import {
  SESSION_TIME_GROUP_LABELS,
  isArchivedSession,
  listArchivedSessions,
  organizeActiveSessions,
} from '../../lib/sessionGroups';
import { workspaceLabelForSession } from '../../lib/sessionWorkspace';
import { permissionModeLabel } from '../../components/ModeMenu';
import { SessionItemMenu } from './SessionItemMenu';

interface SessionHistoryListProps {
  sessions: Session[];
  experts: Expert[];
  workspacePresets: WorkspacePreset[];
  activeId: string | null;
  streamingBySession: Record<string, boolean>;
  pendingBySession: Record<string, ApprovalRequiredPayload>;
  sessionsWithPendingApproval?: Set<string>;
  emptyQuery?: boolean;
  loading?: boolean;
  onSelect: (id: string) => void;
  onMetadataChange?: (sessionId: string, patch: { pinned?: boolean; archived?: boolean }) => void;
  showArchived?: boolean;
}

function expertLabel(experts: Expert[], expertId: string | null | undefined): string | null {
  if (!expertId) {
    return null;
  }
  return experts.find((expert) => expert.id === expertId)?.name ?? expertId;
}

function SessionRow({
  session,
  experts,
  workspacePresets,
  activeId,
  streamingBySession,
  pendingBySession,
  sessionsWithPendingApproval = new Set(),
  onSelect,
  onMetadataChange,
}: {
  session: Session;
  experts: Expert[];
  workspacePresets: WorkspacePreset[];
  activeId: string | null;
  streamingBySession: Record<string, boolean>;
  pendingBySession: Record<string, ApprovalRequiredPayload>;
  sessionsWithPendingApproval?: Set<string>;
  onSelect: (id: string) => void;
  onMetadataChange?: (sessionId: string, patch: { pinned?: boolean; archived?: boolean }) => void;
}) {
  const expertName = expertLabel(experts, session.expertId);
  const spaceLabel = workspaceLabelForSession(session, workspacePresets);
  const tokenTotal = (session.promptTokens ?? 0) + (session.completionTokens ?? 0);
  const archived = isArchivedSession(session);
  const statusDot =
    session.status === 'RUNNING'
      ? 'running'
      : session.status === 'COMPLETED'
        ? 'completed'
        : session.status === 'STOPPED'
          ? 'stopped'
          : 'created';

  return (
    <li>
      <div
        className={`session-item${activeId === session.id ? ' active' : ''}${session.pinned ? ' pinned' : ''}${archived ? ' archived' : ''}`}
      >
        <button
          type="button"
          className="session-item-main"
          onClick={() => onSelect(session.id)}
        >
          <span className="session-item-icon" aria-hidden>
            {session.pinned ? '📌' : '📄'}
          </span>
          <span className="session-item-body">
            <span className="session-row">
              <span className="session-title">{session.title || '未命名任务'}</span>
              <span className="session-badges">
                {(sessionsWithPendingApproval.has(session.id) || pendingBySession[session.id]) && (
                  <span className="session-pending" title="待审批">!</span>
                )}
                {streamingBySession[session.id] && (
                  <span className="session-streaming" title="生成中">●</span>
                )}
                {(session.status === 'RUNNING' ||
                  session.status === 'STOPPED' ||
                  sessionsWithPendingApproval.has(session.id) ||
                  pendingBySession[session.id]) && (
                  <span
                    className={`session-status-dot status-${statusDot}`}
                    title={session.status}
                    aria-label={session.status}
                  />
                )}
              </span>
            </span>
            <span className="session-meta">
              {formatRelativeTime(session.updatedAt)}
              {session.permissionMode && session.permissionMode !== 'CRAFT'
                ? ` · ${permissionModeLabel(session.permissionMode)}`
                : ''}
              {expertName ? ` · ${expertName}` : ''}
              {spaceLabel ? ` · ${spaceLabel}` : ''}
              {tokenTotal > 0 ? ` · ${formatTokenCount(tokenTotal)} tokens` : ''}
            </span>
          </span>
        </button>
        {onMetadataChange && (
          <SessionItemMenu
            pinned={Boolean(session.pinned)}
            archived={archived}
            onPin={(pinned) => onMetadataChange(session.id, { pinned })}
            onArchive={(archivedValue) => onMetadataChange(session.id, { archived: archivedValue })}
          />
        )}
      </div>
    </li>
  );
}

function SessionSection({
  label,
  sessions,
  experts,
  workspacePresets,
  activeId,
  streamingBySession,
  pendingBySession,
  sessionsWithPendingApproval = new Set(),
  onSelect,
  onMetadataChange,
}: {
  label: string;
  sessions: Session[];
  experts: Expert[];
  workspacePresets: WorkspacePreset[];
  activeId: string | null;
  streamingBySession: Record<string, boolean>;
  pendingBySession: Record<string, ApprovalRequiredPayload>;
  sessionsWithPendingApproval?: Set<string>;
  onSelect: (id: string) => void;
  onMetadataChange?: (sessionId: string, patch: { pinned?: boolean; archived?: boolean }) => void;
}) {
  if (sessions.length === 0) {
    return null;
  }

  return (
    <section className="session-time-group">
      <h3 className="session-time-group-label">{label}</h3>
      <ul className="session-list">
        {sessions.map((session) => (
          <SessionRow
            key={session.id}
            session={session}
            experts={experts}
            workspacePresets={workspacePresets}
            activeId={activeId}
            streamingBySession={streamingBySession}
            pendingBySession={pendingBySession}
            sessionsWithPendingApproval={sessionsWithPendingApproval}
            onSelect={onSelect}
            onMetadataChange={onMetadataChange}
          />
        ))}
      </ul>
    </section>
  );
}

export function SessionHistoryList({
  sessions,
  experts,
  workspacePresets,
  activeId,
  streamingBySession,
  pendingBySession,
  sessionsWithPendingApproval = new Set(),
  emptyQuery,
  loading = false,
  onSelect,
  onMetadataChange,
  showArchived = false,
}: SessionHistoryListProps) {
  const organized = useMemo(() => organizeActiveSessions(sessions), [sessions]);
  const archived = useMemo(() => listArchivedSessions(sessions), [sessions]);
  const activeCount = organized.pinned.length + organized.groups.reduce((sum, group) => sum + group.sessions.length, 0);

  if (!showArchived && activeCount === 0) {
    return (
      <ul className="session-list">
        <li className="session-empty">
          {loading
            ? '加载中…'
            : emptyQuery
              ? '无匹配对话'
              : '暂无对话，点击上方新建或在输入框直接开始'}
        </li>
      </ul>
    );
  }

  return (
    <div className="session-grouped-list">
      {!showArchived && (
        <>
          <SessionSection
            label="置顶"
            sessions={organized.pinned}
            experts={experts}
            workspacePresets={workspacePresets}
            activeId={activeId}
            streamingBySession={streamingBySession}
            pendingBySession={pendingBySession}
            sessionsWithPendingApproval={sessionsWithPendingApproval}
            onSelect={onSelect}
            onMetadataChange={onMetadataChange}
          />
          {organized.groups.map((group) => (
            <SessionSection
              key={group.key}
              label={SESSION_TIME_GROUP_LABELS[group.key]}
              sessions={group.sessions}
              experts={experts}
              workspacePresets={workspacePresets}
              activeId={activeId}
              streamingBySession={streamingBySession}
              pendingBySession={pendingBySession}
              sessionsWithPendingApproval={sessionsWithPendingApproval}
              onSelect={onSelect}
              onMetadataChange={onMetadataChange}
            />
          ))}
        </>
      )}
      {showArchived && (
        <SessionSection
          label="已归档"
          sessions={archived}
          experts={experts}
          workspacePresets={workspacePresets}
          activeId={activeId}
          streamingBySession={streamingBySession}
          pendingBySession={pendingBySession}
          sessionsWithPendingApproval={sessionsWithPendingApproval}
          onSelect={onSelect}
          onMetadataChange={onMetadataChange}
        />
      )}
    </div>
  );
}

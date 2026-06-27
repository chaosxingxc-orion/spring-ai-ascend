import { useEffect, useMemo, useState } from 'react';
import type { Expert, Session, SessionLimits } from '../../types/api';
import type { ApprovalRequiredPayload } from '../../types/events';
import type { WorkspacePreset } from '../../types/workspace';
import { filterSessions } from '../../lib/sessionWorkspace';
import { countActiveSessions, listArchivedSessions } from '../../lib/sessionGroups';
import { isAtSessionLimit } from '../../lib/sessionLimits';
import { SessionHistoryList } from './SessionHistoryList';
import { SidebarBrand } from './SidebarBrand';
import { SidebarNav } from './SidebarNav';
import { SidebarUserFooter } from './SidebarUserFooter';
import { WorkspaceGroupsSection } from './WorkspaceGroupsSection';
import { SessionLimitBanner } from '../../components/SessionLimitBanner';
import { t } from '../../lib/i18n';

const SIDEBAR_COLLAPSED_KEY = 'workmate.sidebarCollapsed';

function loadSidebarCollapsed(): boolean {
  try {
    return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1';
  } catch {
    return false;
  }
}

interface AppSidebarProps {
  sessions: Session[];
  experts: Expert[];
  workspacePresets: WorkspacePreset[];
  activeId: string | null;
  loading: boolean;
  pathname: string;
  streamingBySession: Record<string, boolean>;
  pendingBySession: Record<string, ApprovalRequiredPayload>;
  sessionsWithPendingApproval?: Set<string>;
  settingsActive?: boolean;
  auditActive?: boolean;
  devActive?: boolean;
  onSelect: (id: string) => void;
  onCreate: () => void;
  onOpenSettings?: () => void;
  onOpenAudit?: () => void;
  onOpenDev?: () => void;
  sessionLimits?: SessionLimits | null;
  autoArchiveEnabled?: boolean;
  onSessionLimitHelp?: () => void;
  onSessionMetadataChange?: (sessionId: string, patch: { pinned?: boolean; archived?: boolean }) => void;
}

export function AppSidebar({
  sessions,
  experts,
  workspacePresets,
  activeId,
  loading,
  streamingBySession,
  pendingBySession,
  sessionsWithPendingApproval = new Set(),
  pathname,
  settingsActive = false,
  auditActive = false,
  devActive = false,
  onSelect,
  onCreate,
  onOpenSettings,
  onOpenAudit,
  onOpenDev,
  sessionLimits,
  autoArchiveEnabled = false,
  onSessionLimitHelp,
  onSessionMetadataChange,
}: AppSidebarProps) {
  const [query, setQuery] = useState('');
  const [tasksOpen, setTasksOpen] = useState(true);
  const [archivedOpen, setArchivedOpen] = useState(false);
  const [spacesOpen, setSpacesOpen] = useState(true);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(loadSidebarCollapsed);

  useEffect(() => {
    try {
      localStorage.setItem(SIDEBAR_COLLAPSED_KEY, sidebarCollapsed ? '1' : '0');
    } catch {
      // ignore storage errors
    }
  }, [sidebarCollapsed]);

  const filteredSessions = useMemo(
    () => filterSessions(sessions, experts, query),
    [sessions, experts, query],
  );
  const activeSessionCount = useMemo(() => countActiveSessions(filteredSessions), [filteredSessions]);
  const archivedSessionCount = useMemo(
    () => listArchivedSessions(filteredSessions).length,
    [filteredSessions],
  );
  const activeSessions = useMemo(
    () => filteredSessions.filter((session) => !session.archivedAt),
    [filteredSessions],
  );
  const atLimit = sessionLimits
    ? isAtSessionLimit(sessionLimits.activeCount, sessionLimits.maxActive)
    : false;
  const blockNewTask = atLimit && !autoArchiveEnabled;

  return (
    <aside className={`sidebar${sidebarCollapsed ? ' sidebar-collapsed' : ''}`}>
      <header className="sidebar-header">
        <SidebarBrand
          collapsed={sidebarCollapsed}
          onToggleCollapse={() => setSidebarCollapsed((value) => !value)}
        />
        {sessionLimits && onSessionLimitHelp && (
          <SessionLimitBanner
            limits={sessionLimits}
            autoArchiveEnabled={autoArchiveEnabled}
            onShowHelp={onSessionLimitHelp}
          />
        )}
        <button
          type="button"
          className="btn primary sidebar-new-task"
          onClick={onCreate}
          disabled={loading || blockNewTask}
          title={blockNewTask ? t('session.newTaskAtLimit') : undefined}
        >
          + 新建任务
        </button>
        <SidebarNav pathname={pathname} />
      </header>

      <div className="session-list-wrap">
        <button
          type="button"
          className="sidebar-section-toggle"
          onClick={() => setTasksOpen((open) => !open)}
          aria-expanded={tasksOpen}
        >
          <span>任务 ({activeSessionCount})</span>
          <span className="sidebar-section-chevron">{tasksOpen ? '▼' : '▶'}</span>
        </button>
        {tasksOpen && (
          <input
            type="search"
            className="sidebar-search"
            placeholder="搜索任务、专家、空间"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        )}
        {tasksOpen && (
          <SessionHistoryList
            sessions={filteredSessions}
            experts={experts}
            workspacePresets={workspacePresets}
            activeId={activeId}
            streamingBySession={streamingBySession}
            pendingBySession={pendingBySession}
            sessionsWithPendingApproval={sessionsWithPendingApproval}
            emptyQuery={Boolean(query.trim())}
            loading={loading}
            onSelect={onSelect}
            onMetadataChange={onSessionMetadataChange}
          />
        )}
        {archivedSessionCount > 0 && (
          <>
            <button
              type="button"
              className="sidebar-section-toggle sidebar-section-toggle-archived"
              onClick={() => setArchivedOpen((open) => !open)}
              aria-expanded={archivedOpen}
            >
              <span>已归档 ({archivedSessionCount})</span>
              <span className="sidebar-section-chevron">{archivedOpen ? '▼' : '▶'}</span>
            </button>
            {archivedOpen && (
              <SessionHistoryList
                sessions={filteredSessions}
                experts={experts}
                workspacePresets={workspacePresets}
                activeId={activeId}
                streamingBySession={streamingBySession}
                pendingBySession={pendingBySession}
                sessionsWithPendingApproval={sessionsWithPendingApproval}
                emptyQuery={Boolean(query.trim())}
                loading={loading}
                onSelect={onSelect}
                onMetadataChange={onSessionMetadataChange}
                showArchived
              />
            )}
          </>
        )}
        <WorkspaceGroupsSection
          sessions={activeSessions}
          workspacePresets={workspacePresets}
          activeId={activeId}
          open={spacesOpen}
          onToggle={() => setSpacesOpen((open) => !open)}
          onSelect={onSelect}
        />
      </div>

      <SidebarUserFooter
        onOpenSettings={onOpenSettings}
        onOpenAudit={onOpenAudit}
        onOpenDev={onOpenDev}
        settingsActive={settingsActive}
        auditActive={auditActive}
        devActive={devActive}
      />
    </aside>
  );
}

import type { NavigateFunction } from 'react-router-dom';
import { AppSidebar } from '../sidebar';
import { DetailPanel } from '../../components/DetailPanel/DetailPanel';
import type { InputDockConfig } from '../input-dock/types';
import { AuditLogView } from '../../views/admin/AuditLogView';
import { DevStudioView } from '../../views/dev/DevStudioView';
import { ExpertMarketplacePage } from '../../views/market/ExpertMarketplacePage';
import { MyFilesView } from '../../views/myfiles/MyFilesView';
import {
  AssistantHubView,
  AutomationHubView,
  MoreHubView,
  ProjectsHubView,
} from '../../views/nav/SidebarNavViews';
import { NewTaskView } from '../../views/new-task/NewTaskView';
import { SessionChatView } from '../../views/session/SessionChatView';
import { ShareReplayView } from '../../views/share/ShareReplayView';
import { SettingsView } from '../../views/settings/SettingsView';
import type { SettingsSection } from '../settings/settingsTypes';
import { sessionPath, settingsPath, settingsMemoryContentPath, DEV_STUDIO_PATH } from '../../lib/paths';
import type { StreamStage } from '../../lib/streamStage';
import type { SessionRunError } from '../../hooks/useChatStream';
import type {
  ApprovalDecision,
  ApprovalDecisionScope,
  Expert,
  MemoryStatus,
  Session,
  SessionLimits,
} from '../../types/api';
import type { MarketTab } from '../../types/market';
import type { WorkspacePreset } from '../../types/workspace';
import type { WelcomeConfig, PlaybookCard } from '../../types/welcome';
import type {
  ApprovalRequiredPayload,
  ChatItem,
  PlanStep,
} from '../../types/events';
import type { ExpertMarketKind } from '../../lib/expertMarketFilter';
import type { TeamState } from '../../lib/teamStatus';
import type { AppShellAction } from './appShellState';
import type { useAcpSidecarImport } from '../../hooks/useAcpSidecarImport';

export interface AppShellMainContentProps {
  isShareReplay: boolean;
  shareToken: string | null;
  sessions: Session[];
  experts: Expert[];
  expertsLoaded: boolean;
  workspacePresets: WorkspacePreset[];
  activeId: string | null;
  loadingSessions: boolean;
  streamingBySession: Record<string, boolean>;
  pendingBySession: Record<string, ApprovalRequiredPayload>;
  sessionsWithPendingApproval: Set<string>;
  pathname: string;
  isSettings: boolean;
  isAuditLog: boolean;
  isDevStudio: boolean;
  sessionLimits: SessionLimits | null;
  autoArchiveEnabled: boolean;
  onSessionLimitHelp: () => void;
  onSessionMetadataChange: (
    sessionId: string,
    patch: {
      pinned?: boolean;
      archived?: boolean;
      modelId?: string | null;
      effort?: import('../../types/api').ModelEffort | null;
      enabledConnectorIds?: string[];
      enabledSkillIds?: string[];
      permissionMode?: import('../../types/api').PermissionMode;
      expertId?: string | null;
    },
  ) => void;
  navigate: NavigateFunction;
  dispatch: React.Dispatch<AppShellAction>;
  onSelectSession: (id: string) => void;
  onNewTask: () => void;
  isAuditLogView: boolean;
  isDevStudioView: boolean;
  isMyFiles: boolean;
  isAssistantHub: boolean;
  isProjectsHub: boolean;
  isAutomationHub: boolean;
  isMoreHub: boolean;
  settingsSection: SettingsSection;
  isMarket: boolean;
  marketTab: MarketTab;
  welcomeConfig: WelcomeConfig;
  expertMarketQuery: string;
  expertCategory: string;
  expertKind: ExpertMarketKind;
  expertSort: 'popular' | 'newest';
  detailExpert: Expert | null;
  summonBusy: boolean;
  onExpertQueryChange: (query: string) => void;
  onExpertCategoryChange: (category: string) => void;
  onExpertKindChange: (kind: ExpertMarketKind) => void;
  onExpertSortChange: (sort: 'popular' | 'newest') => void;
  onMarketTabChange: (tab: MarketTab) => void;
  onMarketBack: () => void;
  onSelectDetailExpert: (expert: Expert | null) => void;
  onRequestSummon: (expert: Expert) => void;
  onPlaybookSelect: (playbook: PlaybookCard) => void;
  marketSkills: import('../../types/market').SkillInfo[];
  marketConnectors: import('../../types/market').ConnectorInfo[];
  onMarketSkillsChange: (skills: import('../../types/market').SkillInfo[]) => void;
  onMarketConnectorsChange: (connectors: import('../../types/market').ConnectorInfo[]) => void;
  onExpertsRefresh: () => void;
  isNewTask: boolean;
  welcomeHydrated: boolean;
  inputDockConfig: InputDockConfig;
  newTaskDraft: string;
  onInspirationLaunch: (card: PlaybookCard, autoSend?: boolean) => void;
  onDiscoverLaunch: (payload: { initPrompt: string; expertId?: string; title: string }) => void;
  activeSession: Session | null;
  activeTeamHistoryLoading: boolean;
  linkedCloudSessionStatus: string | null;
  onCloudBadgeClick?: () => void;
  chatItems: ChatItem[];
  activeStreaming: boolean;
  activeStreamStage: StreamStage | null;
  assistantBrand: string;
  onConfirmPlan: (planId: string) => void;
  confirmingPlan: boolean;
  onUpdatePlanSteps: (planId: string, steps: PlanStep[], title?: string) => void;
  savingPlan: boolean;
  activeTeam: TeamState | null;
  activeExpert: Expert | null;
  approvalBusy: boolean;
  questionBusy: boolean;
  onQuestionAnswer: (questionId: string, selections: string[], text?: string) => void;
  onQuestionSkip: (questionId: string) => void;
  onApprovalDecide: (
    approvalId: string,
    decision: ApprovalDecision,
    scope?: ApprovalDecisionScope,
  ) => void;
  onEditMessage: (seq: number, text: string) => void;
  onRetry: (fromSeq: number) => void;
  activeRunError: SessionRunError | null;
  onDismissRunError: () => void;
  onRetryRunError?: () => void;
  memoryStatus: MemoryStatus | null;
  onRememberSession: () => void;
  rememberSessionBusy: boolean;
  canRememberSession: boolean;
  activeQueueDepth: number;
  onClearQueue?: () => void;
  clearQueueBusy: boolean;
  onShare: () => void;
  shareDialogOpen: boolean;
  shareToast: string | null;
  onOpenArtifact: (path: string, tab?: 'browser' | 'changes') => void;
  onOpenChanges: (path: string) => void;
  onOpenDetailTab: (tab: 'artifacts' | 'changes') => void;
  onArchive?: (archived: boolean) => void;
  sidecarImport: ReturnType<typeof useAcpSidecarImport>;
  detailPanelVisible: boolean;
  detailPanelAvailable: boolean;
  onToggleDetailPanel: () => void;
  onChangeExpert?: () => void;
  artifactRefreshKey: number;
  onOpenTeamMember: (memberId: string, memberName: string) => void;
  onMemberFocusChange: (focus: { id: string; name: string } | null) => void;
  detailPanelEnabled: boolean;
  detailPanelWidth: number;
  onDetailPanelWidthChange: (width: number) => void;
  artifactAutoOpenPath: string | null;
  artifactAutoOpenMode: 'preview' | 'changes' | null;
  onArtifactAutoOpenHandled: () => void;
  detailFocusTab: 'artifacts' | 'files' | 'changes' | null;
  onFocusTabHandled: () => void;
  detailMemberFocus: { id: string; name: string } | null;
  onFocusMemberHandled: () => void;
  onOpenMyFiles: () => void;
  onOpenMarketExperts: () => void;
  onOpenMarketConnectors: () => void;
  onMoreDiscoverLaunch: (payload: { initPrompt: string; expertId?: string; title: string }) => void;
  onOpenAudit: () => void;
}

export function AppShellMainContent(props: AppShellMainContentProps) {
  const {
    isShareReplay,
    shareToken,
    sessions,
    experts,
    workspacePresets,
    activeId,
    loadingSessions,
    streamingBySession,
    pendingBySession,
    sessionsWithPendingApproval,
    pathname,
    isSettings,
    isAuditLog,
    isDevStudio,
    sessionLimits,
    autoArchiveEnabled,
    onSessionLimitHelp,
    onSessionMetadataChange,
    navigate,
    dispatch,
    onSelectSession,
    onNewTask,
    isAuditLogView,
    isDevStudioView,
    isMyFiles,
    isAssistantHub,
    isProjectsHub,
    isAutomationHub,
    isMoreHub,
    settingsSection,
    isMarket,
    marketTab,
    welcomeConfig,
    expertsLoaded,
    expertMarketQuery,
    expertCategory,
    expertKind,
    expertSort,
    detailExpert,
    summonBusy,
    onExpertQueryChange,
    onExpertCategoryChange,
    onExpertKindChange,
    onExpertSortChange,
    onMarketTabChange,
    onMarketBack,
    onSelectDetailExpert,
    onRequestSummon,
    onPlaybookSelect,
    marketSkills,
    marketConnectors,
    onMarketSkillsChange,
    onMarketConnectorsChange,
    onExpertsRefresh,
    isNewTask,
    welcomeHydrated,
    inputDockConfig,
    newTaskDraft,
    onInspirationLaunch,
    onDiscoverLaunch,
    activeSession,
    activeTeamHistoryLoading,
    linkedCloudSessionStatus,
    onCloudBadgeClick,
    chatItems,
    activeStreaming,
    activeStreamStage,
    assistantBrand,
    onConfirmPlan,
    confirmingPlan,
    onUpdatePlanSteps,
    savingPlan,
    activeTeam,
    activeExpert,
    approvalBusy,
    questionBusy,
    onQuestionAnswer,
    onQuestionSkip,
    onApprovalDecide,
    onEditMessage,
    onRetry,
    activeRunError,
    onDismissRunError,
    onRetryRunError,
    memoryStatus,
    onRememberSession,
    rememberSessionBusy,
    canRememberSession,
    activeQueueDepth,
    onClearQueue,
    clearQueueBusy,
    onShare,
    shareDialogOpen,
    shareToast,
    onOpenArtifact,
    onOpenChanges,
    onOpenDetailTab,
    onArchive,
    sidecarImport,
    detailPanelVisible,
    detailPanelAvailable,
    onToggleDetailPanel,
    onChangeExpert,
    artifactRefreshKey,
    onOpenTeamMember,
    onMemberFocusChange,
    detailPanelEnabled,
    detailPanelWidth,
    onDetailPanelWidthChange,
    artifactAutoOpenPath,
    artifactAutoOpenMode,
    onArtifactAutoOpenHandled,
    detailFocusTab,
    onFocusTabHandled,
    detailMemberFocus,
    onFocusMemberHandled,
    onOpenMyFiles,
    onOpenMarketExperts,
    onOpenMarketConnectors,
    onMoreDiscoverLaunch,
    onOpenAudit,
  } = props;

  if (isShareReplay && shareToken) {
    return <ShareReplayView token={shareToken} />;
  }

  return (
    <>
      {!isShareReplay && (
        <AppSidebar
          sessions={sessions}
          experts={experts}
          workspacePresets={workspacePresets}
          activeId={activeId}
          loading={loadingSessions}
          streamingBySession={streamingBySession}
          pendingBySession={pendingBySession}
          sessionsWithPendingApproval={sessionsWithPendingApproval}
          pathname={pathname}
          settingsActive={isSettings}
          auditActive={isAuditLog}
          devActive={isDevStudio}
          onSelect={onSelectSession}
          onCreate={onNewTask}
          onOpenSettings={() => navigate(settingsPath('general'))}
          onOpenAudit={onOpenAudit}
          onOpenDev={() => navigate(DEV_STUDIO_PATH)}
          sessionLimits={sessionLimits}
          autoArchiveEnabled={autoArchiveEnabled}
          onSessionLimitHelp={onSessionLimitHelp}
          onSessionMetadataChange={(sessionId, patch) =>
            void onSessionMetadataChange(sessionId, patch)
          }
        />
      )}
      {isAuditLogView ? (
        <AuditLogView />
      ) : isDevStudioView ? (
        <DevStudioView
          pathname={pathname}
          onNavigate={(path, options) => navigate(path, options)}
        />
      ) : isMyFiles ? (
        <MyFilesView onOpenTask={(sessionId, filePath) => {
          onOpenArtifact(filePath);
          navigate(sessionPath(sessionId));
        }} />
      ) : isAssistantHub ? (
        <AssistantHubView
          sessions={sessions}
          memoryEnabled={memoryStatus?.enabled ?? false}
          onNewTask={onNewTask}
          onOpenMarket={onOpenMarketExperts}
          onOpenMemorySettings={() => navigate(settingsPath('memory'))}
          onSelectSession={(id) => {
            dispatch({ type: 'select', id });
            navigate(sessionPath(id));
          }}
        />
      ) : isProjectsHub ? (
        <ProjectsHubView
          sessions={sessions}
          workspacePresets={workspacePresets}
          experts={experts}
          onNewTask={onNewTask}
          onOpenFiles={onOpenMyFiles}
          onSelectSession={(id) => {
            dispatch({ type: 'select', id });
            navigate(sessionPath(id));
          }}
        />
      ) : isAutomationHub ? (
        <AutomationHubView
          experts={experts}
          onOpenSession={(id) => {
            dispatch({ type: 'select', id });
            navigate(sessionPath(id));
          }}
          onOpenMarketConnectors={onOpenMarketConnectors}
        />
      ) : isMoreHub ? (
        <MoreHubView
          onDiscoverLaunch={(payload) => void onMoreDiscoverLaunch(payload)}
          onPlaybookSelect={onPlaybookSelect}
        />
      ) : isSettings ? (
        <SettingsView section={settingsSection} onOpenAudit={onOpenAudit} />
      ) : isMarket ? (
        <ExpertMarketplacePage
          tab={marketTab}
          experts={experts}
          expertsLoaded={expertsLoaded}
          marketFeatured={
            welcomeConfig?.marketFeatured ?? {
              placement: 'market-featured',
              enabled: false,
              playbooks: [],
            }
          }
          expertQuery={expertMarketQuery}
          expertCategory={expertCategory}
          expertKind={expertKind}
          expertSort={expertSort}
          detailExpert={detailExpert}
          summonBusy={summonBusy}
          onExpertQueryChange={onExpertQueryChange}
          onExpertCategoryChange={onExpertCategoryChange}
          onExpertKindChange={onExpertKindChange}
          onExpertSortChange={onExpertSortChange}
          onTabChange={onMarketTabChange}
          onBack={onMarketBack}
          onSelectExpert={onSelectDetailExpert}
          onCloseDetail={() => onSelectDetailExpert(null)}
          onRequestSummon={onRequestSummon}
          onPlaybookSelect={onPlaybookSelect}
          marketSearchPlaceholder={welcomeConfig?.marketSearchPlaceholder}
          onExpertsRefresh={onExpertsRefresh}
          marketSkills={marketSkills}
          marketConnectors={marketConnectors}
          onMarketSkillsChange={onMarketSkillsChange}
          onMarketConnectorsChange={onMarketConnectorsChange}
        />
      ) : isNewTask && welcomeHydrated ? (
        <NewTaskView
          dock={inputDockConfig}
          welcome={welcomeConfig}
          draftSeed={newTaskDraft}
          onInspirationSelect={(card, autoSend) => void onInspirationLaunch(card, autoSend)}
          onDiscoverLaunch={(payload) => void onDiscoverLaunch(payload)}
          onPlaybookSelect={onPlaybookSelect}
        />
      ) : isNewTask ? (
        <main className="chat-panel chat-panel-new-task">
          <div className="new-task-stage">
            <p className="market-empty">正在加载…</p>
          </div>
        </main>
      ) : (
        <>
          <SessionChatView
            sessionId={activeId ?? undefined}
            sessionTitle={activeSession?.title ?? '加载中…'}
            sessionLoading={Boolean(activeId && !activeSession?.title)}
            teamHistoryLoading={activeTeamHistoryLoading}
            cloudSessionStatus={linkedCloudSessionStatus}
            onCloudBadgeClick={onCloudBadgeClick}
            items={chatItems}
            streaming={activeStreaming}
            streamStage={activeStreamStage}
            dock={inputDockConfig}
            assistantBrand={assistantBrand}
            promptTokens={activeSession?.promptTokens ?? 0}
            completionTokens={activeSession?.completionTokens ?? 0}
            onConfirmPlan={(planId) => void onConfirmPlan(planId)}
            confirmingPlan={confirmingPlan}
            onUpdatePlanSteps={(planId, steps, title) => void onUpdatePlanSteps(planId, steps, title)}
            savingPlan={savingPlan}
            team={activeTeam}
            expert={activeExpert}
            approvalBusy={approvalBusy}
            questionBusy={questionBusy}
            onQuestionAnswer={(questionId, selections, text) =>
              void onQuestionAnswer(questionId, selections, text)
            }
            onQuestionSkip={(questionId) => void onQuestionSkip(questionId)}
            onApprovalDecide={(approvalId, decision, scope) =>
              void onApprovalDecide(approvalId, decision, scope)
            }
            onEditMessage={onEditMessage}
            onRetry={onRetry}
            runError={activeRunError}
            onDismissRunError={onDismissRunError}
            onRetryRunError={onRetryRunError}
            memoryEnabled={memoryStatus?.enabled ?? false}
            memoryInjectPreview={memoryStatus?.injectPreview}
            onOpenMemorySettings={() => navigate(settingsPath('memory'))}
            onViewMemory={() => navigate(settingsMemoryContentPath())}
            onRememberSession={() => void onRememberSession()}
            rememberSessionBusy={rememberSessionBusy}
            canRememberSession={canRememberSession}
            queueDepth={activeQueueDepth}
            onClearQueue={onClearQueue}
            clearQueueBusy={clearQueueBusy}
            onShare={() => void onShare()}
            shareBusy={shareDialogOpen}
            shareToast={shareToast}
            onOpenArtifact={onOpenArtifact}
            onOpenChanges={onOpenChanges}
            onOpenDetailTab={onOpenDetailTab}
            archived={Boolean(activeSession?.archivedAt)}
            onArchive={onArchive}
            onImportSidecarNdjson={activeId ? sidecarImport.pickNdjsonFile : undefined}
            onRelaySidecarStream={
              activeId && sidecarImport.canRelayStream
                ? () => void sidecarImport.relayStream()
                : undefined
            }
            sidecarImportBusy={sidecarImport.busy}
            sidecarFileInputRef={sidecarImport.fileInputRef}
            onSidecarFileChange={sidecarImport.handleFileChange}
            detailPanelVisible={detailPanelVisible}
            detailPanelAvailable={detailPanelAvailable}
            onToggleDetailPanel={onToggleDetailPanel}
            onChangeExpert={onChangeExpert}
            artifactRefreshKey={artifactRefreshKey}
            onOpenTeamMember={onOpenTeamMember}
            onMemberFocusChange={onMemberFocusChange}
          />
          {detailPanelEnabled && activeId && (
            <DetailPanel
              sessionId={activeId}
              detailWidth={detailPanelWidth}
              onDetailWidthChange={onDetailPanelWidthChange}
              refreshKey={artifactRefreshKey}
              chatItems={chatItems}
              expert={
                activeSession?.expertId
                  ? experts.find((e) => e.id === activeSession.expertId) ?? null
                  : null
              }
              streaming={activeStreaming}
              permissionMode={activeSession?.permissionMode}
              workspaceKey={activeSession?.workspaceKey}
              autoOpenPath={artifactAutoOpenPath}
              autoOpenMode={artifactAutoOpenMode}
              onAutoOpenHandled={onArtifactAutoOpenHandled}
              focusTab={detailFocusTab}
              onFocusTabHandled={onFocusTabHandled}
              focusMember={detailMemberFocus}
              onFocusMemberHandled={onFocusMemberHandled}
              team={activeTeam}
              teamHistoryLoading={activeTeamHistoryLoading}
            />
          )}
        </>
      )}
    </>
  );
}

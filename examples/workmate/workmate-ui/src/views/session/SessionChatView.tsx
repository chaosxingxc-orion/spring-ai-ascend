import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent, type RefObject } from 'react';
import type { ApprovalDecision, ApprovalDecisionScope, Expert } from '../../types/api';
import type { ChatItem } from '../../types/events';
import { MemoryBanner } from '../../components/MemoryBanner';
import { InputDock } from '../../components/InputDock';
import { TeamVisualization } from '../../components/team/TeamVisualization';
import { TeamDelegationBar } from '../../components/team/TeamDelegationBar';
import { TeamMemberBypassComposer } from '../../components/team/TeamMemberBypassComposer';
import { TeamMemberFollowUpHint } from '../../components/team/TeamMemberFollowUpHint';
import { MemberFocusMetadataBar } from '../../components/team/MemberFocusMetadataBar';
import { MemberHistoryLoadingBanner } from '../../components/team/MemberHistoryLoadingBanner';
import { filterMemberChatItems, itemMemberId } from '../../lib/memberChatProjection';
import { shouldShowMemberHistoryLoading } from '../../lib/memberHistoryLoading';
import { shouldShowDelegationDock, isTeamMemberFollowUpAvailable } from '../../lib/teamStatus';
import {
  loadDelegationCardsForMember,
  mergeMemberTimeline,
  sameDelegationCards,
  type MemberDelegationChatItem,
} from '../../lib/delegationInput';
import { resolveTeamVisualizationLayout } from '../../lib/teamVisualizationRoute';
import type { TeamState } from '../../lib/teamStatus';
import type { BusLaneHighlight } from '../../lib/busLaneHighlight';
import { findBusLaneHighlight } from '../../lib/busLaneHighlight';
import { resolveTeamUiLabels } from '../../lib/teamUiLabels';
import { TaskProgressCard } from '../../features/session/TaskProgressCard';
import type { InputDockConfig } from '../../features/input-dock/types';
import { ChatMessageList } from './ChatMessageList';
import { SessionChatHeader } from './SessionChatHeader';
import { RunErrorBanner } from '../../components/RunErrorBanner';
import { ScrollToBottomButton } from '../../components/ScrollToBottomButton';
import { LoadingTips } from '../../components/LoadingTips';
import { SessionChatSearch } from '../../components/SessionChatSearch';
import { useChatScroll } from '../../hooks/useChatScroll';
import { useSessionArtifacts } from '../../hooks/useSessionArtifactPaths';
import type { SessionRunError } from '../../hooks/useChatStream';
import type { StreamStage } from '../../lib/streamStage';
import type { MentionRef } from '../../types/mention';

interface SessionChatViewProps {
  sessionId?: string;
  sessionTitle: string;
  items: ChatItem[];
  streaming: boolean;
  streamStage?: StreamStage | null;
  dock: InputDockConfig;
  assistantBrand?: string;
  promptTokens?: number;
  completionTokens?: number;
  onConfirmPlan?: (planId: string) => void;
  confirmingPlan?: boolean;
  team?: TeamState | null;
  expert?: Expert | null;
  approvalBusy?: boolean;
  questionBusy?: boolean;
  onApprovalDecide?: (approvalId: string, decision: ApprovalDecision, scope?: ApprovalDecisionScope) => void;
  onQuestionAnswer?: (questionId: string, selections: string[], text?: string) => void;
  onQuestionSkip?: (questionId: string) => void;
  onEditMessage?: (seq: number, text: string) => void;
  onRetry?: (fromSeq: number) => void;
  runError?: SessionRunError | null;
  onDismissRunError?: () => void;
  onRetryRunError?: () => void;
  memoryEnabled?: boolean;
  memoryInjectPreview?: string;
  onViewMemory?: () => void;
  onOpenMemorySettings?: () => void;
  onRememberSession?: () => void;
  rememberSessionBusy?: boolean;
  canRememberSession?: boolean;
  queueDepth?: number;
  onClearQueue?: () => void;
  clearQueueBusy?: boolean;
  onShare?: () => void;
  shareBusy?: boolean;
  shareToast?: string | null;
  onOpenArtifact?: (path: string, tab?: 'browser' | 'changes') => void;
  onOpenChanges?: (path: string) => void;
  detailPanelVisible?: boolean;
  detailPanelAvailable?: boolean;
  onToggleDetailPanel?: () => void;
  onOpenDetailTab?: (tab: 'artifacts' | 'changes') => void;
  onUpdatePlanSteps?: (planId: string, steps: import('../../types/events').PlanStep[], title?: string) => void | Promise<void>;
  savingPlan?: boolean;
  archived?: boolean;
  onArchive?: (archived: boolean) => void;
  onImportSidecarNdjson?: () => void;
  onRelaySidecarStream?: () => void;
  sidecarImportBusy?: boolean;
  sidecarFileInputRef?: RefObject<HTMLInputElement | null>;
  onSidecarFileChange?: (event: ChangeEvent<HTMLInputElement>) => void;
  cloudSessionStatus?: string | null;
  onCloudBadgeClick?: () => void;
  onChangeExpert?: () => void;
  artifactRefreshKey?: number;
  onOpenTeamMember?: (memberId: string, memberName: string) => void;
  onMemberFocusChange?: (member: { id: string; name: string } | null) => void;
  sessionLoading?: boolean;
  teamHistoryLoading?: boolean;
}

/** S10+ 活跃对话主区 — workmate-three-column-hifi.png */
export function SessionChatView({
  sessionId,
  sessionTitle,
  items,
  streaming,
  streamStage = null,
  dock,
  assistantBrand,
  promptTokens = 0,
  completionTokens = 0,
  onConfirmPlan,
  confirmingPlan = false,
  team,
  expert,
  approvalBusy = false,
  questionBusy = false,
  onApprovalDecide,
  onQuestionAnswer,
  onQuestionSkip,
  onEditMessage,
  onRetry,
  runError,
  onDismissRunError,
  onRetryRunError,
  memoryEnabled = false,
  memoryInjectPreview,
  onOpenMemorySettings,
  onViewMemory,
  onRememberSession,
  rememberSessionBusy = false,
  canRememberSession = false,
  queueDepth = 0,
  onClearQueue,
  clearQueueBusy = false,
  onShare,
  shareBusy = false,
  shareToast,
  onOpenArtifact,
  onOpenChanges,
  detailPanelVisible = true,
  detailPanelAvailable = false,
  onToggleDetailPanel,
  onOpenDetailTab,
  onUpdatePlanSteps,
  savingPlan = false,
  archived = false,
  onArchive,
  onImportSidecarNdjson,
  onRelaySidecarStream,
  sidecarImportBusy = false,
  sidecarFileInputRef,
  onSidecarFileChange,
  cloudSessionStatus = null,
  onCloudBadgeClick,
  onChangeExpert,
  artifactRefreshKey = 0,
  onOpenTeamMember,
  onMemberFocusChange,
  sessionLoading = false,
  teamHistoryLoading = false,
}: SessionChatViewProps) {
  const teamVizRef = useRef<HTMLDivElement>(null);
  const blackboardAutoOpenedRef = useRef<string | null>(null);
  const {
    knownWorkspacePaths,
    artifactCount: sessionArtifactCount,
    blackboardPath: teamBlackboardPath,
  } = useSessionArtifacts(sessionId, artifactRefreshKey);
  const [busLaneHighlight, setBusLaneHighlight] = useState<BusLaneHighlight | null>(null);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchHighlightId, setSearchHighlightId] = useState<string | null>(null);
  const [focusedMember, setFocusedMember] = useState<{ id: string; name: string } | null>(null);
  const [followUpMentionSeed, setFollowUpMentionSeed] = useState<MentionRef | null>(null);
  const [memberTraceCollapsed, setMemberTraceCollapsed] = useState(true);
  const teamLabels = resolveTeamUiLabels(expert);
  const teamLayout = team ? resolveTeamVisualizationLayout(team) : null;
  const showDelegationDock = shouldShowDelegationDock(team, expert?.teamRuntime);

  // Reset the focused member whenever the session changes.
  useEffect(() => {
    setFocusedMember(null);
  }, [sessionId]);

  useEffect(() => {
    setMemberTraceCollapsed(true);
  }, [sessionId, focusedMember?.id]);

  useEffect(() => {
    onMemberFocusChange?.(showDelegationDock ? focusedMember : null);
  }, [focusedMember, onMemberFocusChange, showDelegationDock]);

  // Default view shows the leader (main-agent) conversation only. The bottom delegation
  // bar filters the same streaming timeline — it does not use a separate data source.
  const memberMode = showDelegationDock && focusedMember != null;
  const memberItems = useMemo(() => {
    if (!showDelegationDock) {
      return items;
    }
    if (focusedMember) {
      return filterMemberChatItems(items, focusedMember);
    }
    return items.filter((item) => itemMemberId(item) == null);
  }, [items, showDelegationDock, focusedMember]);
  const [memberDelegationCards, setMemberDelegationCards] = useState<MemberDelegationChatItem[]>([]);
  useEffect(() => {
    if (!memberMode || !focusedMember || !sessionId) {
      setMemberDelegationCards([]);
      return undefined;
    }
    let cancelled = false;
    void loadDelegationCardsForMember(sessionId, focusedMember.id, items, focusedMember.name).then((next) => {
      if (!cancelled) {
        setMemberDelegationCards((prev) => (sameDelegationCards(prev, next) ? prev : next));
      }
    });
    return () => {
      cancelled = true;
    };
  }, [memberMode, focusedMember, sessionId, items]);
  const visibleItems = useMemo(() => {
    let base = memberItems;
    if (memberMode && memberTraceCollapsed && memberItems.length > 14) {
      const recentTools = new Set(memberItems.filter((item) => item.kind === 'tool').slice(-10));
      base = memberItems.filter((item) => item.kind !== 'tool' || recentTools.has(item));
    }
    if (memberMode && memberDelegationCards.length > 0) {
      return mergeMemberTimeline(base, memberDelegationCards);
    }
    return base;
  }, [memberItems, memberMode, memberTraceCollapsed, memberDelegationCards]);
  const memberHistoryLoading = useMemo(() => {
    if (!memberMode || !focusedMember) {
      return false;
    }
    return shouldShowMemberHistoryLoading({
      memberId: focusedMember.id,
      memberItems,
      team,
      teamHistoryLoading,
    });
  }, [memberMode, focusedMember, memberItems, team, teamHistoryLoading]);
  const showTopTeamViz =
    team != null
    && !showDelegationDock
    && teamLayout !== 'delegation'
    && (items.length > 0 || team.phase === 'running' || team.phase === 'synthesizing');
  const { containerRef, showScrollButton, scrollToBottom, onScroll } = useChatScroll({
    itemCount: visibleItems.length,
    streaming,
  });

  const onBusLaneSelect = useCallback((highlight: BusLaneHighlight) => {
    setBusLaneHighlight(highlight);
  }, []);

  useEffect(() => {
    setBusLaneHighlight(null);
  }, [team?.pattern, team?.busEntryCount]);

  useEffect(() => {
    if (busLaneHighlight) {
      teamVizRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
  }, [busLaneHighlight]);

  useEffect(() => {
    if (!searchHighlightId) {
      return;
    }
    const node = containerRef.current?.querySelector(
      `[data-message-id="${searchHighlightId}"]`,
    );
    node?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [containerRef, searchHighlightId]);

  const memoryCompact = Boolean(team?.phase === 'done' && !streaming && !memoryInjectPreview?.trim());

  useEffect(() => {
    if (!sessionId || !team || team.phase !== 'done' || streaming) {
      return;
    }
    if (!teamBlackboardPath || !onOpenArtifact) {
      return;
    }
    // Respect the collapsed-by-default panel: only switch the preview to the
    // blackboard when the user already has the detail panel open. Don't
    // force-expand it on completion.
    if (!detailPanelVisible) {
      return;
    }
    if (blackboardAutoOpenedRef.current === sessionId) {
      return;
    }
    blackboardAutoOpenedRef.current = sessionId;
    onOpenArtifact(teamBlackboardPath);
  }, [sessionId, team, teamBlackboardPath, streaming, onOpenArtifact, detailPanelVisible]);

  const handleSelectTeamMember = useCallback(
    (member: { id: string; name: string }) => {
      if (showDelegationDock && team && isTeamMemberFollowUpAvailable(team)) {
        setFollowUpMentionSeed({
          type: 'member',
          id: member.id,
          label: member.name,
        });
        setFocusedMember({ id: member.id, name: member.name });
        return;
      }
      if (showDelegationDock) {
        setFocusedMember({ id: member.id, name: member.name });
        return;
      }
      onOpenTeamMember?.(member.id, member.name);
    },
    [onOpenTeamMember, showDelegationDock, team],
  );

  return (
    <main className="chat-panel chat-panel-session">
      <SessionChatHeader
        title={sessionTitle}
        titleLoading={sessionLoading}
        streaming={streaming}
        streamStage={streamStage}
        queueDepth={queueDepth}
        onClearQueue={onClearQueue}
        clearQueueBusy={clearQueueBusy}
        searchOpen={searchOpen}
        onToggleSearch={() => setSearchOpen((value) => !value)}
        onShare={onShare}
        shareBusy={shareBusy}
        shareToast={shareToast}
        detailPanelVisible={detailPanelVisible}
        detailPanelAvailable={detailPanelAvailable}
        onToggleDetailPanel={onToggleDetailPanel}
        onOpenArtifacts={
          detailPanelAvailable && onOpenDetailTab
            ? () => onOpenDetailTab('artifacts')
            : undefined
        }
        archived={archived}
        onArchive={onArchive}
        onImportSidecarNdjson={onImportSidecarNdjson}
        onRelaySidecarStream={onRelaySidecarStream}
        sidecarImportBusy={sidecarImportBusy}
        cloudSessionStatus={cloudSessionStatus}
        onCloudBadgeClick={onCloudBadgeClick}
        onChangeExpert={onChangeExpert}
      />
      {sidecarFileInputRef && onSidecarFileChange && (
        <input
          ref={sidecarFileInputRef}
          type="file"
          accept=".ndjson,application/x-ndjson,text/plain"
          hidden
          aria-hidden
          onChange={onSidecarFileChange}
        />
      )}

      <SessionChatSearch
        items={visibleItems}
        open={searchOpen}
        onClose={() => setSearchOpen(false)}
        onActiveHitChange={setSearchHighlightId}
      />
      {memberMode && searchOpen && (
        <div className="session-chat-search-scope-hint">
          当前仅搜索「{focusedMember?.name ?? '成员'}」视图；点“返回主会话”可搜索全量对话
        </div>
      )}
      {memberMode && focusedMember && team && (
        <MemberFocusMetadataBar
          memberId={focusedMember.id}
          memberName={focusedMember.name}
          team={team}
          items={memberItems}
          streaming={streaming}
        />
      )}

      <div className="chat-body">
        {runError && onDismissRunError && (
          <RunErrorBanner
            message={runError.message}
            canRetry={Boolean(onRetryRunError)}
            onRetry={onRetryRunError}
            onDismiss={onDismissRunError}
          />
        )}
        <MemoryBanner
          enabled={memoryEnabled}
          injectPreview={memoryInjectPreview}
          compact={memoryCompact}
          onOpenSettings={onOpenMemorySettings}
          onViewMemory={onViewMemory}
          onRememberSession={onRememberSession}
          rememberBusy={rememberSessionBusy}
          canRememberSession={canRememberSession}
        />
        {showTopTeamViz && (
          <div ref={teamVizRef} className="session-team-viz-anchor">
            <TeamVisualization
              team={team}
              labels={teamLabels}
              busLaneHighlight={busLaneHighlight}
              onBusLaneSelect={onBusLaneSelect}
              onSelectMember={onOpenTeamMember ? handleSelectTeamMember : undefined}
              blackboardPath={teamBlackboardPath}
              onOpenBlackboard={onOpenArtifact}
            />
          </div>
        )}
        {memberMode && focusedMember && (
          <div className="member-focus-banner">
            <span className="member-focus-banner-label">
              <span className="member-focus-banner-dot" aria-hidden>👁</span>
              正在查看成员工作 · <strong>{focusedMember.name}</strong>
            </span>
            {memberItems.length > 14 && (
              <button
                type="button"
                className="member-focus-banner-toggle"
                onClick={() => setMemberTraceCollapsed((prev) => !prev)}
              >
                {memberTraceCollapsed ? `展开全部 ${memberItems.length} 条` : '收起旧记录'}
              </button>
            )}
            <button
              type="button"
              className="member-focus-banner-back"
              onClick={() => setFocusedMember(null)}
            >
              ↩ 返回主会话
            </button>
          </div>
        )}
        <div
          ref={containerRef}
          className={`chat-messages-wrap${memberMode ? ' chat-messages-wrap-member' : ''}`}
          onScroll={onScroll}
        >
          {memberHistoryLoading && (
            <MemberHistoryLoadingBanner memberName={focusedMember?.name} />
          )}
          {memberMode && visibleItems.length === 0 && !memberHistoryLoading && (
            <p className="member-focus-empty muted">该成员暂无可显示的工作记录。</p>
          )}
          <ChatMessageList
            sessionId={sessionId}
            items={visibleItems}
            hideMemberLabels={memberMode}
            streaming={streaming}
            assistantBrand={assistantBrand}
            promptTokens={promptTokens}
            completionTokens={completionTokens}
            expert={expert}
            approvalBusy={approvalBusy}
            questionBusy={questionBusy}
            onApprovalDecide={onApprovalDecide}
            onQuestionAnswer={onQuestionAnswer}
            onQuestionSkip={onQuestionSkip}
            onEditMessage={onEditMessage}
            onRetry={onRetry}
            highlightItemId={searchHighlightId}
            busLaneHighlight={busLaneHighlight}
            busLanes={team?.busLanes}
            onBusPublishHighlight={(topic, preview) => {
              if (!team?.busLanes) {
                return;
              }
              const match = findBusLaneHighlight(team.busLanes, topic, preview);
              if (match) {
                onBusLaneSelect(match);
              }
            }}
            onOpenArtifact={onOpenArtifact}
            onOpenChanges={onOpenChanges}
            onOpenDetailTab={detailPanelAvailable ? onOpenDetailTab : undefined}
            knownWorkspacePaths={knownWorkspacePaths}
            sessionArtifactCount={sessionArtifactCount}
            teamBlackboardPath={teamBlackboardPath}
            onConfirmPlan={onConfirmPlan}
            confirmingPlan={confirmingPlan}
            onUpdatePlanSteps={onUpdatePlanSteps}
            savingPlan={savingPlan}
          />
          <TaskProgressCard
            items={items}
            visibleItems={visibleItems}
            onConfirmPlan={onConfirmPlan}
            confirmingPlan={confirmingPlan}
            onUpdatePlanSteps={onUpdatePlanSteps}
            savingPlan={savingPlan}
          />
          <ScrollToBottomButton
            visible={showScrollButton}
            onClick={() => scrollToBottom('smooth')}
          />
        </div>
        <LoadingTips streaming={streaming} streamStage={streamStage} />
        {showDelegationDock && team && (
          <div className="session-team-delegation-dock">
            <TeamMemberFollowUpHint team={team} />
            <TeamDelegationBar
              team={team}
              activeMemberId={focusedMember?.id ?? null}
              onSelectLead={() => setFocusedMember(null)}
              onSelectMember={handleSelectTeamMember}
              trailing={<TeamMemberBypassComposer sessionId={sessionId} team={team} />}
            />
          </div>
        )}
        <InputDock
          {...dock}
          centered={false}
          seedMention={followUpMentionSeed}
          onSeedMentionConsumed={() => setFollowUpMentionSeed(null)}
        />
      </div>
    </main>
  );
}

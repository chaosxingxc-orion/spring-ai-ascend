import { Fragment, useMemo, useState, type ReactNode } from 'react';
import type { ApprovalDecision, ApprovalDecisionScope, Expert } from '../../types/api';
import type { ChatItem } from '../../types/events';
import type { BusLaneHighlight } from '../../lib/busLaneHighlight';
import { memberDisplayLabel, resolveTeamUiLabels } from '../../lib/teamUiLabels';
import type { TeamBusEntry } from '../../lib/teamStatus';
import { AskUserQuestionCard } from '../../components/AskUserQuestionCard';
import { BusinessApprovalCard } from '../../components/BusinessApprovalCard';
import { ToolCallGroup, groupToolItems } from '../../components/ToolCallGroup';
import { ToolCard } from '../../components/ToolCard';
import { ConsumptionBadge } from '../../features/session/ConsumptionBadge';
import { PlanCard } from '../../features/session/PlanCard';
import { DeepThinkingTrace } from '../../features/session/DeepThinkingTrace';
import { LeaderTurnCollapseToggle } from '../../features/session/LeaderTurnCollapseToggle';
import { ReasoningBlock } from '../../features/session/ReasoningBlock';
import { TurnSummaryChips } from '../../features/session/TurnSummaryChips';
import { UserMessageAttachments } from '../../components/UserMessageAttachments';
import { MentionContextCards } from '../../components/MentionContextCards';
import { MarkdownContent } from '../../components/MarkdownContent';
import { PlainTextContent } from '../../components/PlainTextContent';
import { ChatCheckpointDivider } from '../../components/ChatCheckpointDivider';
import { ChatExpertSwitchedDivider } from '../../components/ChatExpertSwitchedDivider';
import { OpenResultCard } from '../../components/OpenResultCard';
import { MemberTaskMessage } from '../../components/MemberTaskMessage';
import { delegationInputFromCard } from '../../lib/delegationInput';
import { formatLeaderNarrationText } from '../../lib/leaderNarrationFormat';
import { lastUserSeq } from '../../lib/chatItems';
import { isMemberLifecycleStatusText } from '../../lib/memberChatProjection';
import {
  buildLeaderTurnViews,
  countTurnArtifacts,
  countTurnChanges,
  firstTurnHtmlPreviewPath,
  indexLeaderTurnViews,
  isFollowUpUserTurn,
  isLeaderTurnComplete,
  leaderTurnForItem,
  userTurnIndexById,
} from '../../lib/chatTurns';
import type { PlanStep } from '../../types/events';

interface ChatMessageListProps {
  items: ChatItem[];
  sessionId?: string;
  streaming: boolean;
  highlightItemId?: string | null;
  assistantBrand?: string;
  promptTokens?: number;
  completionTokens?: number;
  expert?: Expert | null;
  approvalBusy?: boolean;
  questionBusy?: boolean;
  onApprovalDecide?: (approvalId: string, decision: ApprovalDecision, scope?: ApprovalDecisionScope) => void;
  onQuestionAnswer?: (questionId: string, selections: string[], text?: string) => void;
  onQuestionSkip?: (questionId: string) => void;
  onEditMessage?: (seq: number, text: string) => void;
  onRetry?: (fromSeq: number) => void;
  busLaneHighlight?: BusLaneHighlight | null;
  busLanes?: Record<string, TeamBusEntry[]>;
  onBusPublishHighlight?: (topic: string, preview?: string) => void;
  onOpenArtifact?: (path: string, tab?: 'browser' | 'changes') => void;
  onOpenChanges?: (path: string) => void;
  onOpenDetailTab?: (tab: 'artifacts' | 'changes') => void;
  knownWorkspacePaths?: ReadonlySet<string>;
  sessionArtifactCount?: number;
  teamBlackboardPath?: string | null;
  onConfirmPlan?: (planId: string) => void;
  confirmingPlan?: boolean;
  onUpdatePlanSteps?: (planId: string, steps: PlanStep[], title?: string) => void | Promise<void>;
  savingPlan?: boolean;
  /** Suppress per-message member name badges (e.g. when a member-focus banner already names them). */
  hideMemberLabels?: boolean;
  /** Rendered as the first node inside the scrolling message list (e.g. a member's delegated task),
   * so it scrolls together with the conversation instead of pinning above the inner scroll region. */
  leading?: ReactNode;
}

function indexOfLastUser(items: ChatItem[]): number {
  for (let index = items.length - 1; index >= 0; index -= 1) {
    if (items[index].kind === 'user') {
      return index;
    }
  }
  return -1;
}

function messageClass(base: string, itemId: string, highlightItemId?: string | null): string {
  return highlightItemId === itemId ? `${base} search-hit-active` : base;
}

export function ChatMessageList({
  items,
  sessionId,
  streaming,
  highlightItemId,
  assistantBrand = 'WorkMate',
  promptTokens = 0,
  completionTokens = 0,
  expert,
  approvalBusy = false,
  questionBusy = false,
  onApprovalDecide,
  onQuestionAnswer,
  onQuestionSkip,
  onEditMessage,
  onRetry,
  busLaneHighlight,
  busLanes,
  onBusPublishHighlight,
  onOpenArtifact,
  onOpenChanges,
  onOpenDetailTab,
  knownWorkspacePaths,
  sessionArtifactCount = 0,
  teamBlackboardPath,
  onConfirmPlan,
  confirmingPlan = false,
  onUpdatePlanSteps,
  savingPlan = false,
  hideMemberLabels = false,
  leading,
}: ChatMessageListProps) {
  const labels = resolveTeamUiLabels(expert);
  const userTurnById = useMemo(() => userTurnIndexById(items), [items]);
  const streamingMemberAssistantIds = useMemo(() => {
    if (!streaming) {
      return new Set<string>();
    }
    const lastByMember = new Map<string, string>();
    for (const item of items) {
      if (item.kind === 'assistant' && item.memberId) {
        lastByMember.set(item.memberId, item.id);
      }
    }
    const ids = new Set<string>();
    for (const item of items) {
      if (item.kind === 'assistant' && item.memberId && !item.text) {
        if (lastByMember.get(item.memberId) === item.id) {
          ids.add(item.id);
        }
      }
    }
    return ids;
  }, [items, streaming]);
  const memberLabelForTopic = (topic: string) => memberDisplayLabel(expert, topic);
  const memberLabel = (memberId: string) => memberDisplayLabel(expert, memberId);
  const lastUserIndex = indexOfLastUser(items);
  const retryFromSeq = lastUserSeq(items);
  const turnItems = lastUserIndex >= 0 ? items.slice(lastUserIndex + 1) : items;
  const turnAssistantIds = turnItems
    .filter((item) => item.kind === 'assistant')
    .map((item) => item.id);
  const latestAssistantId = turnAssistantIds[turnAssistantIds.length - 1];
  const turnArtifactCount = countTurnArtifacts(items);
  const turnChangeCount = countTurnChanges(items);
  const turnPreviewPath = firstTurnHtmlPreviewPath(items);
  const brandLabel = `${assistantBrand} AI`;
  const [editingSeq, setEditingSeq] = useState<number | null>(null);
  const [editingText, setEditingText] = useState('');
  const leaderTurnViews = useMemo(() => buildLeaderTurnViews(items), [items]);
  const { byItemId: leaderTurnByItemId } = useMemo(
    () => indexLeaderTurnViews(leaderTurnViews),
    [leaderTurnViews],
  );
  const [userExpandedTurnKeys, setUserExpandedTurnKeys] = useState<Set<string>>(() => new Set());
  const [userCollapsedTurnKeys, setUserCollapsedTurnKeys] = useState<Set<string>>(() => new Set());

  const isTurnCollapsed = (turnKey: string, isLatestTurn: boolean): boolean => {
    if (isLatestTurn && streaming) {
      return false;
    }
    if (userExpandedTurnKeys.has(turnKey)) {
      return false;
    }
    if (userCollapsedTurnKeys.has(turnKey)) {
      return true;
    }
    return !isLatestTurn;
  };

  const toggleTurnCollapsed = (turnKey: string, isLatestTurn: boolean) => {
    const collapsed = isTurnCollapsed(turnKey, isLatestTurn);
    if (collapsed) {
      setUserExpandedTurnKeys((prev) => new Set(prev).add(turnKey));
      setUserCollapsedTurnKeys((prev) => {
        const next = new Set(prev);
        next.delete(turnKey);
        return next;
      });
      return;
    }
    setUserCollapsedTurnKeys((prev) => new Set(prev).add(turnKey));
    setUserExpandedTurnKeys((prev) => {
      const next = new Set(prev);
      next.delete(turnKey);
      return next;
    });
  };

  const toolProps = {
    labels,
    memberLabelForTopic,
    memberLabel,
    busLaneHighlight,
    busLanes,
    onBusPublishHighlight,
    onOpenChanges,
  };

  const startEdit = (seq: number, text: string) => {
    setEditingSeq(seq);
    setEditingText(text);
  };

  const cancelEdit = () => {
    setEditingSeq(null);
    setEditingText('');
  };

  const submitEdit = () => {
    if (editingSeq == null || !editingText.trim() || !onEditMessage) {
      return;
    }
    onEditMessage(editingSeq, editingText.trim());
    cancelEdit();
  };

  return (
    <div className="chat-messages chat-messages-workmate">
      {leading}
      {groupToolItems(items).map((item) => {
        const leaderTurn = leaderTurnForItem(item, leaderTurnByItemId);
        const turnCollapsed = leaderTurn
          ? isTurnCollapsed(leaderTurn.turnKey, leaderTurn.isLatestTurn)
          : false;
        const hideCollapsedLeaderItem = Boolean(
          turnCollapsed
          && leaderTurn
          && leaderTurn.leaderScopedIds.has(
            item.kind === 'tool-group' ? item.tools[0]?.id ?? '' : item.id,
          )
          && item.id !== leaderTurn.lastLeaderAssistantId
          && (item.kind === 'tool' || item.kind === 'tool-group'),
        );
        const showDeepThinking = leaderTurn
          && item.id === leaderTurn.firstLeaderItemId
          && (leaderTurn.reasoningText || (leaderTurn.isLatestTurn && streaming))
          && !turnCollapsed;
        const deepThinkingNode = showDeepThinking ? (
          <DeepThinkingTrace
            key={`deep-thinking-${leaderTurn.turnKey}`}
            reasoningText={leaderTurn.reasoningText}
            streaming={streaming && leaderTurn.isLatestTurn}
            labels={labels}
            memberLabelForTopic={memberLabelForTopic}
          />
        ) : null;

        if (leaderTurn?.leaderReasoningItemIds.has(item.id)) {
          if (item.id === leaderTurn.firstLeaderItemId) {
            return <Fragment key={item.id}>{deepThinkingNode}</Fragment>;
          }
          return null;
        }

        if (hideCollapsedLeaderItem) {
          return null;
        }

        if (item.kind === 'user') {
          const isEditing = editingSeq != null && item.seq === editingSeq;
          const showCheckpoint = isFollowUpUserTurn(userTurnById.get(item.id) ?? 0);
          return (
            <Fragment key={item.id}>
              {showCheckpoint && <ChatCheckpointDivider />}
              <div
                data-message-id={item.id}
                className={messageClass('message user', item.id, highlightItemId)}
              >
              {isEditing ? (
                <div className="message-edit-form">
                  <textarea
                    className="message-edit-input"
                    value={editingText}
                    placeholder="编辑消息后重发…"
                    rows={3}
                    autoFocus
                    onChange={(event) => setEditingText(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === 'Escape') {
                        cancelEdit();
                      }
                      if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
                        event.preventDefault();
                        submitEdit();
                      }
                    }}
                  />
                  <div className="message-edit-actions">
                    <button type="button" className="btn ghost sm" onClick={cancelEdit}>
                      取消
                    </button>
                    <button type="button" className="btn primary sm" onClick={submitEdit}>
                      重发
                    </button>
                  </div>
                </div>
              ) : (
                <>
                  <MentionContextCards mentions={item.mentions} />
                  <UserMessageAttachments sessionId={sessionId} attachments={item.attachments} />
                  <div className="message-body">{item.text}</div>
                  {!streaming && item.seq != null && onEditMessage && (
                    <div className="message-actions">
                      <button
                        type="button"
                        className="btn ghost sm message-action-btn"
                        onClick={() => startEdit(item.seq!, item.text)}
                      >
                        编辑
                      </button>
                    </div>
                  )}
                </>
              )}
              </div>
            </Fragment>
          );
        }
        if (item.kind === 'reasoning') {
          const memberWork = item.memberId
            ? (memberDisplayLabel(expert, item.memberId) ?? item.memberName ?? item.memberId)
            : null;
          const lifecycleStatus = memberWork && isMemberLifecycleStatusText(item.text);
          return (
            <div
              key={item.id}
              data-message-id={item.id}
              className={messageClass(
                `message reasoning-message${memberWork ? ' member-work-message' : ''}${lifecycleStatus ? ' member-lifecycle-message' : ''}`,
                item.id,
                highlightItemId,
              )}
            >
              {memberWork && !hideMemberLabels && (
                <div className="member-work-badge">
                  {memberWork}
                  {lifecycleStatus ? ' · 状态' : ' · 思考'}
                </div>
              )}
              {lifecycleStatus ? (
                <p className="member-lifecycle-status muted">{item.text}</p>
              ) : hideMemberLabels && memberWork ? (
                <PlainTextContent source={item.text} className="message-body member-plain-text" />
              ) : (
                <ReasoningBlock text={item.text} streaming={streaming} />
              )}
            </div>
          );
        }
        if (item.kind === 'assistant') {
          const memberWork = item.memberId
            ? (memberDisplayLabel(expert, item.memberId) ?? item.memberName ?? item.memberId)
            : null;
          if (
            leaderTurn
            && !memberWork
            && leaderTurn.lastLeaderAssistantId
            && item.id !== leaderTurn.lastLeaderAssistantId
          ) {
            return null;
          }
          if (memberWork) {
            return (
              <div
                key={item.id}
                data-message-id={item.id}
                className={messageClass('message assistant assistant-member', item.id, highlightItemId)}
              >
                {!hideMemberLabels && (
                  <div className="assistant-message-header">
                    <span className="member-work-badge">{memberWork}</span>
                  </div>
                )}
                {item.text && (
                  hideMemberLabels ? (
                    <PlainTextContent source={item.text} className="message-body member-plain-text" />
                  ) : (
                    <MarkdownContent
                      source={item.text}
                      className="message-body assistant-text markdown-body"
                      knownWorkspacePaths={knownWorkspacePaths}
                      onWorkspaceFileClick={onOpenArtifact}
                    />
                  )
                )}
                {streamingMemberAssistantIds.has(item.id) && (
                  <div className="message-body assistant-text muted">…</div>
                )}
              </div>
            );
          }
          const isLatestAssistant = item.id === latestAssistantId;
          const turnComplete = leaderTurn ? isLeaderTurnComplete(leaderTurn, streaming) : false;
          const showTurnCollapse = leaderTurn
            && item.id === leaderTurn.lastLeaderAssistantId
            && turnComplete;
          return (
            <Fragment key={item.id}>
              {deepThinkingNode}
              <div
                data-message-id={item.id}
                className={messageClass(
                  `message assistant assistant-workmate${turnCollapsed ? ' leader-turn-collapsed' : ''}`,
                  item.id,
                  highlightItemId,
                )}
              >
                <div className="assistant-message-header">
                  <span className="assistant-brand">
                    <span className="assistant-brand-icon" aria-hidden>🤖</span>
                    {brandLabel}
                  </span>
                  <ConsumptionBadge
                    streaming={streaming && isLatestAssistant}
                    textLength={item.text.length}
                    promptTokens={promptTokens}
                    completionTokens={completionTokens}
                  />
                  {showTurnCollapse && leaderTurn && (
                    <LeaderTurnCollapseToggle
                      collapsed={turnCollapsed}
                      onToggle={() => toggleTurnCollapsed(leaderTurn.turnKey, leaderTurn.isLatestTurn)}
                    />
                  )}
                  {!streaming && isLatestAssistant && retryFromSeq != null && onRetry && (
                    <button
                      type="button"
                      className="btn ghost sm message-action-btn"
                      onClick={() => onRetry(retryFromSeq)}
                    >
                      重试
                    </button>
                  )}
                </div>
                {item.text && (
                  <MarkdownContent
                    source={formatLeaderNarrationText(item.text)}
                    className={`message-body assistant-text markdown-body${turnCollapsed ? ' leader-turn-collapsed-preview' : ''}`}
                    knownWorkspacePaths={knownWorkspacePaths}
                    onWorkspaceFileClick={onOpenArtifact}
                  />
                )}
                {!turnCollapsed && streaming && isLatestAssistant && !item.text && (
                  <div className="message-body assistant-text muted">…</div>
                )}
                {!turnCollapsed && isLatestAssistant && !streaming && (
                  <TurnSummaryChips
                    artifactCount={turnArtifactCount}
                    sessionArtifactCount={sessionArtifactCount}
                    changeCount={turnChangeCount}
                    previewPath={turnPreviewPath}
                    teamBlackboardPath={teamBlackboardPath ?? undefined}
                    onOpenArtifacts={onOpenDetailTab ? () => onOpenDetailTab('artifacts') : undefined}
                    onOpenChanges={onOpenDetailTab ? () => onOpenDetailTab('changes') : undefined}
                    onOpenPreview={onOpenArtifact}
                    onOpenBlackboard={onOpenArtifact}
                  />
                )}
              </div>
            </Fragment>
          );
        }
        if (item.kind === 'tool') {
          const memberWork = item.memberId
            ? (memberDisplayLabel(expert, item.memberId) ?? item.memberName ?? item.memberId)
            : null;
          return (
            <Fragment key={item.id}>
              {deepThinkingNode}
              <div
                data-message-id={item.id}
                className={messageClass(
                  `message tool-message${memberWork ? ' member-work-message' : ''}`,
                  item.id,
                  highlightItemId,
                )}
              >
              {memberWork && !hideMemberLabels && (
                <div className="member-work-badge">{memberWork}</div>
              )}
              <ToolCard
                toolName={item.toolName}
                status={item.status}
                args={item.args}
                result={item.result}
                occurredAt={item.endedAt ?? item.startedAt}
                memberId={item.memberId}
                {...toolProps}
              />
              </div>
            </Fragment>
          );
        }
        if (item.kind === 'tool-group') {
          return (
            <Fragment key={item.id}>
              {deepThinkingNode}
              <div
                data-message-id={item.id}
                className={messageClass('message tool-group-message', item.id, highlightItemId)}
              >
                <ToolCallGroup tools={item.tools} {...toolProps} />
              </div>
            </Fragment>
          );
        }
        if (item.kind === 'plan') {
          return (
            <div
              key={item.id}
              data-message-id={item.id}
              className={messageClass('message plan-message', item.id, highlightItemId)}
            >
              <PlanCard
                planId={item.planId}
                title={item.title}
                steps={item.steps}
                confirmed={item.confirmed}
                confirming={confirmingPlan}
                saving={savingPlan}
                onConfirm={onConfirmPlan ? () => onConfirmPlan(item.planId) : undefined}
                onSaveSteps={
                  onUpdatePlanSteps
                    ? (steps, title) => onUpdatePlanSteps(item.planId, steps, title)
                    : undefined
                }
              />
            </div>
          );
        }
        if (item.kind === 'approval') {
          return (
            <div
              key={item.id}
              data-message-id={item.id}
              className={messageClass('message approval-message', item.id, highlightItemId)}
            >
              <BusinessApprovalCard
                tool={item.tool}
                summary={item.summary}
                reason={item.reason}
                risk={item.risk}
                args={item.args}
                status={item.status}
                busy={approvalBusy && item.status === 'pending'}
                labels={labels}
                onDecide={
                  item.status === 'pending' && onApprovalDecide
                    ? (decision, scope) => onApprovalDecide(item.approvalId, decision, scope)
                    : undefined
                }
              />
            </div>
          );
        }
        if (item.kind === 'question') {
          return (
            <div
              key={item.id}
              data-message-id={item.id}
              className={messageClass('message question-message', item.id, highlightItemId)}
            >
              <AskUserQuestionCard
                question={item.question}
                options={item.options}
                allowFreeText={item.allowFreeText}
                multiSelect={item.multiSelect}
                status={item.status}
                selections={item.selections}
                answerText={item.answerText}
                busy={questionBusy && item.status === 'pending'}
                onSubmit={
                  item.status === 'pending' && onQuestionAnswer
                    ? (selections, text) => onQuestionAnswer(item.questionId, selections, text)
                    : undefined
                }
                onSkip={
                  item.status === 'pending' && onQuestionSkip
                    ? () => onQuestionSkip(item.questionId)
                    : undefined
                }
              />
            </div>
          );
        }
        if (item.kind === 'artifact-cta') {
          return (
            <div
              key={item.id}
              data-message-id={item.id}
              className={messageClass('message artifact-cta-message', item.id, highlightItemId)}
            >
              <OpenResultCard
                path={item.path}
                name={item.name}
                mime={item.mime}
                preferredTab={item.preferredTab}
                onOpen={onOpenArtifact}
              />
            </div>
          );
        }
        if (item.kind === 'member-delegation') {
          if (!item.message && !item.description) {
            return null;
          }
          return (
            <div
              key={item.id}
              data-message-id={item.id}
              className={messageClass('message member-delegation', item.id, highlightItemId)}
            >
              <MemberTaskMessage
                delegation={delegationInputFromCard(item)}
                round={item.round}
              />
            </div>
          );
        }
        if (item.kind === 'expert-switched') {
          return (
            <ChatExpertSwitchedDivider
              key={item.id}
              fromExpertName={item.fromExpertName}
              toExpertName={item.toExpertName}
            />
          );
        }
        if (item.kind === 'system') {
          // Empty system notices (e.g. error toasts persisted with no text) would render as blank
          // grey bubbles; skip them entirely.
          if (!item.text || !item.text.trim()) {
            return null;
          }
          return (
            <div
              key={item.id}
              data-message-id={item.id}
              className={messageClass(`message system tone-${item.tone ?? 'info'}`, item.id, highlightItemId)}
            >
              {item.text}
            </div>
          );
        }
        return null;
      })}
      <div className="chat-messages-anchor" aria-hidden />
    </div>
  );
}

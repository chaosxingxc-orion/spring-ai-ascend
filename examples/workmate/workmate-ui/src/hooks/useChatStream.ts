import { useCallback, useRef, useState } from 'react';
import { getRunQueueStatus, clearRunQueue, listSessionRunEvents } from '../api/client';
import { resumeEventStream, streamEditMessage, streamPrompt, streamRetry } from '../api/sse';
import { invalidateSessionChatHydration, loadSessionChatItems } from '../lib/sessionChatLoad';
import { readSseEventSeq } from '../lib/chatItemOrder';
import { isTeamSurfacePayload } from '../lib/eventPayload';
import { isLeaderRunTerminal } from '../lib/memberEventProjection';
import { formatTeamBypassSystemText } from '../lib/teamRunEventHydrate';
import type { RunEventRow } from '../lib/reasoningHydrate';
import { parseQuestionRequired,
  parseArtifactAdded,
  parseApprovalRequired,
  parseExpertSwitched,
  parseMessageDelta,
  parsePlanCreate,
  parseRunError,
  parseTeamMemberEvent,
  parseTeamMemory,
  parseTeamBusPublished,
  parseTeamBusSubscribed,
  parseTeamCompleted,
  parseTeamStateProgress,
  parseTeamStarted,
  parseTeamBuildCompleted,
  parseTeamIteration,
  parseTeamVerify,
  parseToolEnd,
  parseToolStart,
  parseUsageDelta,
} from '../lib/eventPayload';
import type { TeamSnapshot } from '../types/api';
import {
  applyLeadSynthesizing,
  applyMemberEvent,
  applyMemberRuntimeError,
  applyMemberToolStart,
  applyMemberUsage,
  mergeTeamSnapshotStatuses,
  applyTeamCompletedPayload,
  applyTeamMemory,
  applyBusPublished,
  applyBusSubscribed,
  applyStateProgress,
  applyIterationStarted,
  applyVerifyAccepted,
  applyVerifyRejected,
  applyVerifyStarted,
  applyTeamStarted,
  applyTeamBuildCompleted,
  ensureDelegationVisualization,
  initialTeamState,
  isDelegationTeamState,
  type TeamState,
} from '../lib/teamStatus';
import type { ChatAction } from '../state/chatState';
import { isToolFailure } from '../state/chatState';
import type { StreamStage } from '../lib/streamStage';
import { classifyTool, writeDiffSummary } from '../lib/toolKind';
import { buildApprovalChatItem, isBusinessApprovalTool } from '../lib/businessApproval';
import { buildQuestionChatItem } from '../lib/questionCard';
import { resolveTeamUiLabels } from '../lib/teamUiLabels';
import type { ApprovalRequiredPayload, ChatItem, SseEvent } from '../types/events';
import type { Expert } from '../types/api';
import type { MentionRef } from '../types/mention';
import { isClientHttpError, readErrorMessage } from '../lib/httpError';

function uid(): string {
  return crypto.randomUUID();
}

function readTeamSurfaceMember(data: unknown): { memberId: string; memberName?: string } | null {
  if (!isTeamSurfacePayload(data) || data == null || typeof data !== 'object') {
    return null;
  }
  const record = data as Record<string, unknown>;
  const memberId = typeof record.memberId === 'string' ? record.memberId : '';
  if (!memberId) {
    return null;
  }
  const memberName = typeof record.memberName === 'string' ? record.memberName : undefined;
  return { memberId, memberName };
}

function readRedactedString(value: unknown): string | undefined {
  if (typeof value === 'string' && value.trim()) {
    return value;
  }
  if (value && typeof value === 'object') {
    const preview = (value as Record<string, unknown>).preview;
    if (typeof preview === 'string' && preview.trim()) {
      return preview;
    }
  }
  return undefined;
}

function readDelegationToolMember(data: unknown): { memberId: string; memberName?: string } | null {
  if (!data || typeof data !== 'object') {
    return null;
  }
  const record = data as Record<string, unknown>;
  const args = record.args && typeof record.args === 'object' ? (record.args as Record<string, unknown>) : null;
  const result = record.result && typeof record.result === 'object' ? (record.result as Record<string, unknown>) : null;
  const resultData = result?.data && typeof result.data === 'object'
    ? (result.data as Record<string, unknown>)
    : null;
  const routing = args?.routing && typeof args.routing === 'object'
    ? (args.routing as Record<string, unknown>)
    : null;
  const stripMention = (value?: string): string | undefined =>
    value ? value.replace(/^@/, '') : value;
  const memberId = stripMention(
    readRedactedString(args?.memberId)
    || readRedactedString(args?.to)
    || readRedactedString(args?.recipient)
    || readRedactedString(args?.target)
    || readRedactedString(routing?.target)
    || readRedactedString(resultData?.memberId),
  );
  if (!memberId) {
    return null;
  }
  const memberName = readRedactedString(args?.memberName) || readRedactedString(resultData?.memberName);
  return { memberId, memberName };
}

function isDelegationTeamTool(toolName: string): boolean {
  const normalized = toolName.toLowerCase();
  return normalized.includes('build_team') || normalized.includes('send_message');
}

type ChatDispatch = (
  action:
    | ChatAction
    | { type: 'update-session-usage'; sessionId: string; promptTokens: number; completionTokens: number }
    | { type: 'set-chat-from-server'; sessionId: string; items: ChatItem[] },
) => void;

export interface UseChatStreamOptions {
  dispatch: ChatDispatch;
  refreshSessions: () => Promise<unknown>;
  bumpArtifacts: () => void;
  onApprovalRequired: (sessionId: string, payload: ApprovalRequiredPayload) => void;
  onArtifactAdded: (path: string) => void;
  onWriteCompleted?: (sessionId: string, path: string) => void;
  onRunCompleted?: (sessionId: string) => void;
  resolveExpert?: (sessionId: string) => Expert | null | undefined;
}

export interface SessionRunError {
  message: string;
}

export function useChatStream({
  dispatch,
  refreshSessions,
  bumpArtifacts,
  onApprovalRequired,
  onArtifactAdded,
  onWriteCompleted,
  onRunCompleted,
  resolveExpert,
}: UseChatStreamOptions) {
  const [streamingBySession, setStreamingBySession] = useState<Record<string, boolean>>({});
  const [streamStageBySession, setStreamStageBySession] = useState<Record<string, StreamStage>>({});
  const [sessionErrorBySession, setSessionErrorBySession] = useState<Record<string, SessionRunError>>({});
  const [queueDepthBySession, setQueueDepthBySession] = useState<Record<string, number>>({});
  const [teamStateBySession, setTeamStateBySession] = useState<Record<string, TeamState>>({});
  const abortBySessionRef = useRef<Record<string, AbortController>>({});
  const assistantIdBySessionRef = useRef<Record<string, string>>({});
  /** Splits leader narration across question/approval cards when backend reuses one messageId. */
  const leaderTurnSegmentRef = useRef<Record<string, number>>({});
  const leaderReasoningIdBySessionRef = useRef<Record<string, string>>({});
  const lastEventIdBySessionRef = useRef<Record<string, string>>({});
  const pendingQueueFollowUpRef = useRef<Record<string, boolean>>({});
  const toolArgsByCallRef = useRef<Record<string, unknown>>({});
  /** When backend emits native tool.start for build_team, skip synth card on team.build.completed. */
  const nativeBuildTeamBySessionRef = useRef<Record<string, boolean>>({});
  const nativeSendTeamBySessionRef = useRef<Record<string, boolean>>({});
  const synthBuildTeamKeysBySessionRef = useRef<Record<string, Set<string>>>({});
  const synthSendByMemberRef = useRef<Record<string, Map<string, string>>>({});

  const closeLeaderTurnForInterjection = useCallback((sessionId: string) => {
    const baseId = assistantIdBySessionRef.current[sessionId];
    if (baseId) {
      leaderTurnSegmentRef.current[baseId] = (leaderTurnSegmentRef.current[baseId] ?? 0) + 1;
    }
    delete assistantIdBySessionRef.current[sessionId];
    delete leaderReasoningIdBySessionRef.current[sessionId];
  }, []);

  const resolveLeaderAssistantItemId = useCallback((baseMessageId: string): string => {
    const segment = leaderTurnSegmentRef.current[baseMessageId] ?? 0;
    return segment === 0 ? baseMessageId : `${baseMessageId}#${segment}`;
  }, []);

  const setStreamStage = useCallback((sessionId: string, stage: StreamStage) => {
    setStreamStageBySession((prev) => ({ ...prev, [sessionId]: stage }));
  }, []);

  const setSessionStreaming = useCallback((sessionId: string, streaming: boolean) => {
    setStreamingBySession((prev) => ({ ...prev, [sessionId]: streaming }));
    if (streaming) {
      setStreamStageBySession((prev) => ({ ...prev, [sessionId]: 'thinking' }));
    } else {
      setStreamStageBySession((prev) => {
        if (!prev[sessionId]) {
          return prev;
        }
        const next = { ...prev };
        delete next[sessionId];
        return next;
      });
    }
  }, []);

  const clearSessionError = useCallback((sessionId: string) => {
    setSessionErrorBySession((prev) => {
      if (!prev[sessionId]) {
        return prev;
      }
      const next = { ...prev };
      delete next[sessionId];
      return next;
    });
  }, []);

  const reportSessionError = useCallback((sessionId: string, message: string) => {
    const normalized = readErrorMessage(message);
    setSessionErrorBySession((prev) => ({ ...prev, [sessionId]: { message: normalized } }));
    return normalized;
  }, []);

  const syncQueueDepth = useCallback(async (sessionId: string) => {
    try {
      const status = await getRunQueueStatus(sessionId);
      setQueueDepthBySession((prev) => ({ ...prev, [sessionId]: status.depth }));
      return status.depth;
    } catch {
      return queueDepthBySession[sessionId] ?? 0;
    }
  }, [queueDepthBySession]);

  const clearQueue = useCallback(
    async (sessionId: string) => {
      try {
        const result = await clearRunQueue(sessionId);
        setQueueDepthBySession((prev) => ({ ...prev, [sessionId]: result.depth }));
        pendingQueueFollowUpRef.current[sessionId] = false;
        dispatch({
          type: 'append-item',
          sessionId,
          item: {
            id: uid(),
            kind: 'system',
            tone: 'info',
            text:
              result.cleared > 0
                ? `已清空排队消息（${result.cleared} 条）`
                : '当前无排队消息',
          },
        });
        return result.cleared;
      } catch (error) {
        reportSessionError(sessionId, (error as Error).message);
        return 0;
      }
    },
    [dispatch, reportSessionError],
  );

  const syncChatFromServer = useCallback(
    async (sessionId: string) => {
      try {
        const serverItems = await loadSessionChatItems(sessionId);
        dispatch({ type: 'set-chat-from-server', sessionId, items: serverItems });
      } catch {
        // keep optimistic projection when sync fails
      }
    },
    [dispatch],
  );

  const refreshMemberSurfaceFromEvents = useCallback(
    (sessionId: string, events: RunEventRow[]) => {
      dispatch({ type: 'refresh-member-surface-from-events', sessionId, events });
    },
    [dispatch],
  );

  const handleSseEventRef = useRef<(sessionId: string, event: SseEvent) => void>(() => undefined);
  const followQueuedRunRef = useRef<(sessionId: string) => Promise<void>>(async () => undefined);

  const handleSseEvent = useCallback(
    (sessionId: string, event: SseEvent) => {
      switch (event.name) {
        case 'message.delta': {
          const eventSeq = readSseEventSeq(event);
          if (isTeamSurfacePayload(event.data)) {
            const member = readTeamSurfaceMember(event.data);
            const payload = parseMessageDelta(event.data);
            if (member && payload.text) {
              dispatch({
                type: 'append-member-delta',
                sessionId,
                memberId: member.memberId,
                memberName: member.memberName,
                text: payload.text,
              });
            }
            break;
          }
          setStreamStage(sessionId, 'generating');
          const payload = parseMessageDelta(event.data);
          // Preferred path: the backend tags each delta with the open turn's messageId, so we
          // upsert one bubble per turn whose id equals the persisted message id (live == refresh,
          // no merge across tools, robust to concurrent member streaming).
          if (payload.messageId) {
            assistantIdBySessionRef.current[sessionId] = payload.messageId;
            dispatch({
              type: 'upsert-assistant-delta',
              sessionId,
              itemId: resolveLeaderAssistantItemId(payload.messageId),
              text: payload.text,
              seq: eventSeq,
            });
            break;
          }
          // Fallback (events without messageId): lazily open a bubble and close it on tool calls.
          const assistantId = assistantIdBySessionRef.current[sessionId];
          if (assistantId) {
            dispatch({
              type: 'append-text-by-id',
              sessionId,
              itemId: assistantId,
              text: payload.text,
            });
          } else {
            const newAssistantId = uid();
            assistantIdBySessionRef.current[sessionId] = newAssistantId;
            dispatch({
              type: 'append-item',
              sessionId,
              item: { id: newAssistantId, kind: 'assistant', text: payload.text },
            });
          }
          break;
        }
        case 'reasoning.delta': {
          const eventSeq = readSseEventSeq(event);
          if (isTeamSurfacePayload(event.data)) {
            const member = readTeamSurfaceMember(event.data);
            const payload = parseMessageDelta(event.data);
            if (member && payload.text) {
              setStreamStage(sessionId, 'thinking');
              dispatch({
                type: 'append-member-reasoning-delta',
                sessionId,
                memberId: member.memberId,
                memberName: member.memberName,
                text: payload.text,
              });
            }
            break;
          }
          setStreamStage(sessionId, 'thinking');
          const payload = parseMessageDelta(event.data);
          const reasoningId = leaderReasoningIdBySessionRef.current[sessionId];
          if (reasoningId) {
            dispatch({
              type: 'append-text-by-id',
              sessionId,
              itemId: reasoningId,
              text: payload.text,
            });
          } else {
            const newReasoningId = eventSeq != null ? `reasoning-${eventSeq}` : uid();
            leaderReasoningIdBySessionRef.current[sessionId] = newReasoningId;
            dispatch({
              type: 'append-item',
              sessionId,
              item: {
                id: newReasoningId,
                kind: 'reasoning',
                text: payload.text,
                seq: eventSeq,
              },
            });
          }
          break;
        }
        case 'plan.update': {
          const payload = parsePlanCreate(event.data);
          if (payload) {
            dispatch({ type: 'upsert-plan', sessionId, plan: payload });
          }
          break;
        }
        case 'tool.start': {
          const eventSeq = readSseEventSeq(event);
          const payload = parseToolStart(event.data);
          const delegationTool = isDelegationTeamTool(payload.toolName);
          const delegationMember = delegationTool ? readDelegationToolMember(event.data) : null;
          if (isTeamSurfacePayload(event.data)) {
            if (delegationTool) {
              break;
            }
            const member = readTeamSurfaceMember(event.data);
            if (payload.toolCallId) {
              toolArgsByCallRef.current[`${sessionId}:${payload.toolCallId}`] = payload.args;
            }
            setStreamStage(sessionId, 'tool');
            dispatch({
              type: 'append-item',
              sessionId,
              item: {
                id: payload.toolCallId ?? uid(),
                kind: 'tool',
                toolName: payload.toolName,
                toolCallId: payload.toolCallId,
                status: 'executing',
                args: payload.args,
                seq: eventSeq,
                startedAt: eventSeq ?? Date.now(),
                memberId: member?.memberId,
                memberName: member?.memberName,
              },
            });
            if (member?.memberId) {
              setTeamStateBySession((prev) => {
                const next = applyMemberToolStart(prev[sessionId] ?? null, member.memberId);
                return next ? { ...prev, [sessionId]: next } : prev;
              });
            }
            break;
          }
          if (payload.toolCallId) {
            toolArgsByCallRef.current[`${sessionId}:${payload.toolCallId}`] = payload.args;
          }
          if (delegationTool && payload.toolName.toLowerCase().includes('build_team')) {
            nativeBuildTeamBySessionRef.current[sessionId] = true;
          }
          if (delegationTool && payload.toolName.toLowerCase().includes('send_message')) {
            nativeSendTeamBySessionRef.current[sessionId] = true;
          }
          // A leader tool call ends the current leader turn (same as question/approval interjections).
          closeLeaderTurnForInterjection(sessionId);
          setStreamStage(sessionId, 'tool');
          dispatch({
            type: 'append-item',
            sessionId,
            item: {
              id: payload.toolCallId ?? uid(),
              kind: 'tool',
              toolName: payload.toolName,
              toolCallId: payload.toolCallId,
              status: 'executing',
              args: payload.args,
              seq: eventSeq,
              startedAt: eventSeq ?? Date.now(),
              // Delegation (build_team / send_message) cards are the leader's own action and must
              // render in the leader timeline at their step — never tagged to the member scope.
            },
          });
          if (delegationMember?.memberId) {
            setTeamStateBySession((prev) => {
              const next = applyMemberToolStart(prev[sessionId] ?? null, delegationMember.memberId);
              return next ? { ...prev, [sessionId]: next } : prev;
            });
          }
          break;
        }
        case 'tool.end': {
          const payload = parseToolEnd(event.data);
          const delegationTool = isDelegationTeamTool(payload.toolName);
          if (isTeamSurfacePayload(event.data)) {
            if (delegationTool) {
              break;
            }
            const surfaceArgsKey = payload.toolCallId ? `${sessionId}:${payload.toolCallId}` : null;
            if (surfaceArgsKey) {
              delete toolArgsByCallRef.current[surfaceArgsKey];
            }
            setStreamStage(sessionId, 'thinking');
            dispatch({
              type: 'tool-end',
              sessionId,
              toolName: payload.toolName,
              toolCallId: payload.toolCallId,
              result: payload.result,
              failed: isToolFailure(payload.result),
            });
            break;
          }
          const argsKey = payload.toolCallId ? `${sessionId}:${payload.toolCallId}` : null;
          const args = argsKey ? toolArgsByCallRef.current[argsKey] : undefined;
          if (argsKey) {
            delete toolArgsByCallRef.current[argsKey];
          }
          if (
            payload.status === 'success'
            && classifyTool(payload.toolName, args) === 'write'
          ) {
            const summary = writeDiffSummary(args, payload.result);
            if (summary?.path) {
              onWriteCompleted?.(sessionId, summary.path);
            }
          }
          setStreamStage(sessionId, 'thinking');
          dispatch({
            type: 'tool-end',
            sessionId,
            toolName: payload.toolName,
            toolCallId: payload.toolCallId,
            result: payload.result,
            failed: isToolFailure(payload.result),
          });
          break;
        }
        case 'approval.required': {
          const payload = parseApprovalRequired(event.data);
          if (payload) {
            const normalized = { ...payload, sessionId: payload.sessionId ?? sessionId };
            if (isBusinessApprovalTool(payload.tool)) {
              closeLeaderTurnForInterjection(sessionId);
              dispatch({
                type: 'upsert-approval',
                sessionId,
                item: buildApprovalChatItem(normalized),
              });
            } else {
              dispatch({
                type: 'tool-waiting',
                sessionId,
                toolName: payload.tool,
                toolCallId: payload.toolCallId,
              });
              onApprovalRequired(sessionId, normalized);
            }
          }
          break;
        }
        case 'question.required': {
          const eventSeq = readSseEventSeq(event);
          const payload = parseQuestionRequired(event.data);
          if (payload) {
            const normalized = { ...payload, sessionId: payload.sessionId ?? sessionId };
            closeLeaderTurnForInterjection(sessionId);
            dispatch({
              type: 'upsert-question',
              sessionId,
              item: buildQuestionChatItem(normalized, eventSeq),
            });
            if (payload.toolName) {
              dispatch({
                type: 'tool-waiting',
                sessionId,
                toolName: payload.toolName,
              });
            }
          }
          break;
        }
        case 'question.cancelled': {
          const payload = parseQuestionRequired(event.data);
          if (payload) {
            const resolvedSessionId = payload.sessionId ?? sessionId;
            dispatch({
              type: 'resolve-question',
              sessionId: resolvedSessionId,
              questionId: payload.questionId,
              status: 'cancelled',
              selections: [],
              answerText: undefined,
            });
            if (payload.toolName) {
              dispatch({
                type: 'tool-resume',
                sessionId: resolvedSessionId,
                toolName: payload.toolName,
              });
            }
          }
          break;
        }
        case 'expert.switched': {
          const item = parseExpertSwitched(event.data);
          if (item) {
            const eventSeq = readSseEventSeq(event);
            dispatch({
              type: 'append-item',
              sessionId,
              item: eventSeq != null ? { ...item, seq: eventSeq } : item,
            });
          }
          break;
        }
        case 'artifact.added': {
          const payload = parseArtifactAdded(event.data);
          if (payload?.path) {
            onArtifactAdded(payload.path);
            if (payload.openInPanel) {
              dispatch({
                type: 'append-item',
                sessionId,
                item: {
                  id: `artifact-cta-${payload.path}-${Date.now()}`,
                  kind: 'artifact-cta',
                  path: payload.path,
                  name: payload.name,
                  mime: payload.mime,
                  preferredTab: payload.preferredTab,
                },
              });
            }
          }
          bumpArtifacts();
          break;
        }
        case 'plan.create': {
          const payload = parsePlanCreate(event.data);
          if (payload) {
            dispatch({ type: 'upsert-plan', sessionId, plan: payload });
          }
          break;
        }
        case 'usage.delta': {
          const payload = parseUsageDelta(event.data);
          if (payload) {
            dispatch({
              type: 'update-session-usage',
              sessionId,
              promptTokens: payload.totalPromptTokens,
              completionTokens: payload.totalCompletionTokens,
            });
            if (payload.memberId) {
              setTeamStateBySession((prev) => {
                const next = applyMemberUsage(
                  prev[sessionId] ?? null,
                  payload.memberId!,
                  payload.deltaPromptTokens ?? 0,
                  payload.deltaCompletionTokens ?? 0,
                );
                return next ? { ...prev, [sessionId]: next } : prev;
              });
            }
          }
          break;
        }
        case 'team.started': {
          const payload = parseTeamStarted(event.data);
          if (payload) {
            setTeamStateBySession((prev) => ({
              ...prev,
              [sessionId]: applyTeamStarted(prev[sessionId] ?? null, payload),
            }));
          }
          break;
        }
        case 'team.build.completed': {
          const eventSeq = readSseEventSeq(event);
          const payload = parseTeamBuildCompleted(event.data);
          if (payload) {
            // Close the pre-build narration segment before the build_team card lands so post-build
            // text opens a fresh bubble (mirrors backend closeAssistantTurn on tool.start).
            closeLeaderTurnForInterjection(sessionId);
            if (!nativeBuildTeamBySessionRef.current[sessionId]) {
              const buildKey = payload.teamName || payload.displayName || 'team';
              const seen = synthBuildTeamKeysBySessionRef.current[sessionId] ?? new Set<string>();
              if (!seen.has(buildKey)) {
                seen.add(buildKey);
                synthBuildTeamKeysBySessionRef.current[sessionId] = seen;
                const id = `team-build-synth-${eventSeq ?? Date.now()}`;
                setStreamStage(sessionId, 'tool');
                dispatch({
                  type: 'append-item',
                  sessionId,
                  item: {
                    id,
                    kind: 'tool',
                    toolName: 'team.build_team',
                    toolCallId: id,
                    status: 'success',
                    args: {
                      team_name: payload.teamName,
                      display_name: payload.displayName,
                      teamName: payload.teamName,
                      displayName: payload.displayName,
                    },
                    result: { success: true, data: payload },
                    seq: eventSeq,
                    startedAt: eventSeq ?? Date.now(),
                  },
                });
              }
            }
            setTeamStateBySession((prev) => {
              const next = applyTeamBuildCompleted(prev[sessionId] ?? null, payload);
              return next ? { ...prev, [sessionId]: next } : prev;
            });
          }
          break;
        }
        case 'team.member.message': {
          const eventSeq = readSseEventSeq(event);
          if (event.data && typeof event.data === 'object') {
            dispatch({
              type: 'append-item',
              sessionId,
              item: {
                id: `team-bypass-live-${eventSeq ?? Date.now()}`,
                kind: 'system',
                text: formatTeamBypassSystemText(event.data as Record<string, unknown>),
                tone: 'info',
                seq: eventSeq,
              },
            });
          }
          break;
        }
        case 'team.member.started':
        case 'team.member.reawakened':
        case 'team.member.completed':
        case 'team.member.paused':
        case 'team.member.failed': {
          const eventSeq = readSseEventSeq(event);
          const payload = parseTeamMemberEvent(event.data);
          if (payload) {
            if ((event.name === 'team.member.started' || event.name === 'team.member.reawakened')
                && !nativeSendTeamBySessionRef.current[sessionId]) {
              const memberId = payload.memberId;
              if (memberId) {
                const byMember = synthSendByMemberRef.current[sessionId] ?? new Map<string, string>();
                const id = `team-send-synth-${eventSeq ?? Date.now()}-${memberId}`;
                byMember.set(memberId, id);
                synthSendByMemberRef.current[sessionId] = byMember;
                dispatch({
                  type: 'append-item',
                  sessionId,
                  item: {
                    id,
                    kind: 'tool',
                    toolName: 'team.send_message',
                    toolCallId: id,
                    status: 'executing',
                    args: {
                      memberId,
                      to: memberId,
                      memberName: payload.memberName,
                      reawaken: event.name === 'team.member.reawakened',
                    },
                    seq: eventSeq,
                    startedAt: eventSeq ?? Date.now(),
                  },
                });
              }
            }
            const kind =
              event.name === 'team.member.started'
                ? 'started'
                : event.name === 'team.member.reawakened'
                  ? 'reawakened'
                  : event.name === 'team.member.completed'
                    ? 'completed'
                    : event.name === 'team.member.paused'
                      ? 'paused'
                      : 'failed';
            setTeamStateBySession((prev) => ({
              ...prev,
              [sessionId]: applyMemberEvent(prev[sessionId] ?? null, kind, payload),
            }));
            if (
              event.name === 'team.member.completed'
              || event.name === 'team.member.paused'
              || event.name === 'team.member.failed'
            ) {
              const synthId = payload.memberId
                ? synthSendByMemberRef.current[sessionId]?.get(payload.memberId)
                : undefined;
              if (synthId) {
                dispatch({
                  type: 'tool-end',
                  sessionId,
                  toolName: 'team.send_message',
                  toolCallId: synthId,
                  failed: event.name === 'team.member.failed',
                  result: {
                    success: event.name !== 'team.member.failed',
                    data: {
                      memberId: payload.memberId,
                      memberName: payload.memberName,
                      paused: event.name === 'team.member.paused',
                      error: payload.error,
                    },
                  },
                });
                synthSendByMemberRef.current[sessionId]?.delete(payload.memberId);
              }
              const lifecycleText =
                event.name === 'team.member.paused'
                  ? '已暂停，等待下一轮…'
                  : kind === 'completed'
                    ? (payload.summary?.trim() || '任务完成')
                    : (payload.error?.trim() || '任务失败');
              dispatch({
                type: 'append-member-reasoning-delta',
                sessionId,
                memberId: payload.memberId,
                memberName: payload.memberName,
                text: lifecycleText,
              });
            }
          }
          break;
        }
        case 'team.lead.synthesizing': {
          setTeamStateBySession((prev) => {
            const next = applyLeadSynthesizing(prev[sessionId] ?? null);
            return next ? { ...prev, [sessionId]: next } : prev;
          });
          break;
        }
        case 'team.iteration.started': {
          const payload = parseTeamIteration(event.data);
          if (payload) {
            setTeamStateBySession((prev) => {
              const next = applyIterationStarted(prev[sessionId] ?? null, payload);
              return next ? { ...prev, [sessionId]: next } : prev;
            });
          }
          break;
        }
        case 'team.state.progress': {
          const payload = parseTeamStateProgress(event.data);
          if (payload) {
            setTeamStateBySession((prev) => {
              const next = applyStateProgress(prev[sessionId] ?? null, payload);
              return next ? { ...prev, [sessionId]: next } : prev;
            });
          }
          break;
        }
        case 'team.verify.started': {
          const payload = parseTeamVerify(event.data);
          if (payload) {
            setTeamStateBySession((prev) => {
              const next = applyVerifyStarted(prev[sessionId] ?? null, payload);
              return next ? { ...prev, [sessionId]: next } : prev;
            });
          }
          break;
        }
        case 'team.verify.accepted': {
          const payload = parseTeamVerify(event.data);
          if (payload) {
            setTeamStateBySession((prev) => {
              const next = applyVerifyAccepted(prev[sessionId] ?? null, payload);
              return next ? { ...prev, [sessionId]: next } : prev;
            });
          }
          break;
        }
        case 'team.verify.rejected': {
          const payload = parseTeamVerify(event.data);
          if (payload) {
            setTeamStateBySession((prev) => {
              const next = applyVerifyRejected(prev[sessionId] ?? null, payload);
              return next ? { ...prev, [sessionId]: next } : prev;
            });
            const labels = resolveTeamUiLabels(resolveExpert?.(sessionId));
            const feedback = payload.feedback?.trim();
            const text = feedback
              ? `${labels.gvRejectedPrefix}：${feedback}`
              : labels.gvRejected;
            dispatch({
              type: 'append-item',
              sessionId,
              item: {
                id: uid(),
                kind: 'system',
                tone: 'error',
                text: `⚠️ ${text}`,
              },
            });
          }
          break;
        }
        case 'team.memory': {
          const payload = parseTeamMemory(event.data);
          if (payload) {
            setTeamStateBySession((prev) => ({
              ...prev,
              [sessionId]: applyTeamMemory(prev[sessionId] ?? null, payload),
            }));
          }
          break;
        }
        case 'team.bus.published': {
          const payload = parseTeamBusPublished(event.data);
          if (payload) {
            setTeamStateBySession((prev) => ({
              ...prev,
              [sessionId]: applyBusPublished(prev[sessionId] ?? null, payload),
            }));
          }
          break;
        }
        case 'team.bus.subscribed': {
          const payload = parseTeamBusSubscribed(event.data);
          if (payload) {
            setTeamStateBySession((prev) => ({
              ...prev,
              [sessionId]: applyBusSubscribed(prev[sessionId] ?? null, payload),
            }));
          }
          break;
        }
        case 'team.completed': {
          const payload = parseTeamCompleted(event.data);
          setTeamStateBySession((prev) => {
            const next = applyTeamCompletedPayload(prev[sessionId] ?? null, payload ?? {});
            return next ? { ...prev, [sessionId]: next } : prev;
          });
          setSessionStreaming(sessionId, false);
          invalidateSessionChatHydration(sessionId);
          void syncChatFromServer(sessionId);
          break;
        }
        case 'run.completed':
          if (isLeaderRunTerminal(event.name, event.data)) {
            clearSessionError(sessionId);
            setSessionStreaming(sessionId, false);
            bumpArtifacts();
            void refreshSessions().catch(() => undefined);
            invalidateSessionChatHydration(sessionId);
            void syncChatFromServer(sessionId);
            void syncQueueDepth(sessionId);
            if (pendingQueueFollowUpRef.current[sessionId]) {
              pendingQueueFollowUpRef.current[sessionId] = false;
              void followQueuedRunRef.current(sessionId);
            }
            onRunCompleted?.(sessionId);
          }
          break;
        case 'run.failed':
        case 'run.error': {
          const surfaceMember = readTeamSurfaceMember(event.data);
          if (surfaceMember?.memberId) {
            setTeamStateBySession((prev) => {
              const next = applyMemberRuntimeError(prev[sessionId] ?? null, surfaceMember.memberId);
              return next ? { ...prev, [sessionId]: next } : prev;
            });
          }
          if (!isLeaderRunTerminal(event.name, event.data)) {
            break;
          }
          const errorText = parseRunError(event.data);
          reportSessionError(sessionId, errorText);
          dispatch({
            type: 'append-item',
            sessionId,
            item: {
              id: uid(),
              kind: 'system',
              tone: 'error',
              text: errorText,
            },
          });
          setSessionStreaming(sessionId, false);
          bumpArtifacts();
          void refreshSessions().catch(() => undefined);
          break;
        }
        default:
          break;
      }
    },
    [bumpArtifacts, clearSessionError, dispatch, onApprovalRequired, onArtifactAdded, onRunCompleted, onWriteCompleted, refreshSessions, reportSessionError, resolveExpert, setSessionStreaming, setStreamStage, syncChatFromServer, syncQueueDepth],
  );

  handleSseEventRef.current = handleSseEvent;

  const followQueuedRun = useCallback(
    async (sessionId: string) => {
      if (streamingBySession[sessionId]) {
        return;
      }

      abortBySessionRef.current[sessionId]?.abort();
      const controller = new AbortController();
      abortBySessionRef.current[sessionId] = controller;

      // Leader/assistant bubbles are created lazily on the first message/reasoning delta and
      // closed on each tool call, so no empty placeholder bubble is seeded up front.
      delete assistantIdBySessionRef.current[sessionId];
      delete leaderReasoningIdBySessionRef.current[sessionId];

      clearSessionError(sessionId);
      setSessionStreaming(sessionId, true);

      try {
        await syncChatFromServer(sessionId);
        await resumeEventStream({
          sessionId,
          signal: controller.signal,
          lastEventId: lastEventIdBySessionRef.current[sessionId],
          onEvent: (event) => handleSseEventRef.current(sessionId, event),
          onLastEventId: (id) => {
            lastEventIdBySessionRef.current[sessionId] = id;
          },
        });
      } catch (error) {
        if ((error as Error).name !== 'AbortError') {
          const message = reportSessionError(sessionId, (error as Error).message);
          dispatch({
            type: 'append-item',
            sessionId,
            item: { id: uid(), kind: 'system', tone: 'error', text: message },
          });
        }
      } finally {
        setSessionStreaming(sessionId, false);
        delete assistantIdBySessionRef.current[sessionId];
        delete leaderReasoningIdBySessionRef.current[sessionId];
        bumpArtifacts();
        void refreshSessions().catch(() => undefined);
        void syncQueueDepth(sessionId);
      }
    },
    [
      bumpArtifacts,
      clearSessionError,
      dispatch,
      refreshSessions,
      reportSessionError,
      setSessionStreaming,
      streamingBySession,
      syncChatFromServer,
      syncQueueDepth,
    ],
  );

  followQueuedRunRef.current = followQueuedRun;

  /** Re-attach SSE when revisiting an in-flight TeamAgent session (run_events show team.started, no terminal). */
  const resumeTeamRunIfActive = useCallback(
    async (
      sessionId: string,
      options?: { ignoreStreamingGuard?: boolean },
    ): Promise<boolean> => {
      if (streamingBySession[sessionId] && !options?.ignoreStreamingGuard) {
        return false;
      }
      const expert = resolveExpert?.(sessionId);
      if (expert?.expertType !== 'team') {
        return false;
      }
      try {
        const events = await listSessionRunEvents(sessionId);
        const names = events.map((event) => event.name);
        if (!names.includes('team.started')) {
          return false;
        }
        if (names.includes('team.completed')) {
          return false;
        }

        abortBySessionRef.current[sessionId]?.abort();
        const controller = new AbortController();
        abortBySessionRef.current[sessionId] = controller;
        clearSessionError(sessionId);
        await syncChatFromServer(sessionId);
        setSessionStreaming(sessionId, true);

        const tailSeq = events.length > 0 ? events[events.length - 1]?.seq : undefined;
        const lastEventId =
          lastEventIdBySessionRef.current[sessionId]
          ?? (typeof tailSeq === 'number' ? String(tailSeq) : undefined);

        await resumeEventStream({
          sessionId,
          signal: controller.signal,
          lastEventId,
          onEvent: (event) => handleSseEventRef.current(sessionId, event),
          onLastEventId: (id) => {
            lastEventIdBySessionRef.current[sessionId] = id;
          },
        });
        return true;
      } catch (error) {
        if ((error as Error).name !== 'AbortError') {
          reportSessionError(sessionId, (error as Error).message);
        }
        return false;
      } finally {
        setSessionStreaming(sessionId, false);
        delete assistantIdBySessionRef.current[sessionId];
        delete leaderReasoningIdBySessionRef.current[sessionId];
        bumpArtifacts();
        void refreshSessions().catch(() => undefined);
        void syncChatFromServer(sessionId);
      }
    },
    [
      bumpArtifacts,
      clearSessionError,
      refreshSessions,
      reportSessionError,
      resolveExpert,
      setSessionStreaming,
      streamingBySession,
      syncChatFromServer,
    ],
  );

  /** After REST-based HITL (question/approval/plan), reconnect GET /events/stream for live updates. */
  const resumeRunAfterHitl = useCallback(
    async (sessionId: string): Promise<void> => {
      abortBySessionRef.current[sessionId]?.abort();
      setSessionStreaming(sessionId, false);
      invalidateSessionChatHydration(sessionId);
      const resumed = await resumeTeamRunIfActive(sessionId, { ignoreStreamingGuard: true });
      if (!resumed) {
        await syncChatFromServer(sessionId);
      }
    },
    [resumeTeamRunIfActive, setSessionStreaming, syncChatFromServer],
  );

  const runStream = useCallback(
    async (
      sessionId: string,
      streamFn: (options: {
        sessionId: string;
        signal: AbortSignal;
        onEvent: (event: SseEvent) => void;
        onLastEventId: (id: string) => void;
      }) => Promise<void>,
      setup?: () => void,
    ) => {
      if (streamingBySession[sessionId]) {
        return;
      }

      abortBySessionRef.current[sessionId]?.abort();
      const controller = new AbortController();
      abortBySessionRef.current[sessionId] = controller;

      setup?.();

      clearSessionError(sessionId);

      const expert = resolveExpert?.(sessionId);
      if (expert?.expertType === 'team') {
        const seeded = initialTeamState(expert);
        if (seeded) {
          setTeamStateBySession((prev) => ({ ...prev, [sessionId]: seeded }));
        }
      }

      setSessionStreaming(sessionId, true);
      const streamOptions = {
        sessionId,
        signal: controller.signal,
        onEvent: (event: SseEvent) => handleSseEvent(sessionId, event),
        onLastEventId: (id: string) => {
          lastEventIdBySessionRef.current[sessionId] = id;
        },
      };

      try {
        await streamFn(streamOptions);
      } catch (error) {
        if ((error as Error).name === 'AbortError') {
          dispatch({
            type: 'append-item',
            sessionId,
            item: { id: uid(), kind: 'system', tone: 'info', text: '已停止生成' },
          });
        } else {
          const rawMessage = (error as Error).message || '连接中断';
          if (isClientHttpError(rawMessage)) {
            const message = reportSessionError(sessionId, rawMessage);
            dispatch({
              type: 'append-item',
              sessionId,
              item: { id: uid(), kind: 'system', tone: 'error', text: message },
            });
          } else {
            const lastEventId = lastEventIdBySessionRef.current[sessionId];
            try {
              await resumeEventStream({ ...streamOptions, lastEventId });
            } catch (resumeError) {
              if ((resumeError as Error).name !== 'AbortError') {
                const message = reportSessionError(
                  sessionId,
                  (resumeError as Error).message || rawMessage,
                );
                dispatch({
                  type: 'append-item',
                  sessionId,
                  item: { id: uid(), kind: 'system', tone: 'error', text: message },
                });
              }
            }
          }
        }
      } finally {
        setSessionStreaming(sessionId, false);
        delete assistantIdBySessionRef.current[sessionId];
        delete leaderReasoningIdBySessionRef.current[sessionId];
        delete lastEventIdBySessionRef.current[sessionId];
        bumpArtifacts();
        void refreshSessions().catch(() => undefined);
      }
    },
    [bumpArtifacts, clearSessionError, dispatch, handleSseEvent, refreshSessions, reportSessionError, resolveExpert, setSessionStreaming, streamingBySession],
  );

  const runPrompt = useCallback(
    async (
      sessionId: string,
      message: string,
      mentions?: MentionRef[],
      attachments?: import('../types/events').UserAttachment[],
    ) => {
      if (streamingBySession[sessionId]) {
        dispatch({
          type: 'append-item',
          sessionId,
          item: { id: uid(), kind: 'user', text: message, mentions, attachments },
        });
        try {
          const outcome = await streamPrompt({
            sessionId,
            message,
            mentions,
            attachments,
            onEvent: () => undefined,
          });
          if (outcome !== 'streamed') {
            setQueueDepthBySession((prev) => ({
              ...prev,
              [sessionId]: outcome.queueDepth,
            }));
            pendingQueueFollowUpRef.current[sessionId] = true;
            dispatch({
              type: 'append-item',
              sessionId,
              item: {
                id: uid(),
                kind: 'system',
                tone: 'info',
                text: `已加入队列（第 ${outcome.queuePosition} 位）`,
              },
            });
          }
        } catch (error) {
          const messageText = reportSessionError(sessionId, (error as Error).message);
          dispatch({
            type: 'append-item',
            sessionId,
            item: { id: uid(), kind: 'system', tone: 'error', text: messageText },
          });
        }
        return;
      }

      await runStream(
        sessionId,
        async (options) => {
          const outcome = await streamPrompt({ ...options, message, mentions, attachments });
          if (outcome !== 'streamed') {
            throw new Error('Unexpected queued prompt while session idle');
          }
        },
        () => {
          dispatch({
            type: 'append-item',
            sessionId,
            item: { id: uid(), kind: 'user', text: message, mentions, attachments },
          });
          delete assistantIdBySessionRef.current[sessionId];
          delete leaderReasoningIdBySessionRef.current[sessionId];
        },
      );
    },
    [dispatch, reportSessionError, runStream, streamingBySession],
  );

  const editMessage = useCallback(
    async (sessionId: string, fromSeq: number, message: string) => {
      await runStream(
        sessionId,
        (options) => streamEditMessage(sessionId, fromSeq, message, options),
        () => {
          dispatch({ type: 'truncate-at-user', sessionId, userSeq: fromSeq, includeUser: true });
          dispatch({
            type: 'append-item',
            sessionId,
            item: { id: uid(), kind: 'user', text: message },
          });
          delete assistantIdBySessionRef.current[sessionId];
          delete leaderReasoningIdBySessionRef.current[sessionId];
        },
      );
    },
    [dispatch, runStream],
  );

  const retryMessage = useCallback(
    async (sessionId: string, fromSeq: number) => {
      await runStream(sessionId, (options) => streamRetry(sessionId, options), () => {
        dispatch({ type: 'truncate-at-user', sessionId, userSeq: fromSeq, includeUser: false });
        delete assistantIdBySessionRef.current[sessionId];
        delete leaderReasoningIdBySessionRef.current[sessionId];
      });
    },
    [dispatch, runStream],
  );

  const stopPrompt = useCallback(
    (sessionId: string) => {
      abortBySessionRef.current[sessionId]?.abort();
      setSessionStreaming(sessionId, false);
    },
    [setSessionStreaming],
  );

  /** 只读审计：从 run_events 重放重建团队协作时间线（打开历史会话时调用）。不覆盖实时态。 */
  const hydrateTeamFromEvents = useCallback(
    (
      sessionId: string,
      events: { name: string; data: unknown; seq?: number }[],
      options?: { snapshot?: TeamSnapshot | null; expertTeamRuntime?: string | null },
    ) => {
      let team: TeamState | null = null;
      for (const ev of events) {
        switch (ev.name) {
          case 'team.started': {
            const p = parseTeamStarted(ev.data);
            if (p) team = applyTeamStarted(team, p);
            break;
          }
          case 'tool.start': {
            const payload = parseToolStart(ev.data);
            const delegationMember = isDelegationTeamTool(payload.toolName) ? readDelegationToolMember(ev.data) : null;
            if (isTeamSurfacePayload(ev.data) && !isDelegationTeamTool(payload.toolName)) {
              const member = readTeamSurfaceMember(ev.data);
              if (member?.memberId) {
                team = applyMemberToolStart(team, member.memberId);
              }
            } else if (delegationMember?.memberId) {
              team = applyMemberToolStart(team, delegationMember.memberId);
            }
            break;
          }
          case 'team.build.completed': {
            const p = parseTeamBuildCompleted(ev.data);
            if (p) team = applyTeamBuildCompleted(team, p);
            break;
          }
          case 'team.member.started':
          case 'team.member.reawakened':
          case 'team.member.completed':
          case 'team.member.paused':
          case 'team.member.failed': {
            const p = parseTeamMemberEvent(ev.data);
            if (p) {
              const kind =
                ev.name === 'team.member.started'
                  ? 'started'
                  : ev.name === 'team.member.reawakened'
                    ? 'reawakened'
                    : ev.name === 'team.member.completed'
                      ? 'completed'
                      : ev.name === 'team.member.paused'
                        ? 'paused'
                        : 'failed';
              team = applyMemberEvent(team, kind, p, ev.seq);
            }
            break;
          }
          case 'team.lead.synthesizing':
            team = applyLeadSynthesizing(team) ?? team;
            break;
          case 'team.iteration.started': {
            const p = parseTeamIteration(ev.data);
            if (p) team = applyIterationStarted(team, p) ?? team;
            break;
          }
          case 'team.state.progress': {
            const p = parseTeamStateProgress(ev.data);
            if (p) team = applyStateProgress(team, p) ?? team;
            break;
          }
          case 'team.verify.started': {
            const p = parseTeamVerify(ev.data);
            if (p) team = applyVerifyStarted(team, p) ?? team;
            break;
          }
          case 'team.verify.accepted': {
            const p = parseTeamVerify(ev.data);
            if (p) team = applyVerifyAccepted(team, p) ?? team;
            break;
          }
          case 'team.verify.rejected': {
            const p = parseTeamVerify(ev.data);
            if (p) team = applyVerifyRejected(team, p) ?? team;
            break;
          }
          case 'team.memory': {
            const p = parseTeamMemory(ev.data);
            if (p) team = applyTeamMemory(team, p);
            break;
          }
          case 'team.bus.published': {
            const p = parseTeamBusPublished(ev.data);
            if (p) team = applyBusPublished(team, p);
            break;
          }
          case 'team.bus.subscribed': {
            const p = parseTeamBusSubscribed(ev.data);
            if (p) team = applyBusSubscribed(team, p);
            break;
          }
          case 'team.completed': {
            const p = parseTeamCompleted(ev.data);
            team = applyTeamCompletedPayload(team, p ?? {}) ?? team;
            break;
          }
          case 'run.failed':
          case 'run.error': {
            const member = readTeamSurfaceMember(ev.data);
            if (member?.memberId) {
              team = applyMemberRuntimeError(team, member.memberId);
            }
            break;
          }
          case 'usage.delta': {
            const p = parseUsageDelta(ev.data);
            if (p?.memberId) {
              team =
                applyMemberUsage(team, p.memberId, p.deltaPromptTokens ?? 0, p.deltaCompletionTokens ?? 0) ?? team;
            }
            break;
          }
          default:
            break;
        }
      }
      if (!team) {
        return;
      }
      const expertTeamRuntime = options?.expertTeamRuntime ?? null;
      if (isDelegationTeamState(team, expertTeamRuntime)) {
        team = ensureDelegationVisualization(team, expertTeamRuntime);
      }
      let hydrated = team;
      if (options?.snapshot) {
        hydrated = mergeTeamSnapshotStatuses(hydrated, options.snapshot) ?? hydrated;
      }
      setTeamStateBySession((prev) => ({ ...prev, [sessionId]: hydrated }));
    },
    [],
  );

  const applyTeamSnapshot = useCallback((sessionId: string, snapshot: TeamSnapshot) => {
    setTeamStateBySession((prev) => {
      const merged = mergeTeamSnapshotStatuses(prev[sessionId] ?? null, snapshot);
      if (!merged) {
        return prev;
      }
      return { ...prev, [sessionId]: merged };
    });
  }, []);

  const isStreaming = useCallback(
    (sessionId: string | null) => (sessionId ? (streamingBySession[sessionId] ?? false) : false),
    [streamingBySession],
  );

  return {
    streamingBySession,
    streamStageBySession,
    sessionErrorBySession,
    queueDepthBySession,
    teamStateBySession,
    runPrompt,
    editMessage,
    retryMessage,
    stopPrompt,
    clearQueue,
    clearSessionError,
    isStreaming,
    hydrateTeamFromEvents,
    resumeTeamRunIfActive,
    resumeRunAfterHitl,
    applyTeamSnapshot,
    syncChatFromServer,
    refreshMemberSurfaceFromEvents,
  };
}

export type { ChatItem };

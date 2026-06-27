import type { ChatItem } from '../types/events';
import { chatItemOrderKey, sortChatItemsByOrder } from './chatItemOrder';
import { mergeChatWithLeaderDeltas } from './leaderMessageHydrate';
import { enrichDelegationToolItems } from './delegationInput';
import { isTeamSurfacePayload } from './eventPayload';
import { mergeStructuralQuestions, questionItemsFromRunEvents } from './questionHydrate';
import { expertSwitchItemsFromRunEvents, mergeStructuralExpertSwitches } from './expertSwitchHydrate';
import { memberSurfaceItemsFromRunEvents } from './memberSurfaceHydrate';
import { reasoningItemsFromRunEvents, type RunEventRow } from './reasoningHydrate';
import { teamToolItemsFromRunEvents, teamBypassSystemItemsFromRunEvents } from './teamRunEventHydrate';
import {
  enrichLeaderWorkspaceTools,
  filterMirroredLeaderWorkspaceToolsFromMessages,
  leaderWorkspaceToolItemsFromRunEvents,
} from './leaderToolHydrate';

/**
 * the reference JSONL is one ordered stream:
 *   message → reasoning → function_call_result (renderer: team-created / team-member-spawned / …)
 *
 * WorkMate maps the same shapes from `run_events` (unified seq):
 *   message.delta → assistant
 *   reasoning.delta → reasoning
 *   tool.start/end + team.* → tool chip
 *
 * DB `session_messages` only supplies structural turns (user / HITL / plan), plus leader
 * workspace tools as fallback when not yet in the event log.
 */
function isOrchestrationTool(toolName: string): boolean {
  const normalized = toolName.toLowerCase();
  return normalized.includes('build_team')
    || (normalized.includes('send_message') && normalized.includes('team'));
}

function isStructuralItem(item: ChatItem): boolean {
  return item.kind === 'user'
    || item.kind === 'question'
    || item.kind === 'approval'
    || item.kind === 'plan'
    || item.kind === 'system'
    || item.kind === 'artifact-cta'
    || item.kind === 'expert-switched';
}

/** Leader workspace tools persisted before run_events catch up (Read/Write/Bash…). */
function leaderWorkspaceToolsFromMessages(messages: ChatItem[]): ChatItem[] {
  return messages.filter(
    (item): item is Extract<ChatItem, { kind: 'tool' }> =>
      item.kind === 'tool'
      && !item.memberId
      && !isOrchestrationTool(item.toolName),
  );
}

/** Legacy assistant rows when events lack per-turn messageId tagging. */
function leaderAssistantsFromMessages(messages: ChatItem[]): ChatItem[] {
  return messages.filter(
    (item): item is Extract<ChatItem, { kind: 'assistant' }> =>
      item.kind === 'assistant' && !item.memberId,
  );
}

function dedupeTimeline(items: ChatItem[]): ChatItem[] {
  const byId = new Map<string, ChatItem>();
  const toolCallIds = new Set<string>();
  for (const item of items) {
    if (item.kind === 'tool' && item.toolCallId) {
      if (toolCallIds.has(item.toolCallId)) {
        continue;
      }
      toolCallIds.add(item.toolCallId);
    }
    if (!byId.has(item.id)) {
      byId.set(item.id, item);
    }
  }
  return [...byId.values()];
}

function interjectionBreakpoints(messages: ChatItem[]): ChatItem[] {
  return messages.filter(
    (item) => item.kind === 'question' || item.kind === 'approval',
  );
}

/** Prefer full delegation args from session_messages over redacted run_event payloads. */
function enrichDelegationTools(eventItems: ChatItem[], messages: ChatItem[]): ChatItem[] {
  return enrichDelegationToolItems(eventItems, messages);
}

function eventTimeline(rows: RunEventRow[], messages: ChatItem[]): ChatItem[] {
  const reasoning = reasoningItemsFromRunEvents(rows);
  const leader = mergeChatWithLeaderDeltas(interjectionBreakpoints(messages), rows);
  const workspaceTools = enrichLeaderWorkspaceTools(leaderWorkspaceToolItemsFromRunEvents(rows), messages);
  const delegation = enrichDelegationTools(teamToolItemsFromRunEvents(rows), messages);
  const bypass = teamBypassSystemItemsFromRunEvents(rows);
  const members = memberSurfaceItemsFromRunEvents(rows);
  return [...reasoning, ...leader, ...workspaceTools, ...delegation, ...bypass, ...members];
}

function withoutEventDuplicates(persisted: ChatItem[], fromEvents: ChatItem[], events: RunEventRow[]): ChatItem[] {
  const eventIds = new Set(fromEvents.map((item) => item.id));
  const eventToolCallIds = new Set(
    fromEvents
      .filter((item): item is Extract<ChatItem, { kind: 'tool' }> => item.kind === 'tool')
      .map((item) => item.toolCallId)
      .filter((id): id is string => Boolean(id)),
  );
  const backedLeaderMessageIds = new Set<string>();
  for (const event of events) {
    if (event.name !== 'message.delta' || isTeamSurfacePayload(event.data)) {
      continue;
    }
    const messageId = typeof event.data?.messageId === 'string' ? event.data.messageId : '';
    if (messageId) {
      backedLeaderMessageIds.add(messageId);
    }
  }
  return persisted.filter((item) => {
    if (eventIds.has(item.id)) {
      return false;
    }
    if (item.kind === 'tool' && item.toolCallId && eventToolCallIds.has(item.toolCallId)) {
      return false;
    }
    if (item.kind === 'assistant' && !item.memberId && backedLeaderMessageIds.has(item.id)) {
      return false;
    }
    return true;
  });
}

/** Project the main chat timeline — one the reference workbench-shaped ordered stream. */
export function projectChatTimeline(messages: ChatItem[], events: RunEventRow[]): ChatItem[] {
  if (events.length === 0) {
    return sortChatItemsByOrder(messages);
  }

  const structural = mergeStructuralExpertSwitches(
    mergeStructuralQuestions(
      messages.filter(isStructuralItem),
      questionItemsFromRunEvents(events, messages),
    ),
    expertSwitchItemsFromRunEvents(events, messages),
  );
  const fromEvents = eventTimeline(events, messages);
  const fallback = withoutEventDuplicates(
    filterMirroredLeaderWorkspaceToolsFromMessages(
      [...leaderAssistantsFromMessages(messages), ...leaderWorkspaceToolsFromMessages(messages)],
      events,
    ),
    fromEvents,
    events,
  );

  return sortChatItemsByOrder(dedupeTimeline([...structural, ...fromEvents, ...fallback]));
}

export { chatItemOrderKey };

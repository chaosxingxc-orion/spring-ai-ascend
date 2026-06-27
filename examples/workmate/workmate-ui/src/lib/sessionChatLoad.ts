import {
  listSessionMessages,
  listSessionRunEvents,
  invalidateRunEventsCache,
  type RecordedEventLogEntry,
} from '../api/client';
import type { ChatItem } from '../types/events';
import { enrichMemberDelegationItems } from './delegationInput';
import { parseChatItems } from './eventPayload';
import type { RunEventRow } from './reasoningHydrate';
import { projectChatTimeline } from './timelineProjector';

function toRunEventRows(events: RecordedEventLogEntry[]): RunEventRow[] {
  return events.map((entry) => ({
    seq: typeof entry.seq === 'number' ? entry.seq : 0,
    name: String(entry.name ?? ''),
    data: (entry.data ?? {}) as Record<string, unknown>,
  }));
}

function buildTimeline(messages: unknown[], events: RunEventRow[]): ChatItem[] {
  const parsed = parseChatItems(messages);
  const timeline = projectChatTimeline(parsed, events);
  return enrichMemberDelegationItems(timeline, messages);
}

export interface SessionChatHydration {
  structuralItems: ChatItem[];
  fullItems: ChatItem[];
  events: RecordedEventLogEntry[];
}

const hydrationInflight = new Map<string, Promise<SessionChatHydration>>();

export function invalidateSessionChatHydration(sessionId?: string) {
  if (sessionId) {
    hydrationInflight.delete(sessionId);
    invalidateRunEventsCache(sessionId);
    return;
  }
  hydrationInflight.clear();
  invalidateRunEventsCache();
}

/**
 * Lazy session open:
 * 1) structural messages only (fast first paint)
 * 2) full run_events history (heavy) then merge into timeline
 *
 * Concurrent callers (chat hydrate + team hydrate) share one in-flight promise.
 */
export function hydrateSessionChat(
  sessionId: string,
  options?: {
    fresh?: boolean;
    onStructural?: (items: ChatItem[]) => void;
  },
): Promise<SessionChatHydration> {
  if (options?.fresh) {
    invalidateSessionChatHydration(sessionId);
  }
  const existing = hydrationInflight.get(sessionId);
  if (existing) {
    return existing;
  }

  const promise = (async (): Promise<SessionChatHydration> => {
    const messages = await listSessionMessages(sessionId);
    const structuralItems = buildTimeline(messages, []);
    options?.onStructural?.(structuralItems);

    const events = await listSessionRunEvents(sessionId).catch(() => [] as RecordedEventLogEntry[]);
    const fullItems = buildTimeline(messages, toRunEventRows(events));
    return { structuralItems, fullItems, events };
  })();

  hydrationInflight.set(sessionId, promise);
  promise.finally(() => {
    if (hydrationInflight.get(sessionId) === promise) {
      hydrationInflight.delete(sessionId);
    }
  });
  return promise;
}

/** Full reload (e.g. after sidecar import or explicit sync). */
export async function loadSessionChatItems(
  sessionId: string,
  options?: { fresh?: boolean },
): Promise<ChatItem[]> {
  const { fullItems } = await hydrateSessionChat(sessionId, options);
  return fullItems;
}

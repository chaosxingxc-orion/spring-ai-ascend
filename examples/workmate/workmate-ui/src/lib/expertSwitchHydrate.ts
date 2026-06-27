import type { ChatItem } from '../types/events';
import { parseExpertSwitched } from './eventPayload';
import type { RunEventRow } from './reasoningHydrate';

type ExpertSwitchedItem = Extract<ChatItem, { kind: 'expert-switched' }>;

function switchKey(item: Pick<ExpertSwitchedItem, 'toExpertId' | 'newGeneration'>): string {
  return `${item.toExpertId}:${item.newGeneration ?? 0}`;
}

/**
 * Rebuild expert-switched cards from run_events when session_messages missed a persist.
 */
export function expertSwitchItemsFromRunEvents(
  events: RunEventRow[],
  persisted: ChatItem[],
): ExpertSwitchedItem[] {
  const persistedKeys = new Set(
    persisted
      .filter((item): item is ExpertSwitchedItem => item.kind === 'expert-switched')
      .map((item) => switchKey(item)),
  );
  const hydrated = new Map<string, ExpertSwitchedItem>();
  for (const event of events) {
    if (event.name !== 'expert.switched' || !event.data) {
      continue;
    }
    const item = parseExpertSwitched(event.data);
    if (!item) {
      continue;
    }
    const withSeq = { ...item, seq: event.seq ?? item.seq };
    if (persistedKeys.has(switchKey(withSeq))) {
      continue;
    }
    hydrated.set(withSeq.id, withSeq);
  }
  return [...hydrated.values()];
}

export function mergeStructuralExpertSwitches(
  structural: ChatItem[],
  hydrated: ExpertSwitchedItem[],
): ChatItem[] {
  if (hydrated.length === 0) {
    return structural;
  }
  const ids = new Set(structural.filter((item) => item.kind === 'expert-switched').map((item) => item.id));
  const extras = hydrated.filter((item) => !ids.has(item.id));
  return extras.length === 0 ? structural : [...structural, ...extras];
}

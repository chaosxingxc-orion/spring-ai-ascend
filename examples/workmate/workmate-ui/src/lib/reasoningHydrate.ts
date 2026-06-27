import type { ChatItem } from '../types/events';
import { insertChatItemByOrder } from './chatItemOrder';
import { isTeamSurfacePayload, readRedactableText } from './eventPayload';

export interface RunEventRow {
  seq: number;
  name: string;
  data?: Record<string, unknown>;
}

function readString(value: unknown): string {
  return readRedactableText(value) ?? '';
}

/** Build reasoning ChatItems from run_events (main conversation only). */
export function reasoningItemsFromRunEvents(events: RunEventRow[]): ChatItem[] {
  const items: ChatItem[] = [];
  let buffer = '';
  let itemId = 'reasoning-0';
  let itemSeq = 0;

  const flush = () => {
    const text = buffer.trim();
    if (text) {
      items.push({ id: itemId, kind: 'reasoning', text: buffer, seq: itemSeq });
    }
    buffer = '';
  };

  for (const event of events) {
    if (isTeamSurfacePayload(event.data)) {
      continue;
    }
    if (event.name === 'reasoning.delta' || event.name === 'reasoning') {
      buffer += readString(event.data?.text);
      itemId = `reasoning-${event.seq}`;
      itemSeq = event.seq;
    } else if (buffer) {
      flush();
    }
  }
  flush();
  return items;
}

function hasReasoningId(items: ChatItem[], id: string): boolean {
  return items.some((item) => item.kind === 'reasoning' && item.id === id);
}

/** Merge reasoning blocks into persisted messages on session reload. */
export function mergeChatWithReasoningEvents(
  items: ChatItem[],
  events: RunEventRow[],
): ChatItem[] {
  const reasoningSegments = reasoningItemsFromRunEvents(events);
  if (reasoningSegments.length === 0) {
    return items;
  }

  let merged = [...items];
  for (const reasoning of reasoningSegments) {
    if (hasReasoningId(merged, reasoning.id)) {
      continue;
    }
    merged = insertChatItemByOrder(merged, reasoning);
  }
  return merged;
}

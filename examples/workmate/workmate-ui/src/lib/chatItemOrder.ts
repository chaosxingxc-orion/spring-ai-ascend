import type { ChatItem } from '../types/events';

/** Monotonic ordering key shared by hydrate paths and live SSE assembly. */
export function chatItemOrderKey(item: ChatItem): number {
  if (item.kind === 'tool') {
    return item.seq ?? item.startedAt ?? 0;
  }
  if (
    item.kind === 'user'
    || item.kind === 'assistant'
    || item.kind === 'reasoning'
    || item.kind === 'question'
    || item.kind === 'approval'
    || item.kind === 'system'
    || item.kind === 'member-delegation'
    || item.kind === 'expert-switched'
    || item.kind === 'artifact-cta'
  ) {
    return item.seq ?? 0;
  }
  if (item.kind === 'tool-group') {
    const first = item.tools[0];
    return first ? chatItemOrderKey(first) : 0;
  }
  return 0;
}

/** Parse SSE `id` field (run-event seq) when present. */
export function readSseEventSeq(event: { id?: string }): number | undefined {
  if (!event.id) {
    return undefined;
  }
  const parsed = Number.parseInt(event.id, 10);
  return Number.isFinite(parsed) ? parsed : undefined;
}

/** Insert `item` at the first position whose order key exceeds `item`'s key. */
export function insertChatItemByOrder(items: ChatItem[], item: ChatItem): ChatItem[] {
  const target = chatItemOrderKey(item);
  if (target <= 0) {
    return [...items, item];
  }
  let insertAt = items.length;
  for (let index = 0; index < items.length; index += 1) {
    const seq = chatItemOrderKey(items[index]);
    if (seq > target) {
      insertAt = index;
      break;
    }
  }
  const next = [...items];
  next.splice(insertAt, 0, item);
  return next;
}

/** Stable sort by order key; equal keys keep relative order. */
export function sortChatItemsByOrder(items: ChatItem[]): ChatItem[] {
  return items
    .map((item, index) => ({ item, index }))
    .sort((a, b) => {
      const delta = chatItemOrderKey(a.item) - chatItemOrderKey(b.item);
      return delta !== 0 ? delta : a.index - b.index;
    })
    .map((entry) => entry.item);
}

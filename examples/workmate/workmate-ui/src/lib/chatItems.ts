import type { ChatItem } from '../types/events';

/** Last user message seq in the transcript (for retry / edit). */
export function lastUserSeq(items: ChatItem[]): number | undefined {
  for (let index = items.length - 1; index >= 0; index -= 1) {
    const item = items[index];
    if (item.kind === 'user' && item.seq != null) {
      return item.seq;
    }
  }
  return undefined;
}

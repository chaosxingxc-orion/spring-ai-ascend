import { describe, expect, it } from 'vitest';
import type { ChatItem } from '../types/events';
import { mergeChatWithReasoningEvents, reasoningItemsFromRunEvents } from './reasoningHydrate';

describe('reasoningHydrate', () => {
  it('builds reasoning items from delta stream', () => {
    const items = reasoningItemsFromRunEvents([
      { seq: 1, name: 'reasoning.delta', data: { text: 'step one ' } },
      { seq: 2, name: 'reasoning.delta', data: { text: 'step two' } },
      { seq: 3, name: 'message.delta', data: { text: 'answer' } },
    ]);
    expect(items).toEqual([
      { id: 'reasoning-2', kind: 'reasoning', text: 'step one step two', seq: 2 },
    ]);
  });

  it('skips team-surface reasoning', () => {
    const items = reasoningItemsFromRunEvents([
      {
        seq: 1,
        name: 'reasoning.delta',
        data: { text: 'hidden', surface: 'team', memberId: 'm1', parentRunId: 'p1' },
      },
    ]);
    expect(items).toHaveLength(0);
  });

  it('merges reasoning before assistant on reload', () => {
    const messages: ChatItem[] = [
      { id: 'u1', kind: 'user', text: 'hi', seq: 1 },
      { id: 'a1', kind: 'assistant', text: 'hello', seq: 10 },
    ];
    const merged = mergeChatWithReasoningEvents(messages, [
      { seq: 5, name: 'reasoning.delta', data: { text: 'thinking…' } },
      { seq: 8, name: 'message.delta', data: { text: 'hello' } },
    ]);
    expect(merged.map((item) => item.kind)).toEqual(['user', 'reasoning', 'assistant']);
    expect(merged[1].kind === 'reasoning' && merged[1].text).toBe('thinking…');
  });

  it('does not duplicate reasoning ids', () => {
    const messages: ChatItem[] = [
      { id: 'reasoning-2', kind: 'reasoning', text: 'cached', seq: 2 },
      { id: 'a1', kind: 'assistant', text: 'hello' },
    ];
    const merged = mergeChatWithReasoningEvents(messages, [
      { seq: 2, name: 'reasoning.delta', data: { text: 'thinking…' } },
    ]);
    expect(merged.filter((item) => item.kind === 'reasoning')).toHaveLength(1);
  });
});

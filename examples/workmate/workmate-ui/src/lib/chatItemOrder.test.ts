import { describe, expect, it } from 'vitest';
import type { ChatItem } from '../types/events';
import { insertChatItemByOrder, sortChatItemsByOrder } from './chatItemOrder';
import { mergeChatWithReasoningEvents } from './reasoningHydrate';

describe('chatItemOrder', () => {
  it('sorts mixed message and tool items by seq', () => {
    const items: ChatItem[] = [
      { id: 'a2', kind: 'assistant', text: 'round 2', seq: 30 },
      { id: 'u1', kind: 'user', text: 'hi', seq: 1 },
      { id: 't1', kind: 'tool', toolName: 'team.build_team', status: 'success', seq: 20, startedAt: 20 },
      { id: 'a1', kind: 'assistant', text: 'round 1', seq: 10 },
    ];
    expect(sortChatItemsByOrder(items).map((item) => item.id)).toEqual(['u1', 'a1', 't1', 'a2']);
  });

  it('inserts reasoning at chronological position in multi-round session', () => {
    const messages: ChatItem[] = [
      { id: 'u1', kind: 'user', text: 'hi', seq: 1 },
      { id: 'a1', kind: 'assistant', text: 'first', seq: 10 },
      { id: 't1', kind: 'tool', toolName: 'team.build_team', status: 'success', seq: 20, startedAt: 20 },
      { id: 'a2', kind: 'assistant', text: 'second', seq: 40 },
    ];
    const merged = mergeChatWithReasoningEvents(messages, [
      { seq: 5, name: 'reasoning.delta', data: { text: 'think-1' } },
      { seq: 8, name: 'message.delta', data: { text: 'first' } },
      { seq: 35, name: 'reasoning.delta', data: { text: 'think-2' } },
      { seq: 38, name: 'message.delta', data: { text: 'second' } },
    ]);
    expect(merged.map((item) => `${item.kind}:${item.seq}`)).toEqual([
      'user:1',
      'reasoning:5',
      'assistant:10',
      'tool:20',
      'reasoning:35',
      'assistant:40',
    ]);
  });

  it('insertChatItemByOrder keeps stable tail append without seq', () => {
    const items: ChatItem[] = [{ id: 'u1', kind: 'user', text: 'hi', seq: 1 }];
    const next = insertChatItemByOrder(items, { id: 'live', kind: 'assistant', text: '…' });
    expect(next.map((item) => item.id)).toEqual(['u1', 'live']);
  });
});

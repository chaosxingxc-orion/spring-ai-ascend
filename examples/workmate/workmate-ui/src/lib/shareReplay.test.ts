import { describe, expect, it } from 'vitest';
import { buildUserTurns, replayStatusLabel, sliceItemsForReplay } from './shareReplay';
import type { ChatItem } from '../types/events';

const items: ChatItem[] = [
  { kind: 'user', id: 'u1', seq: 1, text: 'first' },
  { kind: 'assistant', id: 'a1', seq: 2, text: 'reply one' },
  { kind: 'user', id: 'u2', seq: 3, text: 'second' },
  { kind: 'assistant', id: 'a2', seq: 4, text: 'reply two' },
];

describe('shareReplay helpers', () => {
  it('buildUserTurns groups assistant replies with each user turn', () => {
    expect(buildUserTurns(items)).toEqual([
      { userIndex: 0, endIndex: 1 },
      { userIndex: 2, endIndex: 3 },
    ]);
  });

  it('sliceItemsForReplay caps visible messages', () => {
    expect(sliceItemsForReplay(items, 1)).toHaveLength(2);
    expect(sliceItemsForReplay(items, 99)).toHaveLength(4);
    expect(sliceItemsForReplay(items, -1)).toHaveLength(0);
  });

  it('replayStatusLabel reflects playback state', () => {
    expect(replayStatusLabel(true, 0, 4)).toBe('回放中 · 1/4');
    expect(replayStatusLabel(false, 3, 4)).toBe('已完成');
  });
});

import { describe, expect, it } from 'vitest';
import {
  chatItemsToMemberTrace,
  filterMemberChatItems,
  isMemberLifecycleStatusText,
  summarizeMemberChatItems,
} from './memberChatProjection';
import type { ChatItem } from '../types/events';

describe('memberChatProjection', () => {
  it('filters member-scoped items from the main timeline', () => {
    const items: ChatItem[] = [
      { id: 'u1', kind: 'user', text: 'hi', seq: 1 },
      { id: 'l1', kind: 'assistant', text: 'leader', seq: 2 },
      { id: 'm1', kind: 'assistant', text: 'member work', seq: 5, memberId: 'writer' },
      { id: 't1', kind: 'tool', toolName: 'read', status: 'success', memberId: 'writer', seq: 6 },
    ];
    const filtered = filterMemberChatItems(items, { id: 'writer', name: '写手' });
    expect(filtered.map((item) => item.id)).toEqual(['m1', 't1']);
  });

  it('projects chat items to member trace rows', () => {
    const trace = chatItemsToMemberTrace(
      [
        { id: 'm1', kind: 'assistant', text: 'hello', seq: 10, memberId: 'writer' },
        {
          id: 'tc1',
          kind: 'tool',
          toolName: 'workmate_read',
          toolCallId: 'tc1',
          status: 'success',
          seq: 11,
          memberId: 'writer',
        },
      ],
      'writer',
    );
    expect(trace).toHaveLength(2);
    expect(trace[0]?.kind).toBe('assistant');
    expect(trace[1]?.kind).toBe('tool');
  });

  it('detects lifecycle status copy', () => {
    expect(isMemberLifecycleStatusText('已暂停，等待下一轮…')).toBe(true);
    expect(isMemberLifecycleStatusText('Now thinking…')).toBe(false);
  });

  it('summarizes member tools from chat items', () => {
    const summary = summarizeMemberChatItems(
      [
        { id: 'm1', kind: 'assistant', text: 'done', seq: 10, memberId: 'writer' },
        {
          id: 'tc1',
          kind: 'tool',
          toolName: 'workmate_read',
          status: 'success',
          seq: 11,
          memberId: 'writer',
        },
        {
          id: 'life',
          kind: 'reasoning',
          text: '任务完成',
          seq: 12,
          memberId: 'writer',
        },
      ],
      'writer',
    );
    expect(summary.toolCalls).toBe(1);
    expect(summary.lastCompletedSeq).toBe(12);
    expect(summary.unscheduled).toBe(false);
  });
});

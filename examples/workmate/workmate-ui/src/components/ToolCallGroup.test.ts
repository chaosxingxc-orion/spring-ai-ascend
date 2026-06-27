import { describe, expect, it } from 'vitest';
import type { ChatItem, ToolChatItem } from '../types/events';
import { groupToolItems } from './ToolCallGroup';

function tool(id: string, memberId?: string): ToolChatItem {
  return {
    id,
    kind: 'tool',
    toolName: 'workmate_bash',
    status: 'success',
    startedAt: Number(id.replace(/\D/g, '')) || 0,
    memberId,
  };
}

describe('groupToolItems', () => {
  it('groups consecutive leader (non-member) tools', () => {
    const grouped = groupToolItems([tool('t1'), tool('t2'), tool('t3')]);
    expect(grouped).toHaveLength(1);
    expect(grouped[0].kind).toBe('tool-group');
  });

  it('groups consecutive member tools but lets assistant output break the run', () => {
    const items: ChatItem[] = [
      { id: 'a1', kind: 'assistant', text: 'step 1', memberId: 'topic-researcher' },
      tool('t185', 'topic-researcher'),
      tool('t188', 'topic-researcher'),
      { id: 'a2', kind: 'assistant', text: 'step 2', memberId: 'topic-researcher' },
      tool('t190', 'topic-researcher'),
    ];
    const grouped = groupToolItems(items);
    expect(grouped.map((item) => item.kind)).toEqual([
      'assistant',
      'tool-group',
      'assistant',
      'tool',
    ]);
  });

  it('does not merge tools from different scopes (member vs leader)', () => {
    const items: ChatItem[] = [
      tool('t1'),
      tool('t2', 'topic-researcher'),
      tool('t3', 'topic-researcher'),
      tool('t4'),
    ];
    const grouped = groupToolItems(items);
    expect(grouped.map((item) => item.kind)).toEqual(['tool', 'tool-group', 'tool']);
  });
});

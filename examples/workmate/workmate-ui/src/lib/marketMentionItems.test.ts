import { describe, expect, it } from 'vitest';
import { connectorsToMentionItems, skillsToMentionItems } from './marketMentionItems';

describe('marketMentionItems', () => {
  it('maps skills to mention menu rows', () => {
    expect(
      skillsToMentionItems([
        { id: 'notes', name: '笔记', description: '', category: 'writing', installed: false },
      ]),
    ).toEqual([{ type: 'skill', id: 'notes', label: '笔记', hint: 'writing' }]);
  });

  it('maps connectors to mention menu rows', () => {
    expect(
      connectorsToMentionItems([
        {
          id: 'qieman',
          name: '盈米',
          description: '',
          status: 'connected',
          toolCount: 3,
        },
      ]),
    ).toEqual([{ type: 'connector', id: 'qieman', label: '盈米', hint: 'connected' }]);
  });
});

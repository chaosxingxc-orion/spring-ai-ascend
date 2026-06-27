import { describe, expect, it } from 'vitest';
import { findSearchHits } from './sessionSearch';
import type { ChatItem } from '../types/events';

const items: ChatItem[] = [
  { id: 'u1', kind: 'user', text: '分析销售数据' },
  { id: 'a1', kind: 'assistant', text: '正在读取 CSV 文件' },
  { id: 't1', kind: 'tool', toolName: 'read_file', status: 'success' },
];

describe('findSearchHits', () => {
  it('matches user and assistant text', () => {
    const hits = findSearchHits(items, '销售');
    expect(hits.map((hit) => hit.itemId)).toEqual(['u1']);
  });

  it('matches tool names', () => {
    const hits = findSearchHits(items, 'read_file');
    expect(hits.map((hit) => hit.itemId)).toEqual(['t1']);
  });

  it('returns empty for blank query', () => {
    expect(findSearchHits(items, '   ')).toEqual([]);
  });
});

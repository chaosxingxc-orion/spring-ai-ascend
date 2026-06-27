import { describe, expect, it } from 'vitest';
import type { ChatItem } from '../types/events';
import { mergeServerChatWithLocal } from './chatMerge';

describe('mergeServerChatWithLocal', () => {
  it('keeps local answered state over stale server cancelled', () => {
    const local: ChatItem[] = [{
      id: 'question-e58b44641ea286c5',
      kind: 'question',
      questionId: 'q1',
      question: '确认研究参数',
      options: ['完整模式'],
      allowFreeText: true,
      multiSelect: false,
      status: 'answered',
      selections: ['完整模式'],
      seq: 37,
    }];
    const server: ChatItem[] = [{
      id: 'question-e58b44641ea286c5',
      kind: 'question',
      questionId: 'q1',
      question: '确认研究参数',
      options: ['完整模式'],
      allowFreeText: true,
      multiSelect: false,
      status: 'cancelled',
      seq: 37,
    }];
    const merged = mergeServerChatWithLocal(local, server);
    expect(merged[0]).toMatchObject({
      status: 'answered',
      selections: ['完整模式'],
    });
  });
});

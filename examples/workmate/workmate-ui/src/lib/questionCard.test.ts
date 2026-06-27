import { describe, expect, it } from 'vitest';
import { buildQuestionChatItem } from './questionCard';

describe('questionCard', () => {
  it('builds pending question chat item', () => {
    const item = buildQuestionChatItem({
      questionId: 'q-1',
      sessionId: 's-1',
      question: 'Which scope?',
      options: ['A', 'B'],
      allowFreeText: false,
      multiSelect: false,
    });
    expect(item.kind).toBe('question');
    expect(item.status).toBe('pending');
    expect(item.options).toEqual(['A', 'B']);
  });
});

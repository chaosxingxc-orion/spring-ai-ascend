import { describe, expect, it } from 'vitest';
import type { ChatItem } from '../types/events';
import { mergeStructuralQuestions, questionItemsFromRunEvents } from './questionHydrate';
import type { RunEventRow } from './reasoningHydrate';

describe('questionItemsFromRunEvents', () => {
  it('hydrates question cards missing from session_messages', () => {
    const messages: ChatItem[] = [
      {
        id: 'question-e58b44641ea286c5',
        kind: 'question',
        questionId: 'q1',
        question: '在开始研究之前，请确认以下参数：',
        options: ['完整模式'],
        allowFreeText: true,
        multiSelect: false,
        status: 'answered',
        seq: 36,
      },
    ];
    const events: RunEventRow[] = [
      {
        seq: 1366,
        name: 'question.required',
        data: {
          questionId: 'a8ffdc47-b8ef-45fe-9940-cfcf8d204339',
          sessionId: 'session-1',
          question: '以下是为"AI Agent"规划的研究报告大纲，请确认是否满意？',
          options: ['确认，按此大纲执行', '需要调整（请说明修改意见）'],
          allowFreeText: true,
          multiSelect: false,
          messageId: 'question-7d993dd852b0646e',
        },
      },
      { seq: 1400, name: 'message.delta', data: { delta: '继续', messageId: 'm2' } },
    ];
    const hydrated = questionItemsFromRunEvents(events, messages);
    expect(hydrated).toHaveLength(1);
    expect(hydrated[0]?.id).toBe('question-7d993dd852b0646e');
    expect(hydrated[0]?.status).toBe('cancelled');
    expect(hydrated[0]?.seq).toBe(1366);
  });

  it('marks cancelled when question.cancelled event is present', () => {
    const events: RunEventRow[] = [
      {
        seq: 10,
        name: 'question.required',
        data: {
          questionId: 'q-timeout',
          sessionId: 'session-1',
          question: 'Pick one',
          options: ['A', 'B'],
          allowFreeText: false,
          multiSelect: false,
          messageId: 'question-timeout',
        },
      },
      {
        seq: 11,
        name: 'question.cancelled',
        data: {
          questionId: 'q-timeout',
          sessionId: 'session-1',
          question: 'Pick one',
          options: ['A', 'B'],
          allowFreeText: false,
          multiSelect: false,
          status: 'cancelled',
          reason: 'timeout',
          messageId: 'question-timeout',
        },
      },
    ];
    const hydrated = questionItemsFromRunEvents(events, []);
    expect(hydrated).toHaveLength(1);
    expect(hydrated[0]?.status).toBe('cancelled');
  });

  it('merges hydrated cards into structural timeline', () => {
    const structural: ChatItem[] = [
      { id: 'u1', kind: 'user', text: 'go', seq: 1 },
      {
        id: 'question-e58b44641ea286c5',
        kind: 'question',
        questionId: 'q1',
        question: '确认参数',
        options: [],
        allowFreeText: true,
        multiSelect: false,
        status: 'answered',
        seq: 36,
      },
    ];
    const hydrated: ChatItem[] = [{
      id: 'question-7d993dd852b0646e',
      kind: 'question',
      questionId: 'q2',
      question: '确认大纲',
      options: ['确认'],
      allowFreeText: true,
      multiSelect: false,
      status: 'cancelled',
      seq: 1366,
    }];
    const merged = mergeStructuralQuestions(structural, hydrated as Extract<ChatItem, { kind: 'question' }>[]);
    expect(merged.filter((item) => item.kind === 'question')).toHaveLength(2);
  });
});

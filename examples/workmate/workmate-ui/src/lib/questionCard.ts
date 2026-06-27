import type { ChatItem, QuestionRequiredPayload } from '../types/events';

export function buildQuestionChatItem(
  payload: QuestionRequiredPayload,
  seq?: number,
): Extract<ChatItem, { kind: 'question' }> {
  const stableId = payload.messageId?.trim() || `question-${payload.questionId}`;
  return {
    id: stableId,
    kind: 'question',
    questionId: payload.questionId,
    question: payload.question,
    options: payload.options ?? [],
    allowFreeText: payload.allowFreeText ?? true,
    multiSelect: payload.multiSelect ?? false,
    status: 'pending',
    seq,
  };
}

export function sessionHasPendingQuestion(
  sessionId: string,
  chatBySession: Record<string, ChatItem[]>,
): boolean {
  return (chatBySession[sessionId] ?? []).some(
    (item) => item.kind === 'question' && item.status === 'pending',
  );
}

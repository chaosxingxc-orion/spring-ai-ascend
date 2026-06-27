import type { ChatItem } from '../types/events';
import { parseQuestionRequired } from './eventPayload';
import { buildQuestionChatItem } from './questionCard';
import type { RunEventRow } from './reasoningHydrate';

type QuestionItem = Extract<ChatItem, { kind: 'question' }>;

function readMessageId(data: Record<string, unknown> | undefined): string | undefined {
  if (!data) {
    return undefined;
  }
  const direct = data.messageId ?? data.message_id;
  return typeof direct === 'string' && direct.trim() ? direct.trim() : undefined;
}

function questionRank(item: QuestionItem): number {
  const statusWeight = item.status === 'pending'
    ? 3
    : item.status === 'answered' || item.status === 'skipped'
      ? 2
      : 1;
  return statusWeight * 1_000_000 + (item.seq ?? 0);
}

function mergeQuestion(current: QuestionItem, next: QuestionItem): QuestionItem {
  if (next.status !== 'pending' && current.status === 'pending') {
    return next;
  }
  if (current.status !== 'pending' && next.status === 'pending') {
    return current;
  }
  return questionRank(next) >= questionRank(current) ? next : current;
}

/**
 * Rebuild question cards from run_events when session_messages missed a persist
 * (e.g. leader HITL during long team runs). Persisted rows win on id conflict.
 */
export function questionItemsFromRunEvents(
  events: RunEventRow[],
  persisted: ChatItem[],
): QuestionItem[] {
  const persistedById = new Map<string, QuestionItem>();
  for (const item of persisted) {
    if (item.kind === 'question') {
      persistedById.set(item.id, item);
    }
  }

  const hydrated = new Map<string, QuestionItem>();
  const maxEventSeq = events.reduce((max, event) => Math.max(max, event.seq ?? 0), 0);
  for (const event of events) {
    if (event.name === 'question.cancelled' && event.data) {
      const payload = parseQuestionRequired(event.data);
      if (!payload) {
        continue;
      }
      const messageId = payload.messageId ?? readMessageId(event.data);
      const stableId = messageId ?? `question-${payload.questionId}`;
      const cancelled: QuestionItem = {
        ...buildQuestionChatItem({ ...payload, messageId }, event.seq),
        id: stableId,
        status: 'cancelled',
      };
      const existing = hydrated.get(stableId);
      hydrated.set(stableId, existing ? mergeQuestion(existing, cancelled) : cancelled);
      continue;
    }
    if (event.name !== 'question.required' || !event.data) {
      continue;
    }
    const payload = parseQuestionRequired(event.data);
    if (!payload) {
      continue;
    }
    const messageId = payload.messageId ?? readMessageId(event.data);
    const item = buildQuestionChatItem({ ...payload, messageId }, event.seq);
    const stableId = messageId ?? item.id;
    if (persistedById.has(stableId)) {
      continue;
    }
    let candidate: QuestionItem = { ...item, id: stableId };
    if (candidate.status === 'pending' && maxEventSeq > (event.seq ?? 0) + 2) {
      candidate = { ...candidate, status: 'cancelled' };
    }
    const existing = hydrated.get(stableId);
    hydrated.set(stableId, existing ? mergeQuestion(existing, candidate) : candidate);
  }

  return [...hydrated.values()];
}

export function mergeStructuralQuestions(
  structural: ChatItem[],
  hydrated: QuestionItem[],
): ChatItem[] {
  if (hydrated.length === 0) {
    return structural;
  }
  const ids = new Set(structural.filter((item) => item.kind === 'question').map((item) => item.id));
  const extras = hydrated.filter((item) => !ids.has(item.id));
  return extras.length === 0 ? structural : [...structural, ...extras];
}

import type { ChatItem } from '../types/events';

type QuestionItem = Extract<ChatItem, { kind: 'question' }>;

function isTerminalQuestion(item: QuestionItem): boolean {
  return item.status === 'answered' || item.status === 'skipped';
}

function questionKey(item: QuestionItem): string {
  return item.questionId || item.id;
}

/**
 * Keep optimistic/local terminal question answers when a stale server hydrate
 * still reports pending or cancelled (e.g. sync raced with answer API).
 */
export function mergeServerChatWithLocal(local: ChatItem[], server: ChatItem[]): ChatItem[] {
  const terminalByKey = new Map<string, QuestionItem>();
  for (const item of local) {
    if (item.kind === 'question' && isTerminalQuestion(item)) {
      terminalByKey.set(questionKey(item), item);
    }
  }
  if (terminalByKey.size === 0) {
    return server;
  }
  return server.map((item) => {
    if (item.kind !== 'question') {
      return item;
    }
    const terminal = terminalByKey.get(questionKey(item));
    if (!terminal) {
      return item;
    }
    if (item.status === 'pending' || item.status === 'cancelled') {
      return {
        ...item,
        status: terminal.status,
        selections: terminal.selections ?? item.selections,
        answerText: terminal.answerText ?? item.answerText,
      };
    }
    return item;
  });
}

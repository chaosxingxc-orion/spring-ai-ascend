import { describe, expect, it } from 'vitest';
import { projectShareChatItems } from './shareChatProjection';

describe('shareChatProjection', () => {
  it('enriches messages projection with reasoning run_events', () => {
    const items = projectShareChatItems(
      [{ id: 'a1', kind: 'assistant', text: 'done' }],
      [
        { seq: 1, name: 'reasoning.delta', data: { text: 'think' } },
        { seq: 2, name: 'message.delta', data: { text: 'done' } },
      ],
      'messages',
    );
    expect(items.some((item) => item.kind === 'assistant')).toBe(true);
    expect(items.some((item) => item.kind === 'reasoning' && item.text === 'think')).toBe(true);
  });

  it('rebuilds chat from run-events mode', () => {
    const items = projectShareChatItems(
      [],
      [
        { seq: 1, name: 'message.user', data: { text: 'hi' } },
        { seq: 2, name: 'message.delta', data: { text: 'hello' } },
      ],
      'run-events',
    );
    expect(items).toHaveLength(2);
    expect(items[0]?.kind).toBe('user');
    expect(items[1]?.kind).toBe('assistant');
  });
});

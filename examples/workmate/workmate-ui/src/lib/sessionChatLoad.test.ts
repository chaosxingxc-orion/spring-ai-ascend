import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ChatItem } from '../types/events';

const listSessionMessages = vi.fn();
const listSessionRunEvents = vi.fn();

vi.mock('../api/client', () => ({
  listSessionMessages: (...args: unknown[]) => listSessionMessages(...args),
  listSessionRunEvents: (...args: unknown[]) => listSessionRunEvents(...args),
  invalidateRunEventsCache: vi.fn(),
}));

import { hydrateSessionChat, invalidateSessionChatHydration } from './sessionChatLoad';

afterEach(() => {
  invalidateSessionChatHydration();
  listSessionMessages.mockReset();
  listSessionRunEvents.mockReset();
});

describe('hydrateSessionChat', () => {
  it('returns structural items before run_events resolve', async () => {
    const structural: ChatItem[] = [{ id: 'u1', kind: 'user', text: 'hi' }];
    let resolveEvents!: (value: unknown[]) => void;
    const eventsPromise = new Promise<unknown[]>((resolve) => {
      resolveEvents = resolve;
    });

    listSessionMessages.mockResolvedValue(structural);
    listSessionRunEvents.mockReturnValue(eventsPromise);

    const onStructural = vi.fn();
    const hydration = hydrateSessionChat('sess-1', { onStructural });

    await vi.waitFor(() => expect(onStructural).toHaveBeenCalled());
    expect(onStructural.mock.calls[0][0]).toEqual(structural);
    expect(listSessionRunEvents).toHaveBeenCalled();

    resolveEvents([{ seq: 1, name: 'message.delta', data: { text: { preview: 'ok' } } }]);
    const result = await hydration;
    expect(result.events).toHaveLength(1);
    expect(result.fullItems.length).toBeGreaterThanOrEqual(1);
  });

  it('dedupes concurrent hydrations for the same session', async () => {
    listSessionMessages.mockResolvedValue([]);
    listSessionRunEvents.mockResolvedValue([]);

    const [a, b] = await Promise.all([
      hydrateSessionChat('sess-2'),
      hydrateSessionChat('sess-2'),
    ]);

    expect(a).toBe(b);
    expect(listSessionMessages).toHaveBeenCalledTimes(1);
    expect(listSessionRunEvents).toHaveBeenCalledTimes(1);
  });
});

import { describe, expect, it } from 'vitest';
import { appShellReducer, initialAppState } from './appShellState';

describe('appShellReducer', () => {
  it('selects session and clears selection', () => {
    const selected = appShellReducer(initialAppState, { type: 'select', id: 's1' });
    expect(selected.activeId).toBe('s1');

    const cleared = appShellReducer(selected, { type: 'clear-select' });
    expect(cleared.activeId).toBeNull();
  });

  it('hydrates chat only when empty', () => {
    const items = [{ kind: 'user' as const, seq: 1, text: 'hi' }];
    const hydrated = appShellReducer(initialAppState, {
      type: 'hydrate-chat',
      sessionId: 's1',
      items,
    });
    expect(hydrated.chatBySession.s1).toEqual(items);

    const skipped = appShellReducer(hydrated, {
      type: 'hydrate-chat',
      sessionId: 's1',
      items: [{ kind: 'user' as const, seq: 2, text: 'other' }],
    });
    expect(skipped.chatBySession.s1).toEqual(items);
  });
});

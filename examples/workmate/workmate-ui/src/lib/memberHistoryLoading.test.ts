import { describe, expect, it } from 'vitest';
import type { ChatItem } from '../types/events';
import type { TeamState } from './teamStatus';
import { shouldShowMemberHistoryLoading } from './memberHistoryLoading';

const baseTeam = (memberId: string, patch: Partial<TeamState['members'][0]> = {}): TeamState => ({
  pattern: 'orchestrator',
  collaboration: 'sequential',
  lead: null,
  members: [{ id: memberId, name: memberId, status: 'idle', ...patch }],
  phase: 'running',
  anyMemberFailed: false,
  visualizationMode: 'delegation',
});

describe('shouldShowMemberHistoryLoading', () => {
  it('shows while team run_events hydrate', () => {
    expect(
      shouldShowMemberHistoryLoading({
        memberId: 'writer',
        memberItems: [],
        team: baseTeam('writer'),
        teamHistoryLoading: true,
      }),
    ).toBe(true);
  });

  it('hides once member items exist', () => {
    const items: ChatItem[] = [
      { id: 'a1', kind: 'assistant', text: 'hi', memberId: 'writer' },
    ];
    expect(
      shouldShowMemberHistoryLoading({
        memberId: 'writer',
        memberItems: items,
        team: baseTeam('writer', { status: 'running' }),
        teamHistoryLoading: true,
      }),
    ).toBe(false);
  });

  it('shows for active member with empty timeline after events loaded', () => {
    expect(
      shouldShowMemberHistoryLoading({
        memberId: 'writer',
        memberItems: [],
        team: {
          ...baseTeam('writer', { status: 'running', hasStarted: true }),
          activeMemberIds: ['writer'],
        },
        teamHistoryLoading: false,
      }),
    ).toBe(true);
  });

  it('hides for idle member that never started', () => {
    expect(
      shouldShowMemberHistoryLoading({
        memberId: 'writer',
        memberItems: [],
        team: baseTeam('writer'),
        teamHistoryLoading: false,
      }),
    ).toBe(false);
  });
});

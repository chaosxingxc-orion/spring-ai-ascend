import { describe, expect, it } from 'vitest';
import {
  buildMirroredLeaderWorkspaceToolKeys,
  leaderWorkspaceToolItemsFromRunEvents,
} from './leaderToolHydrate';
import type { RunEventRow } from './reasoningHydrate';

describe('leaderToolHydrate member mirror filter', () => {
  const mirrorPair: RunEventRow[] = [
    {
      seq: 100,
      name: 'tool.start',
      data: { toolName: 'mcp__qieman__SearchFunds', toolCallId: 'leader-1' },
    },
    {
      seq: 101,
      name: 'tool.end',
      data: { toolName: 'mcp__qieman__SearchFunds', toolCallId: 'leader-1', result: { success: true } },
    },
    {
      seq: 103,
      name: 'tool.start',
      data: {
        toolName: 'mcp__qieman__SearchFunds',
        toolCallId: 'member-1',
        surface: 'team',
        memberId: 'stock-researcher',
        parentRunId: 'parent-1',
      },
    },
  ];

  it('detects leader-scope echo of member team-surface tools', () => {
    const keys = buildMirroredLeaderWorkspaceToolKeys(mirrorPair);
    expect(keys.has('id:leader-1')).toBe(true);
    expect(keys.has('id:member-1')).toBe(false);
  });

  it('drops mirrored leader workspace tools from run_events projection', () => {
    const items = leaderWorkspaceToolItemsFromRunEvents(mirrorPair);
    expect(items).toHaveLength(0);
  });

  it('keeps leader-only workspace tools without a member mirror', () => {
    const events: RunEventRow[] = [
      {
        seq: 50,
        name: 'tool.start',
        data: { toolName: 'workmate_bash', toolCallId: 'bash-1', args: { command: 'ls' } },
      },
      {
        seq: 51,
        name: 'tool.end',
        data: { toolName: 'workmate_bash', toolCallId: 'bash-1', result: { success: true } },
      },
    ];
    expect(leaderWorkspaceToolItemsFromRunEvents(events)).toHaveLength(1);
  });

  it('keeps leader team.send_message even when members run plain send_message later', () => {
    const events: RunEventRow[] = [
      {
        seq: 200,
        name: 'tool.start',
        data: { toolName: 'team.send_message', toolCallId: 'deleg-1', args: { memberId: 'writer' } },
      },
      {
        seq: 210,
        name: 'tool.start',
        data: {
          toolName: 'send_message',
          toolCallId: 'member-handback',
          surface: 'team',
          memberId: 'writer',
          parentRunId: 'parent-1',
        },
      },
    ];
    expect(buildMirroredLeaderWorkspaceToolKeys(events).size).toBe(0);
  });

  it('filters leader plain send_message echoes of member handback tools', () => {
    const events: RunEventRow[] = [
      {
        seq: 300,
        name: 'tool.start',
        data: { toolName: 'send_message', toolCallId: 'leader-handback' },
      },
      {
        seq: 303,
        name: 'tool.start',
        data: {
          toolName: 'send_message',
          toolCallId: 'member-handback',
          surface: 'team',
          memberId: 'writer',
          parentRunId: 'parent-1',
        },
      },
    ];
    expect(leaderWorkspaceToolItemsFromRunEvents(events)).toHaveLength(0);
    expect(buildMirroredLeaderWorkspaceToolKeys(events).has('id:leader-handback')).toBe(true);
  });
});

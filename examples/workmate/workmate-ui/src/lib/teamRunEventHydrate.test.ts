import { describe, expect, it } from 'vitest';
import { teamToolItemsFromRunEvents, teamBypassSystemItemsFromRunEvents } from './teamRunEventHydrate';
import type { RunEventRow } from './reasoningHydrate';
import { initialTeamState } from './teamStatus';

describe('teamRunEventHydrate', () => {
  it('synthesizes build and send tool cards from team events', () => {
    const events: RunEventRow[] = [
      {
        seq: 54,
        name: 'team.build.completed',
        data: { displayName: 'research-ai-agent-enterprise', teamName: 'team-1' },
      },
      {
        seq: 77,
        name: 'team.member.started',
        data: { memberId: 'topic-researcher', memberName: '谭溯源' },
      },
      {
        seq: 78,
        name: 'team.member.completed',
        data: { memberId: 'topic-researcher', memberName: '谭溯源' },
      },
    ];
    const items = teamToolItemsFromRunEvents(events);
    expect(items.some((item) => item.kind === 'tool' && item.toolName === 'team.build_team')).toBe(true);
    const send = items.find(
      (item): item is Extract<typeof item, { kind: 'tool' }> =>
        item.kind === 'tool' && item.toolName === 'team.send_message',
    );
    expect(send?.status).toBe('success');
  });

  it('synthesizes one build card when team.build.completed is re-delivered on reawaken', () => {
    const events: RunEventRow[] = [
      {
        seq: 71,
        name: 'team.build.completed',
        data: { displayName: 'research-ai-agent-enterprise', teamName: 'team-1' },
      },
      {
        seq: 31589,
        name: 'team.build.completed',
        data: { displayName: 'research-ai-agent-enterprise', teamName: 'team-1' },
      },
    ];
    const buildCards = teamToolItemsFromRunEvents(events).filter(
      (item) => item.kind === 'tool' && item.toolName === 'team.build_team',
    );
    expect(buildCards).toHaveLength(1);
  });

  it('does not render plain send_message leader echoes (member handback mirrors)', () => {
    const events: RunEventRow[] = [
      {
        seq: 1855,
        name: 'tool.start',
        data: { toolName: 'send_message', toolCallId: 'leader-echo', args: { to: 'team-lead' } },
      },
      {
        seq: 1856,
        name: 'tool.end',
        data: { toolName: 'send_message', toolCallId: 'leader-echo', result: { success: true } },
      },
      {
        seq: 1858,
        name: 'tool.start',
        data: {
          toolName: 'send_message',
          toolCallId: 'member-echo',
          surface: 'team',
          memberId: 'stock-researcher',
          parentRunId: 'parent-1',
        },
      },
      {
        seq: 1544,
        name: 'tool.start',
        data: {
          toolName: 'team.send_message',
          toolCallId: 'deleg-1',
          args: { memberId: 'stock-researcher', description: 'Phase1' },
        },
      },
      {
        seq: 1545,
        name: 'tool.end',
        data: { toolName: 'team.send_message', toolCallId: 'deleg-1', result: { success: true } },
      },
    ];
    const items = teamToolItemsFromRunEvents(events);
    expect(items.some((item) => item.kind === 'tool' && item.toolName === 'send_message')).toBe(false);
    expect(items.some((item) => item.kind === 'tool' && item.toolName === 'team.send_message')).toBe(true);
  });

  it('projects user bypass messages as system timeline rows', () => {
    const items = teamBypassSystemItemsFromRunEvents([
      {
        seq: 120,
        name: 'team.member.message',
        data: {
          from: '__user__',
          fromLabel: '用户',
          to: '@topic-researcher',
          message: '请补充来源链接',
        },
      },
    ]);
    expect(items).toHaveLength(1);
    expect(items[0]).toMatchObject({
      kind: 'system',
      tone: 'info',
      text: '用户 旁路 → @topic-researcher：请补充来源链接',
    });
  });
});

describe('initialTeamState delegation', () => {
  it('seeds delegation from expert runtime', () => {
    const team = initialTeamState({
      id: 'gpt-researcher-team',
      name: '深度研究团队',
      description: '',
      expertType: 'team',
      tags: [],
      skillCompatibility: [],
      teamRuntime: 'openjiuwen-team',
      members: [{ id: 'topic-researcher', name: '谭溯源', expertId: 'x', order: 1 }],
    });
    expect(team?.visualizationMode).toBe('delegation');
  });
});

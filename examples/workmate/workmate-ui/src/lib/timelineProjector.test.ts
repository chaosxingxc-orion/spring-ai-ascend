import { describe, expect, it } from 'vitest';
import type { ChatItem } from '../types/events';
import { projectChatTimeline } from './timelineProjector';
import type { RunEventRow } from './reasoningHydrate';

describe('projectChatTimeline', () => {
  it('orders like the reference workbench: reasoning → assistant → tool per round', () => {
    const messages: ChatItem[] = [
      { id: 'u1', kind: 'user', text: '调研', seq: 1 },
    ];
    const events: RunEventRow[] = [
      { seq: 5, name: 'reasoning.delta', data: { text: '想一下' } },
      { seq: 8, name: 'message.delta', data: { delta: '收到', messageId: 'm1' } },
      { seq: 10, name: 'tool.start', data: { toolName: 'team.build_team', toolCallId: 'tb1', args: { teamName: 't1' } } },
      { seq: 11, name: 'tool.end', data: { toolName: 'team.build_team', toolCallId: 'tb1', result: { success: true } } },
      { seq: 15, name: 'reasoning.delta', data: { text: '派活' } },
      { seq: 18, name: 'message.delta', data: { delta: '已建团队', messageId: 'm2' } },
      { seq: 20, name: 'tool.start', data: { toolName: 'team.send_message', toolCallId: 'ts1', args: { memberId: 'r1', description: 'Phase1' } } },
      { seq: 21, name: 'tool.end', data: { toolName: 'team.send_message', toolCallId: 'ts1', result: { success: true } } },
    ];
    const timeline = projectChatTimeline(messages, events);
    expect(timeline[0].kind).toBe('user');
    const kinds = timeline.slice(1).map((item) => item.kind);
    expect(kinds).toEqual(['reasoning', 'assistant', 'tool', 'reasoning', 'assistant', 'tool']);
    expect(timeline.find((item) => item.kind === 'tool' && item.toolName.includes('build'))?.seq).toBe(10);
  });

  it('places build_team before post-build assistant text on reload', () => {
    const messages: ChatItem[] = [{ id: 'u1', kind: 'user', text: '调研', seq: 1 }];
    const events: RunEventRow[] = [
      { seq: 5, name: 'message.delta', data: { delta: '准备建团队。', messageId: 'm1' } },
      { seq: 10, name: 'tool.start', data: { toolName: 'team.build_team', toolCallId: 'tb1', args: { teamName: '深度研究' } } },
      { seq: 11, name: 'tool.end', data: { toolName: 'team.build_team', toolCallId: 'tb1', result: { success: true } } },
      { seq: 15, name: 'message.delta', data: { delta: '团队已创建。', messageId: 'm1' } },
    ];
    const timeline = projectChatTimeline(messages, events);
    const kinds = timeline.slice(1).map((item) => item.kind);
    expect(kinds).toEqual(['assistant', 'tool', 'assistant']);
    const assistants = timeline.filter((item) => item.kind === 'assistant');
    expect(assistants[0]).toMatchObject({ text: '准备建团队。' });
    expect(assistants[1]).toMatchObject({ text: '团队已创建。' });
  });

  it('falls back to persisted messages when no run_events', () => {
    const messages: ChatItem[] = [
      { id: 'u1', kind: 'user', text: 'hi', seq: 1 },
      { id: 'a1', kind: 'assistant', text: 'hello', seq: 2 },
    ];
    expect(projectChatTimeline(messages, [])).toEqual(messages);
  });

  it('splits leader narration around persisted question cards on reload', () => {
    const messages: ChatItem[] = [
      { id: 'u1', kind: 'user', text: '深度调研', seq: 1 },
      {
        id: 'q1',
        kind: 'question',
        questionId: 'q1',
        question: '确认模式',
        options: [],
        allowFreeText: true,
        multiSelect: false,
        status: 'answered',
        seq: 37,
      },
      {
        id: 'q2',
        kind: 'question',
        questionId: 'q2',
        question: '确认参数',
        options: [],
        allowFreeText: true,
        multiSelect: false,
        status: 'answered',
        seq: 50,
      },
    ];
    const events: RunEventRow[] = [
      { seq: 5, name: 'message.delta', data: { delta: '开始前先确认。', messageId: 'm1' } },
      { seq: 40, name: 'message.delta', data: { delta: '收到模式。', messageId: 'm1' } },
      { seq: 60, name: 'message.delta', data: { delta: '参数确认完毕。', messageId: 'm1' } },
    ];
    const timeline = projectChatTimeline(messages, events);
    expect(timeline.map((item) => item.id)).toEqual(['u1', 'm1', 'q1', 'm1#1', 'q2', 'm1#2']);
  });

  it('prefers full delegation args from session_messages over redacted events', () => {
    const fullTask = '## 任务: Phase 1 初始调研\n\n请完成完整的市场扫描……';
    const messages: ChatItem[] = [
      { id: 'u1', kind: 'user', text: 'go', seq: 1 },
      {
        id: 'ts-persisted',
        kind: 'tool',
        toolName: 'team.send_message',
        toolCallId: 'ts1',
        status: 'success',
        seq: 20,
        args: { memberId: 'm1', memberName: '谭溯源', message: fullTask },
      },
    ];
    const events: RunEventRow[] = [
      {
        seq: 20,
        name: 'tool.start',
        data: {
          toolName: 'team.send_message',
          toolCallId: 'ts1',
          args: { memberId: 'm1', message: { preview: '## 任务: Phase 1…', bytes: 400 } },
        },
      },
      {
        seq: 21,
        name: 'tool.end',
        data: { toolName: 'team.send_message', toolCallId: 'ts1', result: { success: true } },
      },
    ];
    const tool = projectChatTimeline(messages, events).find(
      (item) => item.kind === 'tool' && item.toolCallId === 'ts1',
    );
    expect(tool?.kind).toBe('tool');
    if (tool?.kind === 'tool') {
      expect((tool.args as Record<string, unknown>).message).toBe(fullTask);
    }
  });

  it('hydrates outline question cards from run_events on reload', () => {
    const messages: ChatItem[] = [
      { id: 'u1', kind: 'user', text: '调研', seq: 1 },
    ];
    const events: RunEventRow[] = [
      {
        seq: 37,
        name: 'question.required',
        data: {
          questionId: 'q1',
          sessionId: 's1',
          question: '确认参数',
          options: ['完整模式'],
          messageId: 'question-e58b44641ea286c5',
        },
      },
      {
        seq: 1366,
        name: 'question.required',
        data: {
          questionId: 'q2',
          sessionId: 's1',
          question: '以下是大纲，请确认是否满意？',
          options: ['确认，按此大纲执行'],
          messageId: 'question-7d993dd852b0646e',
        },
      },
      { seq: 1400, name: 'message.delta', data: { delta: '继续', messageId: 'm2' } },
    ];
    const timeline = projectChatTimeline(messages, events);
    const questions = timeline.filter((item) => item.kind === 'question');
    expect(questions).toHaveLength(2);
    expect(questions.map((item) => item.id)).toContain('question-7d993dd852b0646e');
  });

  it('keeps leader bash tools after expert switch and second user turn on reload', () => {
    const messages: ChatItem[] = [
      { id: 'u1', kind: 'user', text: '第一个专家问题', seq: 1 },
      { id: 'a1', kind: 'assistant', text: '第一个专家回答', seq: 3 },
      {
        id: 'sw1',
        kind: 'expert-switched',
        fromExpertName: '专家A',
        toExpertName: '专家B',
        toExpertId: 'b',
        newGeneration: 1,
        seq: 40,
      },
      { id: 'u2', kind: 'user', text: '今天寒武纪风险如何', seq: 41 },
      {
        id: 't1',
        kind: 'tool',
        toolName: 'workmate_bash',
        toolCallId: 'c1',
        status: 'success',
        seq: 50,
      },
      {
        id: 't2',
        kind: 'tool',
        toolName: 'workmate_bash',
        toolCallId: 'c2',
        status: 'success',
        seq: 54,
      },
    ];
    const events: RunEventRow[] = [
      { seq: 42, name: 'message.delta', data: { delta: '我来检查', messageId: 'a2' } },
      { seq: 50, name: 'tool.start', data: { toolName: 'workmate_bash', toolCallId: 'c1' } },
      { seq: 51, name: 'tool.end', data: { toolName: 'workmate_bash', toolCallId: 'c1', result: { success: true } } },
      { seq: 54, name: 'tool.start', data: { toolName: 'workmate_bash', toolCallId: 'c2' } },
      { seq: 55, name: 'tool.end', data: { toolName: 'workmate_bash', toolCallId: 'c2', result: { success: true } } },
      { seq: 56, name: 'message.delta', data: { delta: '工作区目前是空的', messageId: 'a2' } },
    ];
    const timeline = projectChatTimeline(messages, events);
    const u2Index = timeline.findIndex((item) => item.kind === 'user' && item.text.includes('寒武纪'));
    const firstToolIndex = timeline.findIndex((item) => item.kind === 'tool');
    const switchIndex = timeline.findIndex((item) => item.kind === 'expert-switched');
    expect(switchIndex).toBeGreaterThan(timeline.findIndex((item) => item.id === 'a1'));
    expect(u2Index).toBeGreaterThan(switchIndex);
    expect(firstToolIndex).toBeGreaterThan(u2Index);
    expect(timeline.find((item) => item.kind === 'tool')?.seq).toBe(50);
  });

  it('drops leader-scope mirrors of member team-surface MCP tools on reload', () => {
    const messages: ChatItem[] = [
      { id: 'u1', kind: 'user', text: '分析基金', seq: 1378 },
      {
        id: 'leader-mcp',
        kind: 'tool',
        toolName: 'mcp__qieman__SearchFunds',
        toolCallId: 'mirror-1',
        status: 'success',
        seq: 1683,
      },
      {
        id: 'deleg',
        kind: 'tool',
        toolName: 'team.send_message',
        toolCallId: 'deleg-1',
        status: 'success',
        seq: 1544,
      },
    ];
    const events: RunEventRow[] = [
      {
        seq: 1544,
        name: 'tool.start',
        data: { toolName: 'team.send_message', toolCallId: 'deleg-1', args: { memberId: 'writer' } },
      },
      {
        seq: 1545,
        name: 'tool.end',
        data: { toolName: 'team.send_message', toolCallId: 'deleg-1', result: { success: true } },
      },
      {
        seq: 1683,
        name: 'tool.start',
        data: { toolName: 'mcp__qieman__SearchFunds', toolCallId: 'mirror-1' },
      },
      {
        seq: 1684,
        name: 'tool.end',
        data: { toolName: 'mcp__qieman__SearchFunds', toolCallId: 'mirror-1', result: { success: true } },
      },
      {
        seq: 1686,
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
    const timeline = projectChatTimeline(messages, events);
    const leaderTools = timeline.filter(
      (item): item is Extract<ChatItem, { kind: 'tool' }> =>
        item.kind === 'tool' && !item.memberId,
    );
    expect(leaderTools.some((item) => item.toolCallId === 'mirror-1')).toBe(false);
    expect(leaderTools.some((item) => item.toolCallId === 'deleg-1')).toBe(true);
    expect(timeline.some((item) => item.kind === 'tool' && item.memberId === 'stock-researcher')).toBe(true);
  });

  it('drops plain send_message leader echoes from the projected timeline', () => {
    const messages: ChatItem[] = [
      { id: 'u1', kind: 'user', text: '分析基金', seq: 1378 },
      {
        id: 'echo-1',
        kind: 'tool',
        toolName: 'send_message',
        toolCallId: 'leader-echo',
        status: 'success',
        seq: 1854,
      },
      {
        id: 'deleg-1',
        kind: 'tool',
        toolName: 'team.send_message',
        toolCallId: 'deleg-1',
        status: 'success',
        seq: 1542,
      },
    ];
    const events: RunEventRow[] = [
      {
        seq: 1544,
        name: 'tool.start',
        data: { toolName: 'team.send_message', toolCallId: 'deleg-1', args: { memberId: 'writer' } },
      },
      {
        seq: 1545,
        name: 'tool.end',
        data: { toolName: 'team.send_message', toolCallId: 'deleg-1', result: { success: true } },
      },
      {
        seq: 1855,
        name: 'tool.start',
        data: { toolName: 'send_message', toolCallId: 'leader-echo' },
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
    ];
    const timeline = projectChatTimeline(messages, events);
    const leaderSend = timeline.filter(
      (item): item is Extract<ChatItem, { kind: 'tool' }> =>
        item.kind === 'tool' && !item.memberId && item.toolName.includes('send_message'),
    );
    expect(leaderSend).toHaveLength(1);
    expect(leaderSend[0]?.toolName).toBe('team.send_message');
  });
});

import { describe, expect, it } from 'vitest';
import type { ChatItem } from '../types/events';
import {
  delegationInputForMember,
  delegationCardsForMember,
  delegationInputFromRawMessages,
  enrichDelegationToolItems,
  mergeDelegationInputs,
  mergeMemberTimeline,
} from './delegationInput';

describe('delegationInputForMember', () => {
  const fullTask = [
    '## 任务：Phase 1 初始调研',
    '',
    '## 研究参数卡（只读）',
    '',
    '### 基本信息',
    '- 课题：AI Agent',
    '',
    '#### 深度调研要求',
    '请完成完整的市场扫描。',
  ].join('\n');

  it('prefers full message over messagePreview', () => {
    const items: ChatItem[] = [{
      id: 't1',
      kind: 'tool',
      toolName: 'team.send_message',
      toolCallId: 'ts1',
      status: 'success',
      args: {
        memberId: 'researcher',
        message: fullTask,
        messagePreview: `${fullTask.slice(0, 120)}…`,
      },
    }];
    const input = delegationInputForMember(items, 'researcher');
    expect(input?.message).toBe(fullTask);
    expect(input?.truncated).toBe(false);
  });

  it('marks clipped preview-only payloads as truncated', () => {
    const preview = `${'x'.repeat(280)}…`;
    const items: ChatItem[] = [{
      id: 't1',
      kind: 'tool',
      toolName: 'team.send_message',
      toolCallId: 'ts1',
      status: 'success',
      args: { memberId: 'researcher', messagePreview: preview },
    }];
    const input = delegationInputForMember(items, 'researcher');
    expect(input?.message).toBe(preview);
    expect(input?.truncated).toBe(true);
  });

  it('does not mark short full messages as truncated when preview equals message', () => {
    const shortTask = '## 任务\n\n简短说明';
    const items: ChatItem[] = [{
      id: 't1',
      kind: 'tool',
      toolName: 'team.send_message',
      toolCallId: 'ts1',
      status: 'success',
      args: {
        memberId: 'researcher',
        message: shortTask,
        messagePreview: shortTask,
      },
    }];
    const input = delegationInputForMember(items, 'researcher');
    expect(input?.message).toBe(shortTask);
    expect(input?.truncated).toBe(false);
  });

  it('uses latest delegation for the member', () => {
    const items: ChatItem[] = [
      {
        id: 't1',
        kind: 'tool',
        toolName: 'team.send_message',
        toolCallId: 'ts1',
        status: 'success',
        seq: 10,
        args: { memberId: 'researcher', message: 'round 1' },
      },
      {
        id: 't2',
        kind: 'tool',
        toolName: 'team.send_message',
        toolCallId: 'ts2',
        status: 'success',
        seq: 20,
        args: { memberId: 'researcher', message: fullTask },
      },
    ];
    expect(delegationInputForMember(items, 'researcher')?.message).toBe(fullTask);
  });

  it('prefers newer shorter re-task over older longer delegation', () => {
    const items: ChatItem[] = [
      {
        id: 't1',
        kind: 'tool',
        toolName: 'team.send_message',
        toolCallId: 'ts1',
        status: 'success',
        seq: 10,
        args: { memberId: 'researcher', message: 'a very long first round task body' },
      },
      {
        id: 't2',
        kind: 'tool',
        toolName: 'team.send_message',
        toolCallId: 'ts2',
        status: 'executing',
        seq: 30,
        args: { memberId: 'researcher', message: 'round 2 revise outline' },
      },
    ];
    expect(delegationInputForMember(items, 'researcher')?.message).toBe('round 2 revise outline');
  });

  it('returns one card per send_message delegation', () => {
    const items: ChatItem[] = [
      {
        id: 't1',
        kind: 'tool',
        toolName: 'team.send_message',
        toolCallId: 'ts1',
        status: 'success',
        seq: 10,
        args: { memberId: 'researcher', message: 'round 1' },
      },
      {
        id: 't2',
        kind: 'tool',
        toolName: 'team.send_message',
        toolCallId: 'ts2',
        status: 'executing',
        seq: 30,
        args: { memberId: 'researcher', message: 'round 2', reawaken: true },
      },
    ];
    const cards = delegationCardsForMember(items, 'researcher');
    expect(cards).toHaveLength(2);
    expect(cards[0]?.round).toBe(1);
    expect(cards[0]?.message).toBe('round 1');
    expect(cards[1]?.round).toBe(2);
    expect(cards[1]?.message).toBe('round 2');
    expect(cards[1]?.reawaken).toBe(true);
  });

  it('merges delegation cards into member timeline by seq', () => {
    const memberItems: ChatItem[] = [{
      id: 'a1',
      kind: 'assistant',
      text: 'working',
      seq: 40,
      memberId: 'researcher',
    }];
    const cards = delegationCardsForMember([
      {
        id: 't1',
        kind: 'tool',
        toolName: 'team.send_message',
        toolCallId: 'ts1',
        status: 'success',
        seq: 10,
        args: { memberId: 'researcher', message: 'round 1' },
      },
      {
        id: 't2',
        kind: 'tool',
        toolName: 'team.send_message',
        toolCallId: 'ts2',
        status: 'executing',
        seq: 30,
        args: { memberId: 'researcher', message: 'round 2' },
      },
    ], 'researcher');
    const merged = mergeMemberTimeline(memberItems, cards);
    expect(merged.map((item) => item.kind)).toEqual([
      'member-delegation',
      'member-delegation',
      'assistant',
    ]);
  });
});

describe('enrichDelegationToolItems', () => {
  it('replaces redacted event args with persisted full message', () => {
    const fullTask = '## 任务\n\n' + '正文'.repeat(200);
    const eventTools: ChatItem[] = [{
      id: 't1',
      kind: 'tool',
      toolName: 'team.send_message',
      toolCallId: 'ts1',
      status: 'success',
      args: {
        memberId: 'researcher',
        message: { preview: '## 任务…', bytes: 1200 },
      },
    }];
    const persisted: ChatItem[] = [{
      id: 't1',
      kind: 'tool',
      toolName: 'team.send_message',
      toolCallId: 'ts1',
      status: 'success',
      args: { memberId: 'researcher', message: fullTask },
    }];
    const enriched = enrichDelegationToolItems(eventTools, persisted);
    const input = delegationInputForMember(enriched, 'researcher');
    expect(input?.message).toBe(fullTask);
    expect(input?.truncated).toBe(false);
  });
});

describe('mergeDelegationInputs', () => {
  it('keeps the longer non-truncated message', () => {
    const merged = mergeDelegationInputs(
      { message: 'short', truncated: true },
      { message: 'much longer task body', truncated: false },
    );
    expect(merged?.message).toBe('much longer task body');
    expect(merged?.truncated).toBe(false);
  });
});

describe('delegationInputFromRawMessages', () => {
  it('prefers kind=delegation rows with full message over clipped tool args', () => {
    const fullTask = '## 任务\n\n' + '完整正文'.repeat(120);
    const raw = [
      {
        seq: 10,
        kind: 'tool',
        toolName: 'team.send_message',
        toolCallId: 'ts1',
        status: 'done',
        args: {
          memberId: 'topic-researcher',
          message: { preview: `${fullTask.slice(0, 120)}…`, bytes: fullTask.length },
        },
      },
      {
        seq: 11,
        kind: 'delegation',
        toolCallId: 'ts1',
        memberId: 'topic-researcher',
        message: fullTask,
      },
    ];
    const input = delegationInputFromRawMessages(raw, 'topic-researcher');
    expect(input?.message).toBe(fullTask);
    expect(input?.truncated).toBe(false);
  });

  it('matches delegation rows by member name when ids differ in legacy payloads', () => {
    const raw = [{
      seq: 5,
      kind: 'delegation',
      toolCallId: 'ts1',
      memberId: 'topic-researcher',
      memberName: '谭溯源',
      message: '完整任务',
    }];
    expect(delegationInputFromRawMessages(raw, 'wrong-id', '谭溯源')?.message).toBe('完整任务');
  });
});

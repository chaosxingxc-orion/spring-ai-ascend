import { describe, expect, it } from 'vitest';
import type { ChatItem } from '../types/events';
import { foldLeaderMessageDeltas, mergeChatWithLeaderDeltas } from './leaderMessageHydrate';

describe('foldLeaderMessageDeltas', () => {
  it('skips team-surface member deltas', () => {
    const text = foldLeaderMessageDeltas([
      { seq: 1, name: 'message.delta', data: { delta: '主理人：' } },
      {
        seq: 2,
        name: 'message.delta',
        data: { delta: '成员输出', memberId: 'writer', parentRunId: 'run-1' },
      },
      { seq: 3, name: 'message.delta', data: { text: '继续' } },
    ]);
    expect(text).toBe('主理人：继续');
  });
});

describe('mergeChatWithLeaderDeltas', () => {
  it('updates trailing assistant on reload', () => {
    const items: ChatItem[] = [
      { id: 'u1', kind: 'user', text: '调研 AI Agent', seq: 1 },
      { id: 'a1', kind: 'assistant', text: '', seq: 2 },
    ];
    const merged = mergeChatWithLeaderDeltas(items, [
      { seq: 10, name: 'message.delta', data: { delta: 'Phase 1' } },
      { seq: 11, name: 'message.delta', data: { delta: ' 完成' } },
    ]);
    expect(merged.find((item) => item.kind === 'assistant')?.text).toBe('Phase 1 完成');
  });

  it('inserts assistant when only user message persisted', () => {
    const items: ChatItem[] = [{ id: 'u1', kind: 'user', text: 'hello', seq: 1 }];
    const merged = mergeChatWithLeaderDeltas(items, [
      { seq: 5, name: 'message.delta', data: { delta: 'streaming reply' } },
    ]);
    expect(merged).toHaveLength(2);
    expect(merged[1]).toMatchObject({ kind: 'assistant', text: 'streaming reply' });
  });

  it('interleaves leader turns around seq-bearing question cards', () => {
    // Regression: question cards persisted with their own seq (62/451) must anchor leader-turn
    // insertion so a turn at seq 200 lands between them instead of every turn dumping at the tail.
    const items: ChatItem[] = [
      { id: 'u1', kind: 'user', text: '深度调研', seq: 1 },
      {
        id: 'q1',
        kind: 'question',
        questionId: 'q1',
        question: '确认研究参数',
        options: [],
        allowFreeText: true,
        multiSelect: false,
        status: 'answered',
        seq: 62,
      },
      {
        id: 'q2',
        kind: 'question',
        questionId: 'q2',
        question: '确认报告大纲',
        options: [],
        allowFreeText: true,
        multiSelect: false,
        status: 'answered',
        seq: 451,
      },
    ];
    const merged = mergeChatWithLeaderDeltas(items, [
      { seq: 70, name: 'message.delta', data: { delta: '开始调研', messageId: 'm1' } },
      { seq: 460, name: 'message.delta', data: { delta: '生成报告', messageId: 'm2' } },
    ]);
    expect(merged.map((item) => item.id)).toEqual(['u1', 'q1', 'm1', 'q2', 'm2']);
  });

  it('splits a single turn around interleaved question cards', () => {
    // The leader streamed under one messageId across two confirmation questions (seq 37/50). The
    // folded bubble must split into pre-Q1 / between-Qs / post-Q2 segments so each card renders
    // between narration halves instead of after the whole bubble.
    const items: ChatItem[] = [
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
    const merged = mergeChatWithLeaderDeltas(items, [
      { seq: 5, name: 'message.delta', data: { delta: '开始前先确认。', messageId: 'm1' } },
      { seq: 40, name: 'message.delta', data: { delta: '收到模式。', messageId: 'm1' } },
      { seq: 60, name: 'message.delta', data: { delta: '参数确认完毕，建立团队。', messageId: 'm1' } },
    ]);
    expect(merged.map((item) => item.id)).toEqual(['u1', 'm1', 'q1', 'm1#1', 'q2', 'm1#2']);
    expect(merged.filter((i) => i.kind === 'assistant').map((i) => (i.kind === 'assistant' ? i.text : ''))).toEqual([
      '开始前先确认。',
      '收到模式。',
      '参数确认完毕，建立团队。',
    ]);
  });

  it('places per-turn bubbles in chronological seq order across rounds (unified seq)', () => {
    // Two rounds; with one shared per-session counter the round-2 user message (seq 30) sits
    // between round-1 and round-2 leader turns. Turns must insert by seq, not all at the tail.
    const items: ChatItem[] = [
      { id: 'u1', kind: 'user', text: '第一轮', seq: 1 },
      { id: 'u2', kind: 'user', text: '第二轮', seq: 30 },
    ];
    const merged = mergeChatWithLeaderDeltas(items, [
      { seq: 10, name: 'message.delta', data: { delta: '回答一', messageId: 'm1' } },
      { seq: 40, name: 'message.delta', data: { delta: '回答二', messageId: 'm2' } },
    ]);
    expect(merged.map((item) => item.id)).toEqual(['u1', 'm1', 'u2', 'm2']);
  });

  it('splits leader narration at orchestration tool.start so build_team precedes post-build text', () => {
    const items: ChatItem[] = [{ id: 'u1', kind: 'user', text: '深度调研', seq: 1 }];
    const merged = mergeChatWithLeaderDeltas(items, [
      { seq: 5, name: 'message.delta', data: { delta: '准备建立团队。', messageId: 'm1' } },
      { seq: 10, name: 'tool.start', data: { toolName: 'team.build_team', toolCallId: 'tb1' } },
      { seq: 11, name: 'tool.end', data: { toolName: 'team.build_team', toolCallId: 'tb1', result: { success: true } } },
      { seq: 15, name: 'message.delta', data: { delta: '团队已创建，开始 Phase 1。', messageId: 'm1' } },
    ]);
    expect(merged.map((item) => item.id)).toEqual(['u1', 'm1', 'm1#1']);
    expect(merged.filter((i) => i.kind === 'assistant').map((i) => (i.kind === 'assistant' ? i.text : ''))).toEqual([
      '准备建立团队。',
      '团队已创建，开始 Phase 1。',
    ]);
  });

  it('splits leader narration at team.build.completed when native build_team tool is absent', () => {
    const items: ChatItem[] = [{ id: 'u1', kind: 'user', text: '深度调研', seq: 1 }];
    const merged = mergeChatWithLeaderDeltas(items, [
      { seq: 60, name: 'message.delta', data: { delta: '现在建立团队并启动研究。', messageId: 'm1' } },
      { seq: 65, name: 'team.build.completed', data: { teamName: 'research-ai', displayName: '研究组' } },
      { seq: 66, name: 'message.delta', data: { delta: '团队已建立。启动 Phase 1。', messageId: 'm1' } },
    ]);
    expect(merged.map((item) => item.id)).toEqual(['u1', 'm1', 'm1#1']);
    expect(merged.filter((i) => i.kind === 'assistant').map((i) => (i.kind === 'assistant' ? i.text : ''))).toEqual([
      '现在建立团队并启动研究。',
      '团队已建立。启动 Phase 1。',
    ]);
  });
});

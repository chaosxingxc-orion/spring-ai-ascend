import { readFileSync, existsSync } from 'fs';
import { describe, expect, it } from 'vitest';
import type { ChatItem } from '../types/events';
import { parseChatItems } from './eventPayload';
import { projectChatTimeline } from './timelineProjector';
import type { RunEventRow } from './reasoningHydrate';

/** Regression fixture from session ad0f1e44 (build card after merged narration). */
describe('projectChatTimeline session ad0f1e44 fixture', () => {
  const messages: ChatItem[] = [
    { id: 'u1', kind: 'user', text: '调研', seq: 1 },
    {
      id: 'q1',
      kind: 'question',
      questionId: 'q1',
      question: '确认模式',
      options: [],
      allowFreeText: true,
      multiSelect: false,
      status: 'answered',
      seq: 35,
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
      seq: 49,
    },
    {
      id: '3fb23ed0-f98e-4c87-9fa1-86c2ddf4fb65',
      kind: 'assistant',
      text: '好的，参数已确认。现在建立团队并启动研究。团队已建立。现在进入 **Phase1：初始调研**',
      seq: 51,
    },
  ];

  const messageId = '3fb23ed0-f98e-4c87-9fa1-86c2ddf4fb65';
  const events: RunEventRow[] = [
    { seq: 52, name: 'message.delta', data: { delta: '好的，参数已确认。现在建立团队并启动研究。', messageId } },
    { seq: 65, name: 'team.build.completed', data: { teamName: 'gpt-researcher-team', displayName: 'research' } },
    { seq: 66, name: 'message.delta', data: { delta: '团队已建立。现在进入 **Phase1：初始调研**', messageId } },
    { seq: 75, name: 'team.phase.started', data: { phase: 1, label: '初始调研' } },
    { seq: 153, name: 'team.member.started', data: { memberId: 'topic-researcher', memberName: '谭溯源' } },
    { seq: 154, name: 'team.member.paused', data: { memberId: 'topic-researcher', memberName: '谭溯源' } },
  ];

  it('places build_team between pre-build and post-build assistant segments', () => {
    const timeline = projectChatTimeline(messages, events);
    const slice = timeline.filter((item) => item.kind !== 'user' && item.kind !== 'question');
    const labels = slice.map((item) => {
      if (item.kind === 'assistant') {
        return `a:${(item.text ?? '').slice(0, 24)}`;
      }
      if (item.kind === 'tool') {
        return `t:${item.toolName}`;
      }
      return item.kind;
    });
    const buildIdx = slice.findIndex((item) => item.kind === 'tool' && item.toolName.includes('build'));
    const postBuildIdx = slice.findIndex(
      (item) => item.kind === 'assistant' && (item.text ?? '').includes('团队已建立'),
    );
    expect(buildIdx).toBeGreaterThan(-1);
    expect(postBuildIdx).toBeGreaterThan(buildIdx);
    expect(labels).toMatchObject(
      expect.arrayContaining([
        expect.stringMatching(/^a:好的，参数已确认/),
        't:team.build_team',
        expect.stringMatching(/^a:团队已建立/),
      ]),
    );
  });

  it('projects live API snapshot when /tmp fixture files exist', () => {
    const msgPath = '/tmp/workmate-ad0f1e44-messages.json';
    const evPath = '/tmp/workmate-ad0f1e44-events.json';
    if (!existsSync(msgPath) || !existsSync(evPath)) {
      return;
    }
    const messages = parseChatItems(JSON.parse(readFileSync(msgPath, 'utf8')));
    const events: RunEventRow[] = JSON.parse(readFileSync(evPath, 'utf8')).map(
      (entry: { seq: number; name: string; data?: Record<string, unknown> }) => ({
        seq: entry.seq,
        name: entry.name,
        data: entry.data ?? {},
      }),
    );
    const timeline = projectChatTimeline(messages, events);
    const buildIdx = timeline.findIndex((item) => item.kind === 'tool' && item.toolName.includes('build_team'));
    const preBuild = timeline.find(
      (item) => item.kind === 'assistant' && (item.text ?? '').includes('现在建立团队') && !(item.text ?? '').includes('团队已建立'),
    );
    const postBuild = timeline.find(
      (item) => item.kind === 'assistant' && (item.text ?? '').includes('团队已建立'),
    );
    expect(buildIdx).toBeGreaterThan(-1);
    expect(preBuild).toBeTruthy();
    expect(postBuild).toBeTruthy();
    expect(timeline.indexOf(preBuild!)).toBeLessThan(buildIdx);
    expect(buildIdx).toBeLessThan(timeline.indexOf(postBuild!));
  });
});

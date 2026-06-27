import { describe, expect, it } from 'vitest';
import { memberSurfaceItemsFromRunEvents, mergeChatWithMemberSurfaceEvents, refreshChatMemberSurface, isMemberScopedChatItem } from './memberSurfaceHydrate';

describe('memberSurfaceItemsFromRunEvents', () => {
  it('projects member tool and assistant stream', () => {
    const items = memberSurfaceItemsFromRunEvents([
      {
        seq: 10,
        name: 'tool.start',
        data: {
          surface: 'team',
          memberId: 'topic-researcher',
          memberName: '谭溯源',
          parentRunId: 'run-1',
          toolName: 'workmate_read',
          toolCallId: 'tc-1',
          args: { path: 'a.md' },
        },
      },
      {
        seq: 11,
        name: 'message.delta',
        data: {
          surface: 'team',
          memberId: 'topic-researcher',
          memberName: '谭溯源',
          parentRunId: 'run-1',
          text: { preview: '调研摘要', bytes: 12 },
        },
      },
      {
        seq: 12,
        name: 'tool.end',
        data: {
          surface: 'team',
          memberId: 'topic-researcher',
          parentRunId: 'run-1',
          toolName: 'workmate_read',
          toolCallId: 'tc-1',
          result: { success: true },
        },
      },
    ]);
    expect(items.some((item) => item.kind === 'tool' && item.toolName === 'workmate_read')).toBe(true);
    expect(items.find((item) => item.kind === 'assistant')?.text).toBe('调研摘要');
  });

  it('projects member send_message reply tools (not leader delegation cards)', () => {
    const items = memberSurfaceItemsFromRunEvents([
      {
        seq: 30,
        name: 'tool.start',
        data: {
          surface: 'team',
          memberId: 'topic-researcher',
          memberName: '谭溯源',
          parentRunId: 'run-1',
          toolName: 'send_message',
          toolCallId: 'reply-1',
          args: { to: '__lead__', message: '调研结论摘要' },
        },
      },
      {
        seq: 31,
        name: 'tool.end',
        data: {
          surface: 'team',
          memberId: 'topic-researcher',
          parentRunId: 'run-1',
          toolName: 'send_message',
          toolCallId: 'reply-1',
          result: { success: true, data: { summary: '调研结论摘要' } },
        },
      },
    ]);
    const tool = items.find((item) => item.kind === 'tool' && item.toolName === 'send_message');
    expect(tool).toMatchObject({
      memberId: 'topic-researcher',
      status: 'success',
      toolCallId: 'reply-1',
    });
  });

  it('compacts adjacent completed/paused lifecycle markers', () => {
    const items = memberSurfaceItemsFromRunEvents([
      {
        seq: 21,
        name: 'team.member.completed',
        data: {
          surface: 'team',
          memberId: 'writer',
          memberName: '写手',
          parentRunId: 'run-1',
          summary: '初稿完成',
        },
      },
      {
        seq: 22,
        name: 'team.member.paused',
        data: {
          surface: 'team',
          memberId: 'writer',
          memberName: '写手',
          parentRunId: 'run-1',
        },
      },
    ]);
    const lifecycle = items.filter((item) => item.kind === 'reasoning');
    expect(lifecycle).toHaveLength(1);
    expect(lifecycle[0]?.text).toContain('已暂停');
  });

  it('does not project delegation send_message into member scope (leader-scope card)', () => {
    const items = memberSurfaceItemsFromRunEvents([
      {
        seq: 31,
        name: 'tool.start',
        data: {
          toolName: 'team.send_message',
          toolCallId: 'team-send-1',
          args: {
            memberId: { preview: 'topic-researcher' },
            memberName: { preview: '谭溯源' },
          },
        },
      },
      {
        seq: 32,
        name: 'tool.end',
        data: {
          toolName: 'team.send_message',
          toolCallId: 'team-send-1',
          result: {
            success: true,
            data: { memberId: 'topic-researcher', memberName: '谭溯源' },
          },
        },
      },
    ]);
    // Delegation cards belong to the leader timeline (teamRunEventHydrate), never the member surface.
    expect(items).toHaveLength(0);
  });
});

describe('mergeChatWithMemberSurfaceEvents', () => {
  it('inserts member items by seq', () => {
    const merged = mergeChatWithMemberSurfaceEvents(
      [{ id: 'u1', kind: 'user', text: 'hi', seq: 1 }],
      [
        {
          seq: 5,
          name: 'message.delta',
          data: {
            surface: 'team',
            memberId: 'topic-researcher',
            parentRunId: 'run-1',
            text: 'member reply',
          },
        },
      ],
    );
    expect(merged).toHaveLength(2);
    expect(merged[1]).toMatchObject({ kind: 'assistant', memberId: 'topic-researcher' });
  });

  it('upgrades existing tool card with member scope when ids match', () => {
    const merged = mergeChatWithMemberSurfaceEvents(
      [
        {
          id: 'tc-9',
          kind: 'tool',
          toolName: 'workmate_read',
          toolCallId: 'tc-9',
          status: 'success',
        },
      ],
      [
        {
          seq: 41,
          name: 'tool.start',
          data: {
            surface: 'team',
            memberId: 'topic-researcher',
            memberName: '谭溯源',
            parentRunId: 'run-1',
            toolName: 'workmate_read',
            toolCallId: 'tc-9',
            args: { path: 'a.md' },
          },
        },
      ],
    );
    expect(merged).toHaveLength(1);
    expect(merged[0]).toMatchObject({
      id: 'tc-9',
      kind: 'tool',
      memberId: 'topic-researcher',
      memberName: '谭溯源',
    });
  });

  it('keeps leader-scope delegation card unscoped (no member upgrade)', () => {
    const merged = mergeChatWithMemberSurfaceEvents(
      [
        {
          id: 'team-send-1',
          kind: 'tool',
          toolName: 'team.send_message',
          toolCallId: 'team-send-1',
          status: 'success',
        },
      ],
      [
        {
          seq: 41,
          name: 'tool.start',
          data: {
            toolName: 'team.send_message',
            toolCallId: 'team-send-1',
            args: {
              memberId: { preview: 'topic-researcher' },
              memberName: { preview: '谭溯源' },
            },
          },
        },
      ],
    );
    expect(merged).toHaveLength(1);
    expect(merged[0]).toMatchObject({ id: 'team-send-1', kind: 'tool' });
    expect((merged[0] as { memberId?: string }).memberId).toBeUndefined();
  });

  it('upserts growing member assistant text when id already exists', () => {
    const merged = mergeChatWithMemberSurfaceEvents(
      [
        {
          id: 'member-assistant-writer-10',
          kind: 'assistant',
          text: 'hel',
          memberId: 'writer',
        },
      ],
      [
        {
          seq: 10,
          name: 'message.delta',
          data: { surface: 'team', memberId: 'writer', parentRunId: 'run-1', text: 'hello' },
        },
      ],
    );
    expect(merged).toHaveLength(1);
    expect(merged[0]).toMatchObject({ kind: 'assistant', text: 'hello', memberId: 'writer' });
  });
});

describe('refreshChatMemberSurface', () => {
  it('replaces member-scoped rows while preserving leader timeline', () => {
    const refreshed = refreshChatMemberSurface(
      [
        { id: 'u1', kind: 'user', text: 'hi', seq: 1 },
        { id: 'leader-a', kind: 'assistant', text: 'leader reply', seq: 2 },
        { id: 'member-assistant-writer-5', kind: 'assistant', text: 'stale', memberId: 'writer', seq: 5 },
      ],
      [
        {
          seq: 5,
          name: 'message.delta',
          data: { surface: 'team', memberId: 'writer', parentRunId: 'run-1', text: 'fresh member text' },
        },
      ],
    );
    expect(refreshed.map((item) => item.id)).toEqual(['u1', 'leader-a', 'member-assistant-writer-5']);
    expect(refreshed[2]).toMatchObject({ kind: 'assistant', text: 'fresh member text', memberId: 'writer' });
    expect(isMemberScopedChatItem(refreshed[1]!)).toBe(false);
  });
});

import { describe, expect, it } from 'vitest';
import {
  countTurnArtifacts,
  countTurnChanges,
  buildLeaderTurnViews,
  indexLeaderTurnViews,
  isFollowUpUserTurn,
  lastTurnItems,
  leaderTurnForItem,
  userTurnIndexById,
} from './chatTurns';
import type { ChatItem } from '../types/events';

describe('chatTurns turn summary', () => {
  const items: ChatItem[] = [
    { id: 'u1', kind: 'user', text: 'first' },
    { id: 'a1', kind: 'assistant', text: 'ok' },
    { id: 'u2', kind: 'user', text: 'second' },
    {
      id: 't1',
      kind: 'tool',
      toolName: 'workmate_write',
      status: 'success',
      args: { path: 'out.md' },
    },
    {
      id: 'c1',
      kind: 'artifact-cta',
      path: 'outputs/index.html',
      name: 'index.html',
      mime: 'text/html',
    },
  ];

  it('scopes counts to latest turn', () => {
    expect(lastTurnItems(items)).toHaveLength(2);
    expect(countTurnChanges(items)).toBe(1);
    expect(countTurnArtifacts(items)).toBe(1);
  });

  it('indexes user turns for checkpoint divider', () => {
    const map = userTurnIndexById(items);
    expect(map.get('u1')).toBe(0);
    expect(map.get('u2')).toBe(1);
    expect(isFollowUpUserTurn(0)).toBe(false);
    expect(isFollowUpUserTurn(1)).toBe(true);
  });
});

describe('leader turn views', () => {
  it('merges leader reasoning within a user turn', () => {
    const turnItems: ChatItem[] = [
      { id: 'u1', kind: 'user', text: 'go' },
      { id: 'r1', kind: 'reasoning', text: 'step one' },
      { id: 't1', kind: 'tool', toolName: 'read', status: 'success' },
      { id: 'r2', kind: 'reasoning', text: 'step two' },
      { id: 'a1', kind: 'assistant', text: 'done' },
      { id: 'm1', kind: 'assistant', text: 'member', memberId: 'writer' },
    ];
    const views = buildLeaderTurnViews(turnItems);
    expect(views).toHaveLength(1);
    expect(views[0]?.reasoningText).toBe('step one\n\nstep two');
    expect(views[0]?.leaderReasoningItemIds.has('r1')).toBe(true);
    expect(views[0]?.leaderReasoningItemIds.has('r2')).toBe(true);
    expect(views[0]?.lastLeaderAssistantId).toBe('a1');
    expect(views[0]?.leaderScopedIds.has('m1')).toBe(false);
    expect(views[0]?.isLatestTurn).toBe(true);
  });

  it('resolves leader turn for synthetic tool-group ids', () => {
    const turnItems: ChatItem[] = [
      { id: 'u1', kind: 'user', text: 'go' },
      { id: 't1', kind: 'tool', toolName: 'read', status: 'success' },
      { id: 't2', kind: 'tool', toolName: 'read', status: 'success' },
      { id: 'a1', kind: 'assistant', text: 'done' },
    ];
    const { byItemId } = indexLeaderTurnViews(buildLeaderTurnViews(turnItems));
    const group: ChatItem = {
      id: 'tool-group-t1',
      kind: 'tool-group',
      tools: [
        { id: 't1', kind: 'tool', toolName: 'read', status: 'success' },
        { id: 't2', kind: 'tool', toolName: 'read', status: 'success' },
      ],
    };
    expect(leaderTurnForItem(group, byItemId)?.turnKey).toBe('u1');
    expect(leaderTurnForItem(group, byItemId)?.leaderScopedIds.has('t1')).toBe(true);
  });
});

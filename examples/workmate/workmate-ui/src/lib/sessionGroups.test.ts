import { describe, expect, it } from 'vitest';
import type { Session } from '../types/api';
import {
  groupSessionsByTime,
  nonEmptySessionGroups,
  organizeActiveSessions,
  listArchivedSessions,
  SESSION_TIME_GROUP_LABELS,
} from './sessionGroups';

function session(id: string, updatedAt: string): Session {
  return {
    id,
    title: id,
    workspaceRoot: '/tmp',
    status: 'CREATED',
    permissionMode: 'CRAFT',
    updatedAt,
    createdAt: updatedAt,
  };
}

describe('groupSessionsByTime', () => {
  it('groups sessions into today, yesterday, and earlier', () => {
    const now = new Date();
    const todayIso = now.toISOString();
    const yesterday = new Date(now);
    yesterday.setDate(yesterday.getDate() - 1);
    yesterday.setHours(12, 0, 0, 0);
    const weekAgo = new Date(now);
    weekAgo.setDate(weekAgo.getDate() - 5);

    const groups = groupSessionsByTime([
      session('t1', todayIso),
      session('y1', yesterday.toISOString()),
      session('e1', weekAgo.toISOString()),
    ]);

    expect(groups.today.map((s) => s.id)).toContain('t1');
    expect(groups.yesterday.map((s) => s.id)).toContain('y1');
    expect(groups.earlier.map((s) => s.id)).toContain('e1');
  });

  it('labels match S11 mockup', () => {
    expect(SESSION_TIME_GROUP_LABELS.today).toBe('今天');
    expect(SESSION_TIME_GROUP_LABELS.yesterday).toBe('昨天');
    expect(SESSION_TIME_GROUP_LABELS.earlier).toBe('更早');
  });

  it('nonEmptySessionGroups skips empty buckets', () => {
    const groups = groupSessionsByTime([session('t1', new Date().toISOString())]);
    const visible = nonEmptySessionGroups(groups);
    expect(visible.length).toBe(1);
    expect(visible[0].key).toBe('today');
  });

  it('organizeActiveSessions splits pinned and archived', () => {
    const now = new Date().toISOString();
    const organized = organizeActiveSessions([
      { ...session('p1', now), pinned: true },
      { ...session('a1', now), archivedAt: now },
      session('t1', now),
    ]);
    expect(organized.pinned.map((item) => item.id)).toEqual(['p1']);
    expect(organized.groups[0]?.sessions.map((item) => item.id)).toEqual(['t1']);
    expect(listArchivedSessions([
      { ...session('p1', now), pinned: true },
      { ...session('a1', now), archivedAt: now },
      session('t1', now),
    ]).map((item) => item.id)).toEqual(['a1']);
  });
});

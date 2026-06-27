import { describe, expect, it } from 'vitest';
import type { Session } from '../types/api';
import {
  countActiveSessions,
  isAtSessionLimit,
  isAutoArchiveEnabled,
  isNearSessionLimit,
  pickArchiveCandidates,
  sessionsOverLimit,
} from './sessionLimits';

function session(id: string, archivedAt?: string | null, updatedAt?: string, pinned?: boolean): Session {
  return {
    id,
    title: id,
    workspaceRoot: '/tmp',
    status: 'CREATED',
    permissionMode: 'CRAFT',
    createdAt: '',
    updatedAt: updatedAt ?? '2026-06-01T00:00:00Z',
    archivedAt: archivedAt ?? null,
    pinned: pinned ?? false,
  };
}

describe('sessionLimits', () => {
  it('counts only non-archived sessions', () => {
    expect(
      countActiveSessions([session('a'), session('b', '2026-01-01'), session('c')]),
    ).toBe(2);
  });

  it('detects when limit is reached', () => {
    expect(isAtSessionLimit(49, 50)).toBe(false);
    expect(isAtSessionLimit(50, 50)).toBe(true);
  });

  it('computes sessions over limit', () => {
    expect(sessionsOverLimit(49, 50)).toBe(0);
    expect(sessionsOverLimit(50, 50)).toBe(1);
    expect(sessionsOverLimit(57, 50)).toBe(8);
  });

  it('detects near limit within buffer', () => {
    expect(isNearSessionLimit(44, 50)).toBe(false);
    expect(isNearSessionLimit(45, 50)).toBe(true);
    expect(isNearSessionLimit(50, 50)).toBe(true);
  });

  it('picks oldest unpinned active sessions as archive candidates', () => {
    const candidates = pickArchiveCandidates(
      [
        session('pinned', null, '2026-01-01T00:00:00Z', true),
        session('old', null, '2026-01-01T00:00:00Z'),
        session('new', null, '2026-06-01T00:00:00Z'),
        session('archived', '2026-05-01T00:00:00Z', '2026-05-01T00:00:00Z'),
      ],
      2,
    );
    expect(candidates.map((item) => item.id)).toEqual(['old', 'new']);
  });

  it('combines user preference with server policy', () => {
    expect(isAutoArchiveEnabled(true, { autoArchiveOnCreate: true })).toBe(true);
    expect(isAutoArchiveEnabled(true, { autoArchiveOnCreate: false })).toBe(false);
    expect(isAutoArchiveEnabled(false, { autoArchiveOnCreate: true })).toBe(false);
  });
});

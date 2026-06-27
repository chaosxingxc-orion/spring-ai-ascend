import type { Session } from '../types/api';

export type SessionTimeGroup = 'today' | 'yesterday' | 'earlier';

export const SESSION_TIME_GROUP_LABELS: Record<SessionTimeGroup, string> = {
  today: '今天',
  yesterday: '昨天',
  earlier: '更早',
};

export const SESSION_TIME_GROUP_ORDER: SessionTimeGroup[] = ['today', 'yesterday', 'earlier'];

function startOfLocalDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function sortByUpdatedDesc(a: Session, b: Session): number {
  return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
}

export function isArchivedSession(session: Session): boolean {
  return Boolean(session.archivedAt);
}

/** 对标 S11 侧栏：今天 / 昨天 / 更早（不含置顶区）。 */
export function groupSessionsByTime(sessions: Session[]): Record<SessionTimeGroup, Session[]> {
  const now = new Date();
  const startToday = startOfLocalDay(now);
  const startYesterday = new Date(startToday);
  startYesterday.setDate(startYesterday.getDate() - 1);

  const groups: Record<SessionTimeGroup, Session[]> = {
    today: [],
    yesterday: [],
    earlier: [],
  };

  const sorted = [...sessions].sort(sortByUpdatedDesc);

  for (const session of sorted) {
    const updated = new Date(session.updatedAt);
    if (updated >= startToday) {
      groups.today.push(session);
    } else if (updated >= startYesterday) {
      groups.yesterday.push(session);
    } else {
      groups.earlier.push(session);
    }
  }

  return groups;
}

export function nonEmptySessionGroups(
  groups: Record<SessionTimeGroup, Session[]>,
): Array<{ key: SessionTimeGroup; sessions: Session[] }> {
  return SESSION_TIME_GROUP_ORDER
    .map((key) => ({ key, sessions: groups[key] }))
    .filter((group) => group.sessions.length > 0);
}

export interface OrganizedActiveSessions {
  pinned: Session[];
  groups: Array<{ key: SessionTimeGroup; sessions: Session[] }>;
}

/** F4 — active sessions split into pinned + time buckets. */
export function organizeActiveSessions(sessions: Session[]): OrganizedActiveSessions {
  const active = sessions.filter((session) => !isArchivedSession(session));
  const pinned = active.filter((session) => session.pinned).sort(sortByUpdatedDesc);
  const unpinned = active.filter((session) => !session.pinned);
  return {
    pinned,
    groups: nonEmptySessionGroups(groupSessionsByTime(unpinned)),
  };
}

export function listArchivedSessions(sessions: Session[]): Session[] {
  return sessions.filter(isArchivedSession).sort(sortByUpdatedDesc);
}

export function countActiveSessions(sessions: Session[]): number {
  return sessions.filter((session) => !isArchivedSession(session)).length;
}

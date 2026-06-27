import type { Session } from '../types/api';
import type { SessionLimits } from '../types/api';

export function countActiveSessions(sessions: Session[]): number {
  return sessions.filter((session) => !session.archivedAt).length;
}

export function isAtSessionLimit(activeCount: number, maxActive: number): boolean {
  return activeCount >= maxActive;
}

/** How many active sessions must be archived before a new one can be created. */
export function sessionsOverLimit(activeCount: number, maxActive: number): number {
  if (activeCount < maxActive) {
    return 0;
  }
  return activeCount - maxActive + 1;
}

export function isNearSessionLimit(activeCount: number, maxActive: number, buffer = 5): boolean {
  return activeCount >= maxActive - buffer;
}

/** Client preference AND server policy both allow LRU auto-archive. */
export function isAutoArchiveEnabled(
  userPref: boolean,
  limits?: Pick<SessionLimits, 'autoArchiveOnCreate'> | null,
): boolean {
  if (!userPref) {
    return false;
  }
  return limits?.autoArchiveOnCreate !== false;
}

/** Oldest non-pinned active sessions — sensible archive suggestions. */
export function pickArchiveCandidates(sessions: Session[], limit: number): Session[] {
  return sessions
    .filter((session) => !session.archivedAt && !session.pinned)
    .sort((a, b) => new Date(a.updatedAt).getTime() - new Date(b.updatedAt).getTime())
    .slice(0, limit);
}

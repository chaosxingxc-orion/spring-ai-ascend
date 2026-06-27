import type { Expert } from '../types/api';
import { resolveExpertDisplayName } from './teamUiLabels';

const MAX_RECENT = 24;
const RECENT_AGENT_KEY = 'workmate.recentAgentExpertIds';
const RECENT_TEAM_KEY = 'workmate.recentTeamExpertIds';

function loadRecentIds(key: string): string[] {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter((id): id is string => typeof id === 'string' && id.length > 0);
  } catch {
    return [];
  }
}

function touchRecentId(key: string, expertId: string): string[] {
  const trimmed = expertId.trim();
  if (!trimmed) {
    return loadRecentIds(key);
  }
  const next = [trimmed, ...loadRecentIds(key).filter((id) => id !== trimmed)].slice(0, MAX_RECENT);
  try {
    localStorage.setItem(key, JSON.stringify(next));
  } catch {
    // ignore quota / private mode
  }
  return next;
}

export function loadRecentAgentExpertIds(): string[] {
  return loadRecentIds(RECENT_AGENT_KEY);
}

export function touchRecentAgentExpertId(expertId: string): string[] {
  return touchRecentId(RECENT_AGENT_KEY, expertId);
}

export function loadRecentTeamExpertIds(): string[] {
  return loadRecentIds(RECENT_TEAM_KEY);
}

export function touchRecentTeamExpertId(expertId: string): string[] {
  return touchRecentId(RECENT_TEAM_KEY, expertId);
}

/**
 * Popover list order: current selection first, then recently used, then display name.
 */
export function sortExpertsForPopover(
  experts: Expert[],
  selectedExpertId: string,
  recentIds: string[],
): Expert[] {
  const recentRank = new Map<string, number>();
  recentIds.forEach((id, index) => recentRank.set(id, index));

  return [...experts].sort((a, b) => {
    const aSelected = a.id === selectedExpertId;
    const bSelected = b.id === selectedExpertId;
    if (aSelected !== bSelected) {
      return aSelected ? -1 : 1;
    }

    const aRecent = recentRank.has(a.id);
    const bRecent = recentRank.has(b.id);
    if (aRecent !== bRecent) {
      return aRecent ? -1 : 1;
    }
    if (aRecent && bRecent) {
      const rankDiff = (recentRank.get(a.id) ?? 0) - (recentRank.get(b.id) ?? 0);
      if (rankDiff !== 0) {
        return rankDiff;
      }
    }

    return resolveExpertDisplayName(a).localeCompare(resolveExpertDisplayName(b), 'zh-CN');
  });
}

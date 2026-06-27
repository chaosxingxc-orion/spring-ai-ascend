import type { SkillInfo } from '../types/market';

const RECENT_KEY = 'workmate.skillRecentIds';
const MAX_RECENT = 24;

export function loadRecentSkillIds(): string[] {
  try {
    const raw = localStorage.getItem(RECENT_KEY);
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

export function touchRecentSkillId(skillId: string): string[] {
  const trimmed = skillId.trim();
  if (!trimmed) {
    return loadRecentSkillIds();
  }
  const next = [trimmed, ...loadRecentSkillIds().filter((id) => id !== trimmed)].slice(0, MAX_RECENT);
  try {
    localStorage.setItem(RECENT_KEY, JSON.stringify(next));
  } catch {
    // ignore quota / private mode
  }
  return next;
}

export function sortSkillsForPopover(
  skills: SkillInfo[],
  enabledIds: string[],
  recentIds: string[],
): SkillInfo[] {
  const enabledRank = new Map<string, number>();
  [...enabledIds].reverse().forEach((id, index) => enabledRank.set(id, index));

  const recentRank = new Map<string, number>();
  recentIds.forEach((id, index) => recentRank.set(id, index));

  return [...skills].sort((a, b) => {
    const aEnabled = enabledIds.includes(a.id);
    const bEnabled = enabledIds.includes(b.id);
    if (aEnabled !== bEnabled) {
      return aEnabled ? -1 : 1;
    }

    if (aEnabled && bEnabled) {
      const rankDiff = (enabledRank.get(a.id) ?? 999) - (enabledRank.get(b.id) ?? 999);
      if (rankDiff !== 0) {
        return rankDiff;
      }
    }

    const aRecent = recentRank.get(a.id);
    const bRecent = recentRank.get(b.id);
    if (aRecent != null || bRecent != null) {
      if (aRecent == null) {
        return 1;
      }
      if (bRecent == null) {
        return -1;
      }
      if (aRecent !== bRecent) {
        return aRecent - bRecent;
      }
    }

    if (a.installed !== b.installed) {
      return a.installed ? -1 : 1;
    }

    return a.name.localeCompare(b.name, 'zh-CN');
  });
}

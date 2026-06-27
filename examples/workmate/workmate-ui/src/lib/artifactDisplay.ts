/** Friendly labels and helpers for session workspace artifacts. */

export function isTeamBlackboardPath(path: string): boolean {
  return path.startsWith('team/') && /\/blackboard\.md$/i.test(path);
}

export function findTeamBlackboardPath(paths: readonly string[]): string | null {
  const matches = paths.filter(isTeamBlackboardPath);
  if (matches.length === 0) {
    return null;
  }
  return [...matches].sort((a, b) => b.length - a.length)[0] ?? null;
}

export function artifactDisplayName(path: string, fallbackName?: string): string {
  if (isTeamBlackboardPath(path)) {
    return '团队黑板';
  }
  if (path.endsWith('blackboard.meta.json')) {
    return '团队黑板元数据';
  }
  return fallbackName ?? path.split('/').pop() ?? path;
}

import type { Expert, Session } from '../types/api';
import type { WorkspacePreset } from '../types/workspace';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isDefaultSessionWorkspaceKey(workspaceKey: string): boolean {
  return workspaceKey === '' || UUID_PATTERN.test(workspaceKey);
}

export function workspaceLabelForSession(
  session: Session,
  presets: WorkspacePreset[],
): string {
  const key = session.workspaceKey ?? '';
  const preset = presets.find((item) => item.path === key);
  if (preset) {
    return preset.name;
  }
  if (isDefaultSessionWorkspaceKey(key)) {
    return presets.find((item) => item.path === '')?.name ?? '默认工作空间';
  }
  const segments = key.split('/');
  return segments[segments.length - 1] || key;
}

export function filterSessions(
  sessions: Session[],
  experts: Expert[],
  query: string,
): Session[] {
  const q = query.trim().toLowerCase();
  if (!q) {
    return sessions;
  }
  return sessions.filter((session) => {
    const expertName = experts.find((e) => e.id === session.expertId)?.name ?? '';
    return (
      session.title.toLowerCase().includes(q) ||
      expertName.toLowerCase().includes(q) ||
      (session.workspaceKey ?? '').toLowerCase().includes(q)
    );
  });
}

export function groupSessionsByWorkspace(
  sessions: Session[],
  presets: WorkspacePreset[],
): Array<{ preset: WorkspacePreset; sessions: Session[] }> {
  const sharedPresets = presets.filter((preset) => preset.path !== '');
  return sharedPresets
    .map((preset) => ({
      preset,
      sessions: sessions.filter((session) => session.workspaceKey === preset.path),
    }))
    .filter((group) => group.sessions.length > 0);
}

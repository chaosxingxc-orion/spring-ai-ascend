import type { MarketTab } from '../types/market';
import type { ExpertMarketKind } from './expertMarketFilter';

export const NEW_TASK_PATH = '/new';
export const MY_FILES_PATH = '/files';
export const ASSISTANT_PATH = '/assistant';
export const PROJECTS_PATH = '/projects';
export const AUTOMATION_PATH = '/automation';
export const MORE_PATH = '/more';
export const AUDIT_LOG_PATH = '/security/audit';
export const DEV_STUDIO_PATH = '/dev';
export const DEV_AGENTS_PATH = '/dev/agents';
export const DEV_TEAMS_PATH = '/dev/teams';
export const DEV_SKILLS_PATH = '/dev/skills';
export const DEV_WELCOME_PATH = '/dev/welcome';
export const DEV_PLAYBOOKS_PATH = '/dev/playbooks';
export const DEV_RUNTIME_PATH = '/dev/runtime';
export const SETTINGS_PATH = '/settings';
/** @deprecated use settingsPath('memory') */
export const MEMORY_SETTINGS_PATH = '/settings/memory';

export function settingsPath(section: 'general' | 'memory' | 'security' | 'data' | 'quota' | 'about' = 'general'): string {
  if (section === 'general') {
    return SETTINGS_PATH;
  }
  return `${SETTINGS_PATH}/${section}`;
}

export const MEMORY_CONTENT_HASH = 'memory-content';

/** Deep-link to the saved memory preview block in settings. */
export function settingsMemoryContentPath(): string {
  return `${settingsPath('memory')}#${MEMORY_CONTENT_HASH}`;
}

export function sessionPath(sessionId: string): string {
  return `/s/${sessionId}`;
}

export function sharePath(token: string): string {
  return `/share/${token}`;
}

export function marketPath(
  tab: MarketTab,
  query?: string,
  options?: { connectorId?: string; kind?: ExpertMarketKind },
): string {
  const params = new URLSearchParams();
  if (query?.trim()) {
    params.set('q', query.trim());
  }
  if (options?.connectorId?.trim()) {
    params.set('connector', options.connectorId.trim());
  }
  if (options?.kind && options.kind !== 'all') {
    params.set('kind', options.kind);
  }
  const qs = params.toString();
  const base = `/market/${tab}`;
  return qs ? `${base}?${qs}` : base;
}

export function parseMarketTab(tab: string | undefined): MarketTab {
  if (tab === 'skills' || tab === 'connectors') {
    return tab;
  }
  if (tab === 'plugins') {
    return 'skills';
  }
  return 'experts';
}

/** Legacy G24 route — redirect to skills tab in the router shell. */
export function isLegacyPluginsMarketPath(pathname: string): boolean {
  return pathname === '/market/plugins' || pathname.startsWith('/market/plugins/');
}

export function isMarketPath(pathname: string): boolean {
  return pathname.startsWith('/market/');
}

export function marketTabFromPathname(pathname: string): MarketTab {
  if (!isMarketPath(pathname)) {
    return 'experts';
  }
  const segment = pathname.slice('/market/'.length).split('/')[0]?.split('?')[0];
  return parseMarketTab(segment);
}

/** 侧栏 Nav 占位壳层路由（非会话主区） */
export function isSidebarNavShellPath(pathname: string): boolean {
  return (
    pathname === ASSISTANT_PATH ||
    pathname === PROJECTS_PATH ||
    pathname === AUTOMATION_PATH ||
    pathname === MORE_PATH
  );
}

export function isDevStudioPath(pathname: string): boolean {
  return pathname === DEV_STUDIO_PATH || pathname.startsWith(`${DEV_STUDIO_PATH}/`);
}

export function devAgentEditorPath(expertId: string): string {
  return `${DEV_AGENTS_PATH}/${expertId}`;
}

export function devSkillEditorPath(skillId: string): string {
  return `${DEV_SKILLS_PATH}/${skillId}`;
}

export function devTeamEditorPath(teamId: string): string {
  return `${DEV_TEAMS_PATH}/${teamId}`;
}

export function devPlaybookEditorPath(playbookId: string): string {
  return `${DEV_PLAYBOOKS_PATH}/${playbookId}`;
}

export type DevStudioSection = 'agents' | 'teams' | 'skills' | 'welcome' | 'playbooks' | 'runtime';

export function parseDevStudioSection(pathname: string): DevStudioSection {
  if (pathname.startsWith(DEV_RUNTIME_PATH)) {
    return 'runtime';
  }
  if (pathname.startsWith(DEV_WELCOME_PATH)) {
    return 'welcome';
  }
  if (pathname.startsWith(DEV_PLAYBOOKS_PATH)) {
    return 'playbooks';
  }
  if (pathname.startsWith(DEV_SKILLS_PATH)) {
    return 'skills';
  }
  if (pathname.startsWith(DEV_TEAMS_PATH)) {
    return 'teams';
  }
  return 'agents';
}

export function parseDevAgentId(pathname: string): string | null {
  if (!pathname.startsWith(`${DEV_AGENTS_PATH}/`)) {
    return null;
  }
  const id = pathname.slice(`${DEV_AGENTS_PATH}/`.length).split('/')[0];
  return id || null;
}

export function parseDevSkillId(pathname: string): string | null {
  if (!pathname.startsWith(`${DEV_SKILLS_PATH}/`)) {
    return null;
  }
  const id = pathname.slice(`${DEV_SKILLS_PATH}/`.length).split('/')[0];
  return id || null;
}

export function parseDevTeamId(pathname: string): string | null {
  if (!pathname.startsWith(`${DEV_TEAMS_PATH}/`)) {
    return null;
  }
  const id = pathname.slice(`${DEV_TEAMS_PATH}/`.length).split('/')[0];
  return id || null;
}

export function parseDevPlaybookId(pathname: string): string | null {
  if (!pathname.startsWith(`${DEV_PLAYBOOKS_PATH}/`)) {
    return null;
  }
  const id = pathname.slice(`${DEV_PLAYBOOKS_PATH}/`.length).split('/')[0];
  return id || null;
}

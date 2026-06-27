import { TERM } from '../../lib/terminology';
import {
  ASSISTANT_PATH,
  AUTOMATION_PATH,
  MORE_PATH,
  MY_FILES_PATH,
  PROJECTS_PATH,
  marketPath,
} from '../../lib/paths';

export type SidebarNavId =
  | 'assistant'
  | 'project'
  | 'experts'
  | 'files'
  | 'automation'
  | 'more';

export interface SidebarNavItem {
  id: SidebarNavId;
  label: string;
  sub: string;
  icon: string;
  path: string;
  /** 未落地能力侧栏灰显、不可点击 */
  implemented?: boolean;
}

/** 对标 hi-fi 侧栏主导航；`implemented: false` 的入口灰显 */
export const SIDEBAR_NAV_ITEMS: SidebarNavItem[] = [
  { id: 'assistant', label: '助理', sub: 'Claw', icon: '💬', path: ASSISTANT_PATH, implemented: false },
  { id: 'project', label: '项目', sub: '团队协作', icon: '📁', path: PROJECTS_PATH, implemented: false },
  {
    id: 'experts',
    label: TERM.expert,
    sub: `${TERM.skill} / ${TERM.runtimeMcp}`,
    icon: '👤',
    path: marketPath('experts'),
  },
  { id: 'files', label: '文件', sub: '跨任务中心', icon: '📂', path: MY_FILES_PATH, implemented: false },
  { id: 'automation', label: '自动化', sub: '定时任务', icon: '⏰', path: AUTOMATION_PATH, implemented: false },
  { id: 'more', label: '更多', sub: '资料库 · 灵感', icon: '⊞', path: MORE_PATH, implemented: false },
];

export function resolveSidebarNavId(pathname: string): SidebarNavId | null {
  if (pathname === ASSISTANT_PATH) {
    return 'assistant';
  }
  if (pathname === PROJECTS_PATH) {
    return 'project';
  }
  if (pathname === AUTOMATION_PATH) {
    return 'automation';
  }
  if (pathname === MORE_PATH) {
    return 'more';
  }
  if (pathname === MY_FILES_PATH) {
    return 'files';
  }
  if (pathname.startsWith('/market/')) {
    return 'experts';
  }
  return null;
}

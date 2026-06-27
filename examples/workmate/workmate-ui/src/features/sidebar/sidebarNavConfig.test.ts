import { describe, expect, it } from 'vitest';
import { resolveSidebarNavId, SIDEBAR_NAV_ITEMS } from './sidebarNavConfig';
import {
  ASSISTANT_PATH,
  AUTOMATION_PATH,
  MORE_PATH,
  MY_FILES_PATH,
  NEW_TASK_PATH,
  PROJECTS_PATH,
  marketPath,
} from '../../lib/paths';

describe('resolveSidebarNavId', () => {
  it('maps shell and market routes', () => {
    expect(resolveSidebarNavId(ASSISTANT_PATH)).toBe('assistant');
    expect(resolveSidebarNavId(PROJECTS_PATH)).toBe('project');
    expect(resolveSidebarNavId(AUTOMATION_PATH)).toBe('automation');
    expect(resolveSidebarNavId(MORE_PATH)).toBe('more');
    expect(resolveSidebarNavId(MY_FILES_PATH)).toBe('files');
    expect(resolveSidebarNavId(marketPath('experts'))).toBe('experts');
    expect(resolveSidebarNavId(marketPath('skills'))).toBe('experts');
    expect(resolveSidebarNavId('/s/abc')).toBeNull();
    expect(resolveSidebarNavId(NEW_TASK_PATH)).toBeNull();
  });

  it('only experts nav is implemented in v0.3', () => {
    const implemented = SIDEBAR_NAV_ITEMS.filter((item) => item.implemented !== false);
    expect(implemented.map((item) => item.id)).toEqual(['experts']);
  });
});

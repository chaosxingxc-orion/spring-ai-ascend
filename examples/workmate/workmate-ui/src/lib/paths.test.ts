import { describe, expect, it } from 'vitest';
import {
  NEW_TASK_PATH,
  DEV_AGENTS_PATH,
  DEV_STUDIO_PATH,
  DEV_WELCOME_PATH,
  isDevStudioPath,
  isMarketPath,
  isLegacyPluginsMarketPath,
  marketPath,
  marketTabFromPathname,
  parseDevAgentId,
  parseDevSkillId,
  parseDevStudioSection,
  parseDevTeamId,
  devTeamEditorPath,
} from './paths';

describe('market path helpers', () => {
  it('detects market routes from pathname', () => {
    expect(isMarketPath(marketPath('experts'))).toBe(true);
    expect(isMarketPath(marketPath('skills'))).toBe(true);
    expect(isMarketPath(NEW_TASK_PATH)).toBe(false);
  });

  it('parses market tab segment from pathname', () => {
    expect(marketTabFromPathname('/market/experts')).toBe('experts');
    expect(marketTabFromPathname('/market/skills')).toBe('skills');
    expect(marketTabFromPathname('/market/connectors')).toBe('connectors');
    expect(marketTabFromPathname('/market/plugins')).toBe('skills');
    expect(marketTabFromPathname(NEW_TASK_PATH)).toBe('experts');
  });

  it('detects legacy plugins market path', () => {
    expect(isLegacyPluginsMarketPath('/market/plugins')).toBe(true);
    expect(isLegacyPluginsMarketPath('/market/skills')).toBe(false);
  });
});

describe('dev studio path helpers', () => {
  it('detects dev studio routes', () => {
    expect(isDevStudioPath(DEV_STUDIO_PATH)).toBe(true);
    expect(isDevStudioPath(DEV_AGENTS_PATH)).toBe(true);
    expect(isDevStudioPath('/dev/skills/web-access')).toBe(true);
    expect(isDevStudioPath(DEV_WELCOME_PATH)).toBe(true);
    expect(isDevStudioPath(NEW_TASK_PATH)).toBe(false);
  });

  it('parses dev studio section and ids', () => {
    expect(parseDevStudioSection('/dev/agents')).toBe('agents');
    expect(parseDevStudioSection('/dev/teams')).toBe('teams');
    expect(parseDevStudioSection('/dev/skills')).toBe('skills');
    expect(parseDevStudioSection('/dev/welcome')).toBe('welcome');
    expect(parseDevStudioSection('/dev/playbooks')).toBe('playbooks');
    expect(parseDevStudioSection('/dev/runtime')).toBe('runtime');
    expect(parseDevAgentId('/dev/agents/fund-analyst')).toBe('fund-analyst');
    expect(parseDevAgentId('/dev/agents')).toBeNull();
    expect(parseDevTeamId('/dev/teams/stock-partner-team')).toBe('stock-partner-team');
    expect(parseDevTeamId('/dev/teams')).toBeNull();
    expect(devTeamEditorPath('demo-team')).toBe('/dev/teams/demo-team');
    expect(parseDevSkillId('/dev/skills/web-access')).toBe('web-access');
  });
});

import { describe, expect, it } from 'vitest';
import { chipPrompt, heroDisplayText, heroUsesSplitBrand } from '../types/welcome';
import { TERM, expertRuntimeType, isTeamExpertType } from './terminology';
import { UI_ALIGNMENT } from './uiAlignment';

describe('terminology', () => {
  it('maps product labels to runtime types', () => {
    expect(TERM.expert).toBe(UI_ALIGNMENT.terminology.expert);
    expect(TERM.connectApps).toBe(UI_ALIGNMENT.dockToolbar.connectApps);
    expect(expertRuntimeType('solo')).toBe('agent');
    expect(isTeamExpertType('team')).toBe(true);
    expect(expertRuntimeType('team')).toBe('multi-agent');
  });
});

describe('uiAlignment screens', () => {
  it('points S01/S06 to office welcome config', () => {
    expect(UI_ALIGNMENT.screens.S01.configSource).toBe('office/welcome.yaml');
    expect(UI_ALIGNMENT.screens.S01.welcomeApi).toBe('/api/v1/welcome');
    expect(UI_ALIGNMENT.screens.S06.playbooksApi).toContain('market-featured');
  });
});

describe('welcome S01 hero', () => {
  it('uses headline for WorkMate mockup', () => {
    expect(
      heroDisplayText({
        hero: { headline: 'WorkMate，我帮你', title: 'WorkMate', tagline: '我帮你' },
        dock: {},
        growthPlan: { enabled: false },
        bestPractices: { placement: '', enabled: false, playbooks: [] },
        marketFeatured: { placement: '', enabled: true, playbooks: [] },
        homeFeatured: { enabled: false },
        scenes: [],
      }),
    ).toBe('WorkMate，我帮你');
  });

  it('uses split brand when title and tagline are set', () => {
    const config = {
      hero: { headline: 'WorkMate，我帮你', title: 'WorkMate', tagline: '我帮你' },
      dock: {},
      growthPlan: { enabled: false },
      bestPractices: { placement: '', enabled: false, playbooks: [] },
      marketFeatured: { placement: '', enabled: true, playbooks: [] },
      homeFeatured: { enabled: false },
      scenes: [],
    };
    expect(heroUsesSplitBrand(config)).toBe(true);
  });
});

describe('welcome chip prompts', () => {
  it('uses custom initPrompt when provided', () => {
    expect(chipPrompt({ label: '文档处理', initPrompt: '精读研报' })).toBe('精读研报');
  });

  it('falls back to label-based seed', () => {
    expect(chipPrompt({ label: '总结要点' })).toBe('帮我完成：总结要点');
  });
});

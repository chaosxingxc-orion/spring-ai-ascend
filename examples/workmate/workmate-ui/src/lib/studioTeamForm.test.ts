import { describe, expect, it } from 'vitest';
import { emptyTeamForm, patternHasLead, patternImpactHint, teamViewToForm } from './studioTeamForm';
import type { StudioTeamView } from '../types/studio';

describe('studioTeamForm', () => {
  it('detects lead-bearing patterns', () => {
    expect(patternHasLead('orchestrator')).toBe(true);
    expect(patternHasLead('pipeline')).toBe(false);
  });

  it('describes runtime impact', () => {
    expect(patternImpactHint('orchestrator', 'openjiuwen-team')).toContain('TeamAgent');
    expect(patternImpactHint('pipeline', 'workmate-orchestrator')).toContain('无中心主理人');
  });

  it('creates empty team with two members', () => {
    const form = emptyTeamForm('demo-team');
    expect(form.members).toHaveLength(2);
    expect(form.members[0].expertId).toBe('demo-team__member-a');
  });

  it('maps member prompt content from team view', () => {
    const view: StudioTeamView = {
      team: {
        summary: {
          id: 'demo-team',
          name: 'Demo Team',
          description: 'Desc',
          expertType: 'team',
          tags: [],
          skillCompatibility: [],
        },
        promptFile: 'lead-prompt.md',
        promptContent: 'Lead prompt body',
        expertYaml: '',
        source: 'MARKET',
        sourceDir: '/tmp',
      },
      members: [
        {
          member: {
            id: 'member-a',
            name: 'Member A',
            expertId: 'demo-team__member-a',
          },
          expertResolved: true,
          expertSource: 'MARKET',
          promptFile: 'prompt.md',
          promptContent: 'Member A prompt\n',
          expertYaml: 'id: demo-team__member-a',
        },
      ],
      runtimePreview: {
        resolvedRuntime: 'openjiuwen-team',
        coordinationPattern: 'orchestrator',
        migratablePattern: true,
        hasLead: true,
        hint: 'hint',
      },
      warnings: [],
    };

    const form = teamViewToForm(view);
    expect(form.promptContent).toBe('Lead prompt body');
    expect(form.members[0].promptContent).toBe('Member A prompt');
    expect(form.members[0].expertYaml).toBe('id: demo-team__member-a');
  });
});

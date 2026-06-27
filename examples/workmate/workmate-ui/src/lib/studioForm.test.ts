import { describe, expect, it } from 'vitest';
import { expertSourceToForm, isNewStudioId, joinCommaList, normalizeExpertWriteBody, parseCommaList, sourceLabel } from './studioForm';
import type { StudioExpertSource } from '../types/studio';

describe('studioForm helpers', () => {
  it('parses comma and newline separated lists', () => {
    expect(parseCommaList('a, b\nc')).toEqual(['a', 'b', 'c']);
    expect(parseCommaList('')).toEqual([]);
  });

  it('joins list items', () => {
    expect(joinCommaList(['qieman', 'web'])).toBe('qieman, web');
  });

  it('labels asset sources', () => {
    expect(sourceLabel('DRAFT')).toBe('草稿');
    expect(sourceLabel('BUILTIN')).toBe('内置');
  });

  it('detects new editor route id', () => {
    expect(isNewStudioId('new')).toBe(true);
    expect(isNewStudioId('fund-analyst')).toBe(false);
  });

  it('maps expert source to write form', () => {
    const source: StudioExpertSource = {
      summary: {
        id: 'fund-analyst',
        name: 'Fund',
        description: 'Desc',
        expertType: 'agent',
        tags: ['qieman'],
        skillCompatibility: ['qieman'],
        quickPrompts: [],
      },
      promptFile: 'prompt.md',
      promptContent: 'Prompt body\n',
      expertYaml: 'id: fund-analyst',
      source: 'BUILTIN',
      sourceDir: '/office/experts/fund-analyst',
    };
    const form = expertSourceToForm(source);
    expect(form.promptContent).toBe('Prompt body');
    expect(form.skillCompatibility).toEqual(['qieman']);
  });

  it('keeps expert id in validate payload for existing experts', () => {
    const payload = normalizeExpertWriteBody(
      {
        id: 'fund-analyst',
        name: 'Fund',
        description: 'Desc',
        expertType: 'agent',
        promptContent: 'Prompt',
      },
      'fund-analyst',
    );
    expect(payload.id).toBe('fund-analyst');
  });
});

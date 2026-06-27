import { describe, expect, it } from 'vitest';
import type { Expert } from '../types/api';
import { expertRecommendedConnectorIds } from './expertConnectorBootstrap';

function expert(skillCompatibility: string[]): Expert {
  return {
    id: 'demo',
    name: 'Demo',
    description: '',
    expertType: 'agent',
    tags: [],
    skillCompatibility,
  };
}

describe('expertRecommendedConnectorIds', () => {
  it('deduplicates and trims connector ids', () => {
    expect(expertRecommendedConnectorIds(expert([' qieman ', 'qieman', 'oa']))).toEqual([
      'qieman',
      'oa',
    ]);
  });

  it('returns empty for missing compatibility', () => {
    expect(expertRecommendedConnectorIds(expert([]))).toEqual([]);
  });
});

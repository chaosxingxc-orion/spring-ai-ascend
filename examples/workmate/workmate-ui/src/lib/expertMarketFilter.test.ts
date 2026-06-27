import { describe, expect, it } from 'vitest';
import type { Expert } from '../types/api';
import { expertIsBeta, expertMatchesCategory } from './expertMarketFilter';

const baseExpert: Expert = {
  id: 'demo',
  name: 'Demo',
  description: '',
  expertType: 'agent',
  tags: [],
  skillCompatibility: [],
};

describe('expertMatchesCategory', () => {
  it('matches zone by tag without requiring category', () => {
    const expert = { ...baseExpert, category: 'product', tags: ['opc'] };
    expect(expertMatchesCategory(expert, 'opc')).toBe(true);
    expect(expertMatchesCategory(expert, 'product')).toBe(true);
  });

  it('matches standard category', () => {
    const expert = { ...baseExpert, category: 'finance' };
    expect(expertMatchesCategory(expert, 'finance')).toBe(true);
  });
});

describe('expertIsBeta', () => {
  it('reads beta flag or tag', () => {
    expect(expertIsBeta({ ...baseExpert, beta: true })).toBe(true);
    expect(expertIsBeta({ ...baseExpert, tags: ['beta'] })).toBe(true);
    expect(expertIsBeta(baseExpert)).toBe(false);
  });
});

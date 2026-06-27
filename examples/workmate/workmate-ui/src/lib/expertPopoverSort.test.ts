import { describe, expect, it } from 'vitest';
import type { Expert } from '../types/api';
import { sortExpertsForPopover } from './expertPopoverSort';

function expert(id: string, name: string): Expert {
  return {
    id,
    name,
    description: '',
    expertType: 'agent',
    tags: [],
    skillCompatibility: [],
  };
}

describe('sortExpertsForPopover', () => {
  it('puts selected expert first', () => {
    const items = [expert('a', 'Alpha'), expert('b', 'Beta'), expert('c', 'Gamma')];
    const sorted = sortExpertsForPopover(items, 'b', []);
    expect(sorted.map((item) => item.id)).toEqual(['b', 'a', 'c']);
  });

  it('ranks recently used experts after selected', () => {
    const items = [expert('a', 'Alpha'), expert('b', 'Beta'), expert('c', 'Gamma')];
    const sorted = sortExpertsForPopover(items, '', ['c', 'a']);
    expect(sorted.map((item) => item.id)).toEqual(['c', 'a', 'b']);
  });

  it('keeps selected ahead of recent-only entries', () => {
    const items = [expert('a', 'Alpha'), expert('b', 'Beta'), expert('c', 'Gamma')];
    const sorted = sortExpertsForPopover(items, 'b', ['c', 'a']);
    expect(sorted.map((item) => item.id)).toEqual(['b', 'c', 'a']);
  });
});

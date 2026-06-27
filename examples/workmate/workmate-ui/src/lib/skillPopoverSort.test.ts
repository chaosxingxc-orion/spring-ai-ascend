import { describe, expect, it } from 'vitest';
import type { SkillInfo } from '../types/market';
import { sortSkillsForPopover } from './skillPopoverSort';

function skill(id: string, name: string, installed = true): SkillInfo {
  return { id, name, description: '', installed };
}

describe('sortSkillsForPopover', () => {
  it('puts session-enabled skills first', () => {
    const sorted = sortSkillsForPopover(
      [skill('b', 'B'), skill('a', 'A'), skill('c', 'C')],
      ['c', 'a'],
      [],
    );
    expect(sorted.map((item) => item.id)).toEqual(['a', 'c', 'b']);
  });
});

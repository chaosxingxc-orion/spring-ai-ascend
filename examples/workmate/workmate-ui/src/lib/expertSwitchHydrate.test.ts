import { describe, expect, it } from 'vitest';
import { expertSwitchItemsFromRunEvents, mergeStructuralExpertSwitches } from './expertSwitchHydrate';
import type { ChatItem } from '../types/events';

describe('expertSwitchHydrate', () => {
  it('hydrates expert.switched from run_events', () => {
    const items = expertSwitchItemsFromRunEvents(
      [
        {
          seq: 42,
          name: 'expert.switched',
          data: {
            fromExpertId: 'prd-writer',
            toExpertId: 'fund-analyst',
            fromExpertName: 'PRD 写手',
            toExpertName: '基金分析师',
            newGeneration: 2,
            mode: 'SUMMON_IN_SESSION',
          },
        },
      ],
      [],
    );
    expect(items).toHaveLength(1);
    expect(items[0].kind).toBe('expert-switched');
    expect(items[0].toExpertName).toBe('基金分析师');
    expect(items[0].seq).toBe(42);
  });

  it('skips when persisted message already exists', () => {
    const persisted: ChatItem[] = [
      {
        id: 'db-uuid',
        kind: 'expert-switched',
        toExpertId: 'fund-analyst',
        toExpertName: '基金分析师',
        newGeneration: 2,
      },
    ];
    const items = expertSwitchItemsFromRunEvents(
      [
        {
          seq: 1,
          name: 'expert.switched',
          data: {
            toExpertId: 'fund-analyst',
            toExpertName: '基金分析师',
            newGeneration: 2,
          },
        },
      ],
      persisted,
    );
    expect(items).toHaveLength(0);
  });

  it('merges hydrated switches into structural timeline', () => {
    const structural: ChatItem[] = [{ id: 'u1', kind: 'user', text: 'hi' }];
    const hydrated = expertSwitchItemsFromRunEvents(
      [
        {
          seq: 5,
          name: 'expert.switched',
          data: {
            toExpertId: 'fund-analyst',
            toExpertName: '基金分析师',
            newGeneration: 2,
          },
        },
      ],
      structural,
    );
    const merged = mergeStructuralExpertSwitches(structural, hydrated);
    expect(merged).toHaveLength(2);
    expect(merged[1].kind).toBe('expert-switched');
  });
});

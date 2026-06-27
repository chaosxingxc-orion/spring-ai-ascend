import { describe, expect, it } from 'vitest';
import {
  findBusLaneHighlight,
  isSameBusLaneHighlight,
  previewMatches,
} from './busLaneHighlight';
import type { TeamBusEntry } from './teamStatus';

describe('previewMatches', () => {
  it('matches truncated lane preview', () => {
    const long = '这是一段较长的正文内容用于测试截断匹配';
    const truncated = `${long.slice(0, 20)}…`;
    expect(previewMatches(long, truncated)).toBe(true);
  });

  it('returns false when only one side has text', () => {
    expect(previewMatches('hello', undefined)).toBe(false);
  });
});

describe('findBusLaneHighlight', () => {
  const lanes: Record<string, TeamBusEntry[]> = {
    'content-writer': [
      { topic: 'content-writer', preview: '编排发布', publishSource: 'orchestrator' },
      { topic: 'content-writer', preview: '成员 mid-run 正文…', publishSource: 'mid-run' },
    ],
  };

  it('prefers preview match over older entries', () => {
    const hit = findBusLaneHighlight(lanes, 'content-writer', '成员 mid-run 正文');
    expect(hit).toEqual({ topic: 'content-writer', index: 1 });
  });

  it('falls back to last mid-run when preview missing', () => {
    const hit = findBusLaneHighlight(lanes, 'content-writer');
    expect(hit).toEqual({ topic: 'content-writer', index: 1 });
  });
});

describe('isSameBusLaneHighlight', () => {
  it('compares topic and index', () => {
    expect(isSameBusLaneHighlight({ topic: 'a', index: 0 }, { topic: 'a', index: 0 })).toBe(true);
    expect(isSameBusLaneHighlight({ topic: 'a', index: 0 }, { topic: 'a', index: 1 })).toBe(false);
  });
});

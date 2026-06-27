import { describe, expect, it } from 'vitest';
import { detectMentionTrigger, mentionToApiPayload, parseMentionsFromServer } from './mentionParse';
import type { MentionRef } from '../types/mention';

describe('mentionParse', () => {
  it('detects @ and / triggers', () => {
    expect(detectMentionTrigger('read @sum', 9)).toEqual({ trigger: '@', query: 'sum', start: 5 });
    expect(detectMentionTrigger('use /fund', 9)).toEqual({ trigger: '/', query: 'fund', start: 4 });
    expect(detectMentionTrigger('plain', 5)).toEqual({ trigger: null, query: '', start: -1 });
  });

  it('round-trips api payload', () => {
    const mentions: MentionRef[] = [
      { type: 'file', id: 'notes.md', path: 'notes.md', label: 'notes.md' },
      { type: 'skill', id: 'fund-search', label: '基金检索' },
    ];
    expect(mentionToApiPayload(mentions)).toEqual([
      { type: 'file', id: 'notes.md', path: 'notes.md', label: 'notes.md' },
      { type: 'skill', id: 'fund-search', label: '基金检索' },
    ]);
  });

  it('parses server mentions', () => {
    const parsed = parseMentionsFromServer([
      { type: 'member', id: 'analyst', label: '分析师' },
      { type: 'invalid', id: 'x' },
    ]);
    expect(parsed).toEqual([{ type: 'member', id: 'analyst', label: '分析师' }]);
  });
});

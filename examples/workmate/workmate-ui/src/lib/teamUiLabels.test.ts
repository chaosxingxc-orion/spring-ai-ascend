import { describe, expect, it } from 'vitest';
import type { Expert } from '../types/api';
import {
  memberDisplayLabel,
  resolveExpertDisplayName,
  resolveTeamUiLabels,
} from './teamUiLabels';

const gvExpert: Expert = {
  id: 'content-review-team',
  name: '内容质控团队',
  description: '',
  expertType: 'team',
  tags: [],
  skillCompatibility: [],
  coordination: { pattern: 'generator-verifier' },
  uiLabels: {
    gvTitle: '方案起草与合规审查',
    'member.content-writer': '理财顾问',
  },
  members: [
    {
      id: 'content-writer',
      name: '文笔佳',
      expertId: 'content-writer',
      profession: { zh: '内容撰稿人', en: 'Content Writer' },
    },
  ],
};

describe('resolveTeamUiLabels', () => {
  it('merges pattern defaults with expert overrides', () => {
    const labels = resolveTeamUiLabels(gvExpert);
    expect(labels.gvTitle).toBe('方案起草与合规审查');
    expect(labels.gvRejected).toBe('合规审查未通过');
    expect(labels.gvGeneratorBadge).toBe('起草');
  });

  it('falls back to global defaults for unknown patterns', () => {
    const labels = resolveTeamUiLabels(null);
    expect(labels.gvTitle).toBe('生成校验协作');
    expect(labels.messageBusTitle).toBe('消息总线');
  });
});

describe('resolveExpertDisplayName', () => {
  it('prefers displayName over name', () => {
    const expert: Expert = {
      id: 'fund-analyst',
      name: '基金研究助手',
      displayName: { zh: '基金研究助手', en: 'Fund Analyst' },
      description: '',
      expertType: 'agent',
      tags: [],
      skillCompatibility: [],
    };
    expect(resolveExpertDisplayName(expert)).toBe('基金研究助手');
    expect(resolveExpertDisplayName(expert, 'en')).toBe('Fund Analyst');
  });
});

describe('memberDisplayLabel', () => {
  it('prefers uiLabels member key', () => {
    expect(memberDisplayLabel(gvExpert, 'content-writer')).toBe('理财顾问');
  });

  it('formats花名·profession when no uiLabels override', () => {
    const team: Expert = {
      ...gvExpert,
      uiLabels: { gvTitle: '方案起草与合规审查' },
    };
    expect(memberDisplayLabel(team, 'content-writer')).toBe('文笔佳 · 内容撰稿人');
  });
});

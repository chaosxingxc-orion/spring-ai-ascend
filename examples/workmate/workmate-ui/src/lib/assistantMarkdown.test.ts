import { describe, expect, it } from 'vitest';
import { prepareAssistantMarkdown } from './assistantMarkdown';
import { resolveTeamVisualizationLayout } from './teamVisualizationRoute';
import type { TeamState } from './teamStatus';

function team(overrides: Partial<TeamState>): TeamState {
  return {
    pattern: 'orchestrator',
    collaboration: 'sequential',
    lead: null,
    members: [],
    phase: 'done',
    anyMemberFailed: false,
    ...overrides,
  };
}

describe('resolveTeamVisualizationLayout', () => {
  it('uses stepper for sequential orchestrator teams', () => {
    expect(resolveTeamVisualizationLayout(team({ pattern: 'orchestrator', collaboration: 'sequential' })))
      .toBe('stepper');
  });

  it('uses agent grid for parallel orchestrator and agent-team', () => {
    expect(resolveTeamVisualizationLayout(team({ pattern: 'orchestrator', collaboration: 'parallel' })))
      .toBe('agent-grid');
    expect(resolveTeamVisualizationLayout(team({ pattern: 'agent-team', collaboration: 'parallel' })))
      .toBe('agent-grid');
  });

  it('uses specialized layouts for other patterns', () => {
    expect(resolveTeamVisualizationLayout(team({ pattern: 'message-bus', collaboration: 'parallel' })))
      .toBe('specialized');
  });
});

describe('prepareAssistantMarkdown', () => {
  it('splits a heading marker glued to the previous line', () => {
    const out = prepareAssistantMarkdown('正文内容## 小标题');
    expect(out).toContain('\n## 小标题');
  });

  it('adds a missing space after heading hashes', () => {
    expect(prepareAssistantMarkdown('##标题')).toContain('## 标题');
  });

  it('removes orphan hash-only lines', () => {
    const source = ['# 报告', '#', '##', '正文'].join('\n');
    const out = prepareAssistantMarkdown(source);
    expect(out).not.toMatch(/^#+\s*$/m);
    expect(out).toContain('正文');
  });

  it('renders content faithfully without deleting repeated sections', () => {
    const source = ['# 报告', '第一版', '', '# 报告', '第二版'].join('\n');
    const out = prepareAssistantMarkdown(source);
    expect(out).toContain('第一版');
    expect(out).toContain('第二版');
    expect(out.match(/# 报告/g)?.length).toBe(2);
  });

  it('keeps member subheadings at their original level', () => {
    const source = '## 各成员分析摘要\n## 张三\n内容';
    const out = prepareAssistantMarkdown(source);
    expect(out).toContain('## 张三');
    expect(out).not.toContain('### 张三');
  });

  it('trims streaming replay when duplicate paragraphs restart mid-bubble', () => {
    const source = [
      '好的，我来检索基金信息。',
      '',
      '| 项目 | 内容 |',
      '| --- | --- |',
      '| 基金代码 | 005827 |',
      '',
      '建议稍后重试。',
      '好的，我来检索基金信息。',
      '',
      '| 项目 | 内容 |',
      '| --- | --- |',
      '| 基金代码 | 005827 |',
    ].join('\n');
    const out = prepareAssistantMarkdown(source);
    expect(out).toContain('005827');
    expect(out.match(/\| 项目 \| 内容 \|/g)?.length).toBe(1);
    expect(out.match(/好的，我来检索基金信息。/g)?.length).toBe(1);
  });
});

import { describe, expect, it } from 'vitest';
import { extractReportTitle, simpleMarkdown } from './simpleMarkdown';

describe('simpleMarkdown', () => {
  it('renders markdown table', () => {
    const html = simpleMarkdown('| A | B |\n| --- | --- |\n| 1 | 2 |');
    expect(html).toContain('<table class="md-table">');
    expect(html).toContain('<td>1</td>');
  });

  it('strips heavy box-drawing borders and keeps content readable', () => {
    const html = simpleMarkdown('━━━━━━━━━━📋 进度通报━━━━━━━━━━📍 当前阶段：Phase 1');
    expect(html).not.toContain('━');
    expect(html).toContain('📋 进度通报');
    expect(html).toContain('📍 当前阶段：Phase 1');
  });

  it('breaks crammed status-card rows onto separate lines', () => {
    const html = simpleMarkdown(
      '⬚ Phase2规划大纲 —待执行⬚ Phase3逐章研究 —待执行⏭️下一步：等待回传',
    );
    expect(html).toContain('<p>⬚ Phase2规划大纲 —待执行</p>');
    expect(html).toContain('<p>⬚ Phase3逐章研究 —待执行</p>');
    expect(html).toContain('<p>⏭️下一步：等待回传</p>');
  });

  it('leaves light box-drawing (tree output) untouched', () => {
    const tree = '```\nsrc\n├── a.ts\n└── b.ts\n```';
    const html = simpleMarkdown(tree);
    expect(html).toContain('├──');
    expect(html).toContain('└──');
  });

  it('renders a light divider line as a rule', () => {
    const html = simpleMarkdown('above\n──────\nbelow');
    expect(html).toContain('<hr class="md-hr" />');
  });

  it('renders blockquote', () => {
    const html = simpleMarkdown('> 合规提示');
    expect(html).toContain('blockquote');
    expect(html).toContain('合规提示');
  });

  it('highlights fenced code blocks with language', () => {
    const html = simpleMarkdown('```javascript\nconst x = 1;\n```');
    expect(html).toContain('class="language-javascript"');
    expect(html).toContain('token');
    expect(html).toContain('const');
  });

  it('linkifies http(s) URLs with safe rel', () => {
    const html = simpleMarkdown('See https://example.com/docs for details.');
    expect(html).toContain('<a href="https://example.com/docs"');
    expect(html).toContain('rel="noopener noreferrer"');
    expect(html).toContain('target="_blank"');
  });

  it('does not linkify URLs inside inline code', () => {
    const html = simpleMarkdown('Use `https://example.com` literally.');
    expect(html).toContain('<code>https://example.com</code>');
    expect(html).not.toContain('<a href="https://example.com"');
  });

  it('strips trailing punctuation from linkified URLs', () => {
    const html = simpleMarkdown('Docs: https://example.com/path.');
    expect(html).toContain('<a href="https://example.com/path"');
    expect(html).toContain('/path</a>.');
  });

  it('escapes linkified URL attributes', () => {
    const html = simpleMarkdown('See https://example.com/"onclick="alert(1)');
    expect(html).toContain('href="https://example.com/&amp;quot;onclick=&amp;quot;alert(1"');
    expect(html).not.toContain('"onclick=');
  });

  it('renders workspace file paths in backticks as buttons', () => {
    const html = simpleMarkdown('See `team/run/blackboard.md` for details.', {
      knownWorkspacePaths: new Set(['team/run/blackboard.md']),
    });
    expect(html).toContain('class="md-ws-file"');
    expect(html).toContain('data-ws-path="team/run/blackboard.md"');
  });

  it('marks missing workspace paths', () => {
    const html = simpleMarkdown('Saved `deliverables/report.md`', {
      knownWorkspacePaths: new Set(['team/run/blackboard.md']),
    });
    expect(html).toContain('md-ws-file missing');
  });

  it('parses headings without space after hash', () => {
    const html = simpleMarkdown('#Title without space');
    expect(html).toContain('<h1>');
    expect(html).toContain('Title without space');
  });

  it('renders ordered lists', () => {
    const html = simpleMarkdown('1. 第一步\n2. 第二步');
    expect(html).toContain('<ol>');
    expect(html).toContain('<li>第一步</li>');
    expect(html).toContain('<li>第二步</li>');
    expect(html).not.toContain('1. 第一步');
  });

  it('does not merge ordered and unordered lists', () => {
    const html = simpleMarkdown('- 项目\n1. 步骤');
    expect(html).toContain('<ul><li>项目</li></ul>');
    expect(html).toContain('<ol><li>步骤</li></ol>');
  });

  it('keeps short titles as headings', () => {
    const html = simpleMarkdown('## Phase 2 规划大纲');
    expect(html).toContain('<h2>');
    expect(html).toContain('Phase 2 规划大纲');
  });

  it('renders run-on heading lines as paragraphs, not giant headings', () => {
    const runOn =
      '## Phase1：初始调研调度谭溯源进行广泛初步调研。谭溯源正在调研中，等待回传结果。让我检查一下是否有新消息。';
    const html = simpleMarkdown(runOn);
    expect(html).not.toContain('<h2>');
    expect(html).toContain('<p>');
    expect(html).toContain('谭溯源正在调研中');
    expect(html).not.toContain('##');
  });

  it('linkifies bare workspace paths outside backticks', () => {
    const html = simpleMarkdown('Saved deliverables/a-share/report.md for review.', {
      knownWorkspacePaths: new Set(['deliverables/a-share/report.md']),
    });
    expect(html).toContain('class="md-ws-file"');
    expect(html).toContain('data-ws-path="deliverables/a-share/report.md"');
  });
});

describe('extractReportTitle', () => {
  it('uses first h1', () => {
    expect(extractReportTitle('# 季度检视报告\n正文', 'report.md')).toBe('季度检视报告');
  });

  it('falls back to filename', () => {
    expect(extractReportTitle('正文', 'credit-memo.md')).toBe('credit memo');
  });
});

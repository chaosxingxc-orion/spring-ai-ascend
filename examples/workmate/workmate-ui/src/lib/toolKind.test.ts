import { describe, expect, it } from 'vitest';
import {
  bashCommandFromArgs,
  busPublishPreviewFromArgs,
  busPublishTopicFromResult,
  classifyTool,
  isTeamBusPublishTool,
  parseMcpToolName,
  parseWebFetchPreview,
  parseWebSearchResults,
  parseSkillPreview,
  skillProgressFromResult,
  toolStepLabel,
  writeDiffSummary,
} from '../lib/toolKind';

describe('toolKind', () => {
  it('classifies bash tools', () => {
    expect(classifyTool('workmate_bash__abc')).toBe('bash');
    expect(classifyTool('workmate_bash__abc', { command: 'ls -la' })).toBe('list');
    expect(classifyTool('workmate_bash__abc', { command: 'rm notes.md' })).toBe('delete');
    expect(toolStepLabel('workmate_bash__abc', { command: 'ls' })).toBe('列出文件');
    expect(bashCommandFromArgs({ command: 'pip install pdfplumber' })).toBe('pip install pdfplumber');
  });

  it('recovers bash command from redacted / stringified args', () => {
    // Live args may arrive as a JSON string.
    expect(bashCommandFromArgs('{"command": "ls -la"}')).toBe('ls -la');
    // Persisted args are redacted: the whole args object is stringified into `preview` and the
    // command (which the BashToolCard reads) is no longer a top-level field.
    expect(
      bashCommandFromArgs({ preview: '{"command": "echo hi"}', bytes: 22, sha256: 'x' }),
    ).toBe('echo hi');
    // The preview can be truncated mid-value (no closing quote) — still recover what is present.
    expect(
      bashCommandFromArgs({ preview: '{"command": "cat << EOF > /tmp/a.md\\n# title', bytes: 600 }),
    ).toBe('cat << EOF > /tmp/a.md\n# title');
    // Per-field redaction keeps a {preview} object on `command`.
    expect(bashCommandFromArgs({ command: { preview: 'curl https://x', bytes: 14 } })).toBe(
      'curl https://x',
    );
  });

  it('classifies read/write tools', () => {
    expect(classifyTool('workmate_read__abc')).toBe('read');
    expect(toolStepLabel('workmate_read__abc', { path: '/tmp/report.pdf' })).toBe(
      '读取 /tmp/report.pdf',
    );
    expect(classifyTool('workmate_write__abc')).toBe('write');
  });

  it('labels delegation step with the recipient (plain and redacted args)', () => {
    expect(toolStepLabel('team.send_message', { memberName: '傅梓铭' })).toBe('派活给 傅梓铭');
    expect(
      toolStepLabel('team.send_message', { memberName: { preview: '程文成', bytes: 9 } }),
    ).toBe('派活给 程文成');
    expect(toolStepLabel('team.send_message', {})).toBe('派活给成员');
  });

  it('classifies member send_message as receive card', () => {
    expect(classifyTool('send_message', { to: '__lead__' })).toBe('generic');
    expect(classifyTool('send_message', { to: '__lead__' }, { memberId: 'research-planner' })).toBe(
      'team-receive-message',
    );
    expect(classifyTool('send_message', { to: 'team-lead' }, { memberId: 'topic-researcher' })).toBe(
      'team-receive-message',
    );
  });

  it('classifies team bus publish tool', () => {
    expect(isTeamBusPublishTool('workmate_team_bus_publish__session-1')).toBe(true);
    expect(classifyTool('workmate_team_bus_publish__session-1')).toBe('team-bus-publish');
    expect(
      toolStepLabel('workmate_team_bus_publish__session-1', { topic: 'writer', body: 'note' }),
    ).toBe('发布到总线 · writer');
    expect(busPublishPreviewFromArgs({ body: 'incremental finding' })).toEqual({
      preview: 'incremental finding',
    });
    expect(
      busPublishTopicFromResult({ success: true, data: { topic: 'writer', bytes: 12 } }),
    ).toBe('writer');
  });

  it('parses mcp tool names', () => {
    expect(parseMcpToolName('mcp__qieman__SearchFunds')).toEqual({
      server: 'qieman',
      tool: 'SearchFunds',
    });
  });

  it('summarizes write tool output', () => {
    const summary = writeDiffSummary(
      { path: 'report.md', content: 'line1\nline2' },
      { success: true, data: { path: 'report.md', bytes: 12 } },
    );
    expect(summary?.path).toBe('report.md');
    expect(summary?.label).toContain('report.md');
  });

  it('classifies skill tools', () => {
    expect(classifyTool('invoke_skill')).toBe('skill');
    expect(classifyTool('Skill__prd-writer')).toBe('skill');
    expect(parseSkillPreview({ skillName: 'PRD 写手', step: '起草' }, 'invoke_skill')).toEqual({
      name: 'PRD 写手',
      detail: '起草',
    });
    expect(skillProgressFromResult({ success: true, data: { percent: 100, message: 'done' } })).toEqual({
      percent: 100,
      message: 'done',
    });
  });

  it('classifies web search and fetch tools', () => {
    expect(classifyTool('WebSearch')).toBe('web-search');
    expect(classifyTool('web_fetch')).toBe('web-fetch');
    expect(classifyTool('mcp__search__WebSearch')).toBe('web-search');
    expect(parseWebSearchResults({
      success: true,
      data: {
        results: [{ title: 'Example', url: 'https://example.com', snippet: 'hello' }],
      },
    })).toEqual([
      { title: 'Example', url: 'https://example.com', snippet: 'hello' },
    ]);
    expect(parseWebFetchPreview({ url: 'https://example.com' }, { url: 'https://example.com' })).toEqual({
      url: 'https://example.com',
      title: undefined,
      snippet: undefined,
    });
  });
});

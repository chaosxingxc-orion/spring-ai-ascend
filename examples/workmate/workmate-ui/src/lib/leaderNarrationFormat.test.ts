import { describe, expect, it } from 'vitest';
import { formatLeaderNarrationText } from './leaderNarrationFormat';

describe('formatLeaderNarrationText', () => {
  it('inserts a paragraph break after ellipsis before the next clause', () => {
    const text = '已派发第3章深度调研任务给谭溯源，正在等待其回传结果...第3章草稿质量很高。现在进入审稿环节。';
    expect(formatLeaderNarrationText(text)).toBe(
      '已派发第3章深度调研任务给谭溯源，正在等待其回传结果...\n\n第3章草稿质量很高。现在进入审稿环节。',
    );
  });

  it('leaves text without ellipsis unchanged', () => {
    const text = '团队已建立，开始 Phase 1。';
    expect(formatLeaderNarrationText(text)).toBe(text);
  });
});

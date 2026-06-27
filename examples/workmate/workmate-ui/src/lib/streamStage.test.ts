import { describe, expect, it } from 'vitest';
import { STREAM_STAGE_LABELS, streamStageTip } from './streamStage';

describe('streamStage', () => {
  it('labels all stages', () => {
    expect(STREAM_STAGE_LABELS.thinking).toBe('思考');
    expect(STREAM_STAGE_LABELS.tool).toBe('工具');
    expect(STREAM_STAGE_LABELS.generating).toBe('生成');
  });

  it('returns stage tips', () => {
    expect(streamStageTip('thinking')).toContain('推理');
    expect(streamStageTip('tool')).toContain('工具');
    expect(streamStageTip('generating')).toContain('生成');
    expect(streamStageTip(null)).toBeNull();
  });
});

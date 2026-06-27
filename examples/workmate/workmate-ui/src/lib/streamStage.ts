export type StreamStage = 'thinking' | 'tool' | 'generating';

export const STREAM_STAGE_LABELS: Record<StreamStage, string> = {
  thinking: '思考',
  tool: '工具',
  generating: '生成',
};

/** Stage-specific hint shown in LoadingTips while streaming. */
export function streamStageTip(stage: StreamStage | null | undefined): string | null {
  if (!stage) {
    return null;
  }
  switch (stage) {
    case 'thinking':
      return 'Agent 正在推理，完成后会展示回复或调用工具';
    case 'tool':
      return '正在执行工具，可在对话流中展开详情';
    case 'generating':
      return '正在生成回复，可切换到其他任务，结果会保留在此';
    default:
      return null;
  }
}

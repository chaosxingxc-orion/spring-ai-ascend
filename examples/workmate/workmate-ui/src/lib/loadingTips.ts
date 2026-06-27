export const LOADING_TIPS = [
  '长任务可切换标签页，Agent 会在后台继续运行',
  '工具执行中可展开卡片查看详细进度',
  '对结果不满意时，可编辑上一条消息重新发送',
  '团队任务可在右侧「团队」Tab 查看成员状态',
  '文件变更可在右侧「文件」Tab 预览与回退',
] as const;

export function pickLoadingTip(seed: number): string {
  const index = Math.abs(seed) % LOADING_TIPS.length;
  return LOADING_TIPS[index] ?? LOADING_TIPS[0];
}

export const LOADING_TIPS_DISMISS_KEY = 'wm-loading-tips-dismissed';

export function isLoadingTipsDismissed(): boolean {
  try {
    return localStorage.getItem(LOADING_TIPS_DISMISS_KEY) === '1';
  } catch {
    return false;
  }
}

export function dismissLoadingTips(): void {
  try {
    localStorage.setItem(LOADING_TIPS_DISMISS_KEY, '1');
  } catch {
    // ignore
  }
}

export type OAuthWalkthroughStepId =
  | 'intro'
  | 'authorize'
  | 'complete'
  | 'verify';

export interface OAuthWalkthroughStep {
  id: OAuthWalkthroughStepId;
  title: string;
  detail: string;
}

export const DEVICE_CODE_WALKTHROUGH: OAuthWalkthroughStep[] = [
  {
    id: 'intro',
    title: '了解连接器',
    detail: '确认该 MCP 连接器提供的工具范围，以及凭据将仅保存在本机 workmate-api。',
  },
  {
    id: 'authorize',
    title: '获取设备码',
    detail: '在盈米 Stargate 或对应控制台输入设备码，完成一方授权。',
  },
  {
    id: 'complete',
    title: '粘贴密钥',
    detail: '将授权后获得的 API Key 粘贴到下方输入框。',
  },
  {
    id: 'verify',
    title: '验证连接',
    detail: '连接成功后可在对话中 @ 连接器，或在市场页查看已连接状态。',
  },
];

export const REDIRECT_WALKTHROUGH: OAuthWalkthroughStep[] = [
  {
    id: 'intro',
    title: '了解连接器',
    detail: 'OAuth 重定向流将打开授权页，完成后带回授权码。',
  },
  {
    id: 'authorize',
    title: '打开授权页',
    detail: '在浏览器完成登录与授权；桌面版可自动通过 workmate:// 深链回传。',
  },
  {
    id: 'complete',
    title: '完成回调',
    detail: '粘贴授权码或等待桌面版自动填入。',
  },
  {
    id: 'verify',
    title: '验证连接',
    detail: '凭据脱敏显示在连接器卡片上，可随时撤销。',
  },
];

export const TOKEN_WALKTHROUGH: OAuthWalkthroughStep[] = [
  {
    id: 'intro',
    title: '准备凭据',
    detail: '从服务方控制台复制 API Key 或 Personal Access Token。',
  },
  {
    id: 'complete',
    title: '粘贴并保存',
    detail: '凭据经本地加密存储，仅用于 MCP 请求头注入。',
  },
  {
    id: 'verify',
    title: '验证连接',
    detail: '连接后 Agent 即可调用该连接器工具。',
  },
];

export function walkthroughForAuthMethod(
  method: string | undefined,
): OAuthWalkthroughStep[] {
  switch (method) {
    case 'REDIRECT':
      return REDIRECT_WALKTHROUGH;
    case 'DEVICE_CODE':
    case 'QR':
      return DEVICE_CODE_WALKTHROUGH;
    default:
      return TOKEN_WALKTHROUGH;
  }
}

export function activeWalkthroughStep(
  steps: OAuthWalkthroughStep[],
  phase: OAuthWalkthroughStepId,
): number {
  const index = steps.findIndex((step) => step.id === phase);
  return index >= 0 ? index : 0;
}

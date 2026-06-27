import { formatNumber } from '../../lib/formatLocale';

interface ConsumptionBadgeProps {
  streaming?: boolean;
  textLength?: number;
  promptTokens?: number;
  completionTokens?: number;
}

/** 会话 token 用量：有累计值时展示实际用量，否则在输入中展示估算区间 */
export function ConsumptionBadge({
  streaming,
  textLength = 0,
  promptTokens = 0,
  completionTokens = 0,
}: ConsumptionBadgeProps) {
  const totalTokens = promptTokens + completionTokens;

  if (totalTokens > 0) {
    return (
      <span className="consumption-badge consumption-badge-actual" title="本轮会话累计 token">
        已消耗 <span className="consumption-icon" aria-hidden>◇</span> {formatNumber(totalTokens)}
      </span>
    );
  }

  if (!streaming && textLength === 0) {
    return null;
  }

  const base = Math.max(textLength, streaming ? 120 : 0) / 100;
  const min = Math.max(1, base * 0.7).toFixed(1);
  const max = Math.max(3, base * 2.5).toFixed(0);

  return (
    <span className="consumption-badge" title="估算值，首个模型响应后将显示实际用量">
      预计消耗 <span className="consumption-icon" aria-hidden>◇</span> {min} ~ {max}
    </span>
  );
}

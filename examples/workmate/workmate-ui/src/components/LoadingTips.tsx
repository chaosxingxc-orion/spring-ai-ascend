import { useEffect, useState } from 'react';
import {
  dismissLoadingTips,
  isLoadingTipsDismissed,
  pickLoadingTip,
} from '../lib/loadingTips';
import { streamStageTip, type StreamStage } from '../lib/streamStage';

interface LoadingTipsProps {
  streaming: boolean;
  streamStage?: StreamStage | null;
}

/** F5 — gentle hints while the agent is still working. */
export function LoadingTips({ streaming, streamStage = null }: LoadingTipsProps) {
  const [dismissed, setDismissed] = useState(isLoadingTipsDismissed);
  const [tipIndex, setTipIndex] = useState(0);

  useEffect(() => {
    if (!streaming || dismissed) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      setTipIndex((value) => value + 1);
    }, 8000);
    return () => window.clearInterval(timer);
  }, [streaming, dismissed]);

  if (!streaming || dismissed) {
    return null;
  }

  const stageTip = streamStageTip(streamStage);

  return (
    <div className="loading-tips" role="status" aria-live="polite">
      <span className="loading-tips-text">{stageTip ?? pickLoadingTip(tipIndex)}</span>
      <button
        type="button"
        className="btn ghost sm loading-tips-dismiss"
        onClick={() => {
          dismissLoadingTips();
          setDismissed(true);
        }}
      >
        不再提示
      </button>
    </div>
  );
}

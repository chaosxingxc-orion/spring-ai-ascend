import { useState } from 'react';
import { studioReload } from '../../api/studio';

export function DevStudioReloadButton() {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleReload = async () => {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const result = await studioReload();
      const warningCount = result.warnings?.length ?? 0;
      setMessage(
        `已热加载：${result.experts} 专家 · ${result.skills} 技能` +
          (warningCount > 0 ? ` · ${warningCount} 条警告` : ''),
      );
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="dev-studio-reload">
      <button type="button" className="btn ghost sm" disabled={busy} onClick={() => void handleReload()}>
        {busy ? '加载中…' : '热加载'}
      </button>
      {message && (
        <span className="dev-studio-reload-msg muted" role="status">
          {message}
        </span>
      )}
      {error && <span className="dev-studio-reload-msg dev-studio-error">{error}</span>}
    </div>
  );
}

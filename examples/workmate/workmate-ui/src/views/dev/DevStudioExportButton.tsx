import { useState } from 'react';
import { downloadStudioAllExports, getStudioExportPreview } from '../../api/studio';

export function DevStudioExportButton() {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleExport = async () => {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const preview = await getStudioExportPreview();
      if (preview.items.length === 0) {
        setMessage('没有可导出的草稿');
        return;
      }
      await downloadStudioAllExports();
      setMessage(`已导出 ${preview.expertCount} 专家 · ${preview.skillCount} 技能`);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="dev-studio-export">
      <button type="button" className="btn ghost sm" disabled={busy} onClick={() => void handleExport()}>
        {busy ? '导出中…' : '导出 office'}
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

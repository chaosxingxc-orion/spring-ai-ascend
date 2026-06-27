import { useEffect, useState } from 'react';
import {
  buildShareUrl,
  createSessionShare,
  type ShareCreateOptions,
  type ShareLink,
  type ShareScope,
} from '../../api/share';

interface ShareDialogProps {
  open: boolean;
  sessionId: string;
  sessionTitle: string;
  busy?: boolean;
  onClose: () => void;
  onShared?: (link: ShareLink, url: string) => void;
}

const EXPIRY_OPTIONS = [
  { label: '24 小时', hours: 24 },
  { label: '7 天', hours: 168 },
  { label: '30 天', hours: 720 },
] as const;

/** W49-E2 — scope / expiry / copy link dialog. */
export function ShareDialog({
  open,
  sessionId,
  sessionTitle,
  busy = false,
  onClose,
  onShared,
}: ShareDialogProps) {
  const [scope, setScope] = useState<ShareScope>('full');
  const [expiresInHours, setExpiresInHours] = useState(168);
  const [shareUrl, setShareUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [localBusy, setLocalBusy] = useState(false);

  useEffect(() => {
    if (!open) {
      setShareUrl(null);
      setError(null);
      setScope('full');
      setExpiresInHours(168);
    }
  }, [open]);

  if (!open) {
    return null;
  }

  const working = busy || localBusy;

  const handleCreate = async () => {
    setLocalBusy(true);
    setError(null);
    try {
      const options: ShareCreateOptions = { scope, expiresInHours };
      const link = await createSessionShare(sessionId, options);
      const url = buildShareUrl(window.location.origin, link.token);
      setShareUrl(url);
      onShared?.(link, url);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLocalBusy(false);
    }
  };

  const handleCopy = async () => {
    if (!shareUrl) {
      return;
    }
    try {
      await navigator.clipboard.writeText(shareUrl);
    } catch {
      setError('复制失败，请手动选择链接复制');
    }
  };

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div
        className="modal share-dialog"
        role="dialog"
        aria-labelledby="share-dialog-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="modal-header">
          <h2 id="share-dialog-title">分享任务</h2>
          <button type="button" className="btn ghost" onClick={onClose} disabled={working}>
            ×
          </button>
        </header>
        <div className="modal-body">
          <p className="approval-summary">
            为「{sessionTitle}」创建只读分享链接。内容将自动脱敏，访客无法发送消息。
          </p>

          <label className="connector-connect-field">
            <span>分享范围</span>
            <select
              className="connector-connect-input"
              value={scope}
              disabled={working || Boolean(shareUrl)}
              onChange={(event) => setScope(event.target.value as ShareScope)}
            >
              <option value="full">对话 + 产物</option>
              <option value="messages">仅对话回放</option>
              <option value="artifacts">仅产物列表</option>
            </select>
          </label>

          <label className="connector-connect-field">
            <span>有效期</span>
            <select
              className="connector-connect-input"
              value={expiresInHours}
              disabled={working || Boolean(shareUrl)}
              onChange={(event) => setExpiresInHours(Number(event.target.value))}
            >
              {EXPIRY_OPTIONS.map((option) => (
                <option key={option.hours} value={option.hours}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>

          {shareUrl && (
            <label className="import-field">
              <span>分享链接</span>
              <input className="connector-connect-input" readOnly value={shareUrl} />
            </label>
          )}

          {error && <p className="market-hint error">{error}</p>}
        </div>
        <footer className="modal-footer">
          <button type="button" className="btn ghost" disabled={working} onClick={onClose}>
            关闭
          </button>
          {!shareUrl ? (
            <button type="button" className="btn primary" disabled={working} onClick={() => void handleCreate()}>
              {working ? '生成中…' : '生成链接'}
            </button>
          ) : (
            <button type="button" className="btn primary" disabled={working} onClick={() => void handleCopy()}>
              复制链接
            </button>
          )}
        </footer>
      </div>
    </div>
  );
}

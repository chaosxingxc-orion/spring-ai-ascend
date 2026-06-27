import type { Expert } from '../../types/api';
import { summonActionLabel, formatConnectorLabel, expertRuntimeType, TERM } from '../../lib/terminology';

interface SummonConfirmModalProps {
  expert: Expert | null;
  busy: boolean;
  /** When true, bind expert to the open session instead of creating a new task. */
  continueSession?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function SummonConfirmModal({
  expert,
  busy,
  continueSession = false,
  onConfirm,
  onCancel,
}: SummonConfirmModalProps) {
  if (!expert) {
    return null;
  }

  const hasInitPrompt = Boolean(expert.defaultInitPrompt?.trim());

  return (
    <div className="modal-backdrop" role="presentation">
      <div className="modal summon-modal" role="dialog" aria-labelledby="summon-title">
        <header className="modal-header">
          <h3 id="summon-title">{summonActionLabel(expert.expertType)}</h3>
          <button type="button" className="btn ghost" onClick={onCancel} disabled={busy}>
            ×
          </button>
        </header>
        <div className="modal-body">
          <p>
            {continueSession ? (
              <>
                将在<strong>当前对话</strong>中继续，并绑定 <strong>{expert.name}</strong>
              </>
            ) : (
              <>
                将创建新任务并绑定 <strong>{expert.name}</strong>
              </>
            )}
            <span className="summon-runtime-type">（{expertRuntimeType(expert.expertType)} · {expert.id}）</span>
          </p>
          {expert.skillCompatibility.length > 0 && (
            <p className="summon-mcp-hint">
              推荐{TERM.connector}：
              {expert.skillCompatibility.map((id) => formatConnectorLabel(id)).join('、')}
            </p>
          )}
          {hasInitPrompt ? (
            <>
              <p className="summon-mcp-hint">默认开场 prompt 将填入输入框，可自行修改后再发送。</p>
              <pre className="summon-prompt-preview">{expert.defaultInitPrompt?.trim()}</pre>
            </>
          ) : (
            <p className="summon-mcp-hint">
              {continueSession
                ? '确认后可在输入框描述下一步任务并发送。'
                : '进入对话页后，在输入框中描述任务并发送。'}
            </p>
          )}
          {expert.skillCompatibility.length > 0 && (
            <p className="summon-mcp-hint">
              推荐{TERM.connector}将{continueSession ? '为本会话' : '在创建任务后'}自动启用。
            </p>
          )}
        </div>
        <footer className="modal-footer">
          <div className="modal-footer-actions">
            <button type="button" className="btn secondary" disabled={busy} onClick={onCancel}>
              取消
            </button>
            <button type="button" className="btn primary" disabled={busy} onClick={onConfirm}>
              {busy ? (continueSession ? '切换中…' : '创建中…') : '确认召唤'}
            </button>
          </div>
        </footer>
      </div>
    </div>
  );
}

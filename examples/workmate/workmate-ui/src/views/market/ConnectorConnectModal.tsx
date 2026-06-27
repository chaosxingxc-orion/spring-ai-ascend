import type { ConnectorInfo } from '../../types/market';

interface ConnectorConnectModalProps {
  connector: ConnectorInfo | null;
  busy?: boolean;
  onConfirm: (apiKey?: string) => void;
  onCancel: () => void;
}

export function ConnectorConnectModal({
  connector,
  busy,
  onConfirm,
  onCancel,
}: ConnectorConnectModalProps) {
  if (!connector) {
    return null;
  }

  const needsKey = connector.requiresAuth;

  return (
    <div className="modal-backdrop" role="presentation" onClick={onCancel}>
      <div
        className="modal connector-connect-modal"
        role="dialog"
        aria-labelledby="connector-connect-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="modal-header">
          <h2 id="connector-connect-title">连接 {connector.name}</h2>
        </header>
        <div className="modal-body">
          <p className="modal-copy">{connector.description}</p>
          {needsKey && (
            <label className="connector-connect-field">
              <span>{connector.authHint ?? 'API Key'}</span>
              <input
                type="password"
                className="connector-connect-input"
                placeholder="粘贴 API Key"
                id="connector-api-key"
                autoComplete="off"
              />
            </label>
          )}
        </div>
        <footer className="modal-footer">
          <button type="button" className="btn secondary" disabled={busy} onClick={onCancel}>
            取消
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={busy}
            onClick={() => {
              if (needsKey) {
                const input = document.getElementById('connector-api-key') as HTMLInputElement | null;
                const value = input?.value?.trim();
                if (!value) {
                  return;
                }
                onConfirm(value);
                return;
              }
              onConfirm();
            }}
          >
            {busy ? '连接中…' : '确认连接'}
          </button>
        </footer>
      </div>
    </div>
  );
}

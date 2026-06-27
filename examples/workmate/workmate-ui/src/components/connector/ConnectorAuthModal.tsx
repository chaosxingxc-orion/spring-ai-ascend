import { useEffect, useMemo, useState } from 'react';
import type { ConnectorInfo } from '../../types/market';
import {
  completeOAuthCallback,
  completeOAuthDeviceCode,
  connectConnector,
  getConnectorAuthProfile,
  revokeConnector,
  startOAuthDeviceCode,
  startOAuthRedirect,
  storeOAuthToken,
} from '../../api/market';
import { resolveApiAuthorizeUrl } from '../../lib/apiBase';
import {
  walkthroughForAuthMethod,
  type OAuthWalkthroughStepId,
} from '../../lib/connectorOAuthWalkthrough';
import { getDesktopBridge } from '../../lib/desktopBridge';
import { ConnectorOAuthWalkthrough } from './ConnectorOAuthWalkthrough';

type AuthTab = 'device' | 'redirect' | 'token';

interface ConnectorAuthModalProps {
  connector: ConnectorInfo | null;
  busy?: boolean;
  onConnected: (connector: ConnectorInfo) => void;
  onRevoked?: (connector: ConnectorInfo) => void;
  onCancel: () => void;
}

export function ConnectorAuthModal({
  connector,
  busy = false,
  onConnected,
  onRevoked,
  onCancel,
}: ConnectorAuthModalProps) {
  const [tab, setTab] = useState<AuthTab>('device');
  const [error, setError] = useState<string | null>(null);
  const [localBusy, setLocalBusy] = useState(false);
  const [credentialMask, setCredentialMask] = useState<string | null>(null);
  const [deviceSession, setDeviceSession] = useState<{
    sessionId: string;
    userCode: string;
    verificationUri: string;
  } | null>(null);
  const [redirectState, setRedirectState] = useState<{ state: string; authorizeUrl: string } | null>(null);
  const [secret, setSecret] = useState('');

  const authMethod = connector?.authMethod ?? (connector?.requiresAuth ? 'API_KEY' : 'NONE');
  const walkthroughSteps = useMemo(() => walkthroughForAuthMethod(authMethod), [authMethod]);

  const walkthroughPhase = useMemo((): OAuthWalkthroughStepId => {
    if (credentialMask || connector?.hasCredential) {
      return 'verify';
    }
    if (tab === 'token' && secret.trim()) {
      return 'verify';
    }
    if (tab === 'redirect' && redirectState && secret.trim()) {
      return 'verify';
    }
    if (tab === 'redirect' && redirectState) {
      return 'complete';
    }
    if (tab === 'device' && deviceSession && secret.trim()) {
      return 'complete';
    }
    if (tab === 'device' && deviceSession) {
      return 'authorize';
    }
    if (tab === 'redirect' && !redirectState) {
      return 'authorize';
    }
    if (tab === 'device' && !deviceSession) {
      return 'authorize';
    }
    return 'intro';
  }, [credentialMask, connector?.hasCredential, deviceSession, redirectState, secret, tab]);

  useEffect(() => {
    if (!connector) {
      return;
    }
    setError(null);
    setSecret('');
    setDeviceSession(null);
    setRedirectState(null);
    const method = connector.authMethod ?? (connector.requiresAuth ? 'API_KEY' : 'NONE');
    if (method === 'REDIRECT') {
      setTab('redirect');
    } else if (method === 'DEVICE_CODE' || method === 'QR') {
      setTab('device');
    } else {
      setTab('token');
    }
    void getConnectorAuthProfile(connector.id)
      .then((profile) => setCredentialMask(profile.credentialMask ?? null))
      .catch(() => setCredentialMask(connector.credentialMask ?? null));
  }, [connector]);

  const working = busy || localBusy;

  const run = async (action: () => Promise<ConnectorInfo>) => {
    setLocalBusy(true);
    setError(null);
    try {
      const updated = await action();
      onConnected(updated);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLocalBusy(false);
    }
  };

  useEffect(() => {
    const bridge = getDesktopBridge();
    if (!bridge?.onOAuthCallback || !redirectState) {
      return undefined;
    }
    return bridge.onOAuthCallback(({ state, code }) => {
      if (state !== redirectState.state) {
        return;
      }
      setSecret(code);
      void run(() => completeOAuthCallback(state, code));
    });
  }, [redirectState]);

  if (!connector) {
    return null;
  }

  const handleDirectConnect = () => {
    void run(() => connectConnector(connector.id));
  };

  const handleStartDevice = () => {
    void (async () => {
      setLocalBusy(true);
      setError(null);
      try {
        const start = await startOAuthDeviceCode(
          connector.id,
          authMethod === 'QR' ? 'QR' : 'DEVICE_CODE',
        );
        setDeviceSession({
          sessionId: start.sessionId,
          userCode: start.userCode,
          verificationUri: start.verificationUri,
        });
      } catch (err) {
        setError((err as Error).message);
      } finally {
        setLocalBusy(false);
      }
    })();
  };

  const handleCompleteDevice = () => {
    if (!deviceSession || !secret.trim()) {
      return;
    }
    void run(() =>
      completeOAuthDeviceCode(deviceSession.sessionId, { apiKey: secret.trim() }),
    );
  };

  const handleStartRedirect = () => {
    void (async () => {
      setLocalBusy(true);
      setError(null);
      try {
        const start = await startOAuthRedirect(connector.id);
        const fullUrl = resolveApiAuthorizeUrl(start.authorizeUrl);
        setRedirectState({ state: start.state, authorizeUrl: fullUrl });
        window.open(fullUrl, '_blank', 'noopener,noreferrer');
      } catch (err) {
        setError((err as Error).message);
      } finally {
        setLocalBusy(false);
      }
    })();
  };

  const handleCompleteRedirect = () => {
    if (!redirectState || !secret.trim()) {
      return;
    }
    void run(() => completeOAuthCallback(redirectState.state, secret.trim()));
  };

  const handleStoreToken = () => {
    if (!secret.trim()) {
      return;
    }
    void run(() => storeOAuthToken(connector.id, { apiKey: secret.trim() }));
  };

  const handleRevoke = () => {
    void (async () => {
      setLocalBusy(true);
      setError(null);
      try {
        const updated = await revokeConnector(connector.id);
        setCredentialMask(null);
        onRevoked?.(updated);
      } catch (err) {
        setError((err as Error).message);
      } finally {
        setLocalBusy(false);
      }
    })();
  };

  return (
    <div className="modal-backdrop" role="presentation" onClick={onCancel}>
      <div
        className="modal connector-auth-modal"
        role="dialog"
        aria-labelledby="connector-auth-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="modal-header">
          <h2 id="connector-auth-title">连接 {connector.name}</h2>
          <button type="button" className="btn ghost" onClick={onCancel} disabled={working}>
            ×
          </button>
        </header>
        <div className="modal-body">
          <p className="modal-copy">{connector.description}</p>
          {credentialMask && (
            <p className="connector-auth-mask">已保存凭据：{credentialMask}</p>
          )}

          {!connector.requiresAuth ? (
            <p className="market-hint">此连接器无需授权，可直接连接。</p>
          ) : (
            <>
              <ConnectorOAuthWalkthrough
                steps={walkthroughSteps}
                activeStep={walkthroughPhase}
              />

              <div className="connector-auth-tabs">
                {(authMethod === 'DEVICE_CODE' || authMethod === 'QR') && (
                  <button
                    type="button"
                    className={`market-pill${tab === 'device' ? ' active' : ''}`}
                    onClick={() => setTab('device')}
                  >
                    {authMethod === 'QR' ? '扫码授权' : '设备码'}
                  </button>
                )}
                {authMethod === 'REDIRECT' && (
                  <button
                    type="button"
                    className={`market-pill${tab === 'redirect' ? ' active' : ''}`}
                    onClick={() => setTab('redirect')}
                  >
                    重定向授权
                  </button>
                )}
                <button
                  type="button"
                  className={`market-pill${tab === 'token' ? ' active' : ''}`}
                  onClick={() => setTab('token')}
                >
                  粘贴令牌
                </button>
              </div>

              {tab === 'device' && (
                <div className="connector-auth-panel">
                  {!deviceSession ? (
                    <button
                      type="button"
                      className="btn primary"
                      disabled={working}
                      onClick={handleStartDevice}
                    >
                      生成{authMethod === 'QR' ? '二维码' : '设备码'}
                    </button>
                  ) : (
                    <>
                      <p className="connector-auth-code">
                        请在授权页输入设备码：<strong>{deviceSession.userCode}</strong>
                      </p>
                      {authMethod === 'QR' && (
                        <p className="market-hint">深链：{deviceSession.verificationUri}</p>
                      )}
                      <label className="connector-connect-field">
                        <span>{connector.authHint ?? 'API Key / 授权码'}</span>
                        <input
                          type="password"
                          className="connector-connect-input"
                          value={secret}
                          onChange={(event) => setSecret(event.target.value)}
                          placeholder="完成授权后粘贴密钥"
                          autoComplete="off"
                        />
                      </label>
                      <button
                        type="button"
                        className="btn primary"
                        disabled={working || !secret.trim()}
                        onClick={handleCompleteDevice}
                      >
                        完成授权并连接
                      </button>
                    </>
                  )}
                </div>
              )}

              {tab === 'redirect' && (
                <div className="connector-auth-panel">
                  {!redirectState ? (
                    <button
                      type="button"
                      className="btn primary"
                      disabled={working}
                      onClick={handleStartRedirect}
                    >
                      打开授权页
                    </button>
                  ) : (
                    <>
                      <p className="market-hint">
                        授权完成后粘贴授权码；桌面版可通过 workmate:// 深链自动回填。
                      </p>
                      <p className="market-hint">
                        <a href={redirectState.authorizeUrl} target="_blank" rel="noreferrer">
                          重新打开授权页
                        </a>
                      </p>
                      <label className="connector-connect-field">
                        <span>授权码</span>
                        <input
                          type="password"
                          className="connector-connect-input"
                          value={secret}
                          onChange={(event) => setSecret(event.target.value)}
                          placeholder="粘贴授权码或 API Key"
                          autoComplete="off"
                        />
                      </label>
                      <button
                        type="button"
                        className="btn primary"
                        disabled={working || !secret.trim()}
                        onClick={handleCompleteRedirect}
                      >
                        完成授权并连接
                      </button>
                    </>
                  )}
                </div>
              )}

              {tab === 'token' && (
                <div className="connector-auth-panel">
                  <label className="connector-connect-field">
                    <span>{connector.authHint ?? 'API Key / Token'}</span>
                    <input
                      type="password"
                      className="connector-connect-input"
                      value={secret}
                      onChange={(event) => setSecret(event.target.value)}
                      placeholder="粘贴 API Key 或 Bearer Token"
                      autoComplete="off"
                    />
                  </label>
                  <button
                    type="button"
                    className="btn primary"
                    disabled={working || !secret.trim()}
                    onClick={handleStoreToken}
                  >
                    保存并连接
                  </button>
                </div>
              )}
            </>
          )}

          {error && <p className="market-hint error">{error}</p>}

          {(credentialMask || connector.hasCredential) && (
            <section className="connector-auth-danger">
              <h3>危险操作</h3>
              <p className="market-hint">撤销授权将清除已保存凭据并断开连接。</p>
              <button
                type="button"
                className="btn danger"
                disabled={working}
                onClick={handleRevoke}
              >
                撤销授权
              </button>
            </section>
          )}
        </div>
        <footer className="modal-footer">
          <button type="button" className="btn secondary" disabled={working} onClick={onCancel}>
            取消
          </button>
          {!connector.requiresAuth && (
            <button type="button" className="btn primary" disabled={working} onClick={handleDirectConnect}>
              {working ? '连接中…' : '确认连接'}
            </button>
          )}
        </footer>
      </div>
    </div>
  );
}

import { useEffect, useState } from 'react';
import {
  connectConnector,
  disconnectConnector,
  listConnectors,
  reconnectConnector,
} from '../../api/market';
import type { ConnectorInfo } from '../../types/market';
import { TERM, marketCapabilityPillLabel } from '../../lib/terminology';
import { ConnectorAuthModal } from '../../components/connector/ConnectorAuthModal';
import { ConnectorSwitch } from '../../components/ConnectorSwitch';

interface ConnectorMarketTabProps {
  initialConnectors?: ConnectorInfo[];
  onConnectorsChange?: (connectors: ConnectorInfo[]) => void;
}

export function ConnectorMarketTab({
  initialConnectors,
  onConnectorsChange,
}: ConnectorMarketTabProps = {}) {
  const hasInitialConnectors = (initialConnectors?.length ?? 0) > 0;
  const [connectors, setConnectors] = useState<ConnectorInfo[]>(initialConnectors ?? []);
  const [loading, setLoading] = useState(!hasInitialConnectors);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [connectTarget, setConnectTarget] = useState<ConnectorInfo | null>(null);

  const applyConnectors = (next: ConnectorInfo[]) => {
    setConnectors(next);
    onConnectorsChange?.(next);
  };

  const load = (showSpinner = connectors.length === 0) => {
    setError(null);
    if (showSpinner) {
      setLoading(true);
    }
    void listConnectors()
      .then(applyConnectors)
      .catch((err) => setError((err as Error).message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (hasInitialConnectors) {
      return;
    }
    load(true);
  }, []);

  useEffect(() => {
    if (initialConnectors?.length) {
      setConnectors(initialConnectors);
      setLoading(false);
    }
  }, [initialConnectors]);

  const handleDisconnect = async (connector: ConnectorInfo) => {
    setBusyId(connector.id);
    setError(null);
    try {
      const updated = await disconnectConnector(connector.id);
      applyConnectors(connectors.map((c) => (c.id === updated.id ? updated : c)));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const handleReconnect = async (connector: ConnectorInfo) => {
    setBusyId(connector.id);
    setError(null);
    try {
      const updated = await reconnectConnector(connector.id);
      applyConnectors(connectors.map((c) => (c.id === updated.id ? updated : c)));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const handleConnected = (updated: ConnectorInfo) => {
    applyConnectors(connectors.map((c) => (c.id === updated.id ? updated : c)));
    setConnectTarget(null);
  };

  const handleRevoked = (updated: ConnectorInfo) => {
    applyConnectors(connectors.map((c) => (c.id === updated.id ? updated : c)));
  };

  const statusLabel = (connector: ConnectorInfo) => {
    if (!connector.runnable) {
      return '目录';
    }
    if (connector.status === 'connected') {
      return '已连接';
    }
    if (connector.status === 'error') {
      return '连接失败';
    }
    return '未连接';
  };

  const handleToggle = async (connector: ConnectorInfo, nextConnected: boolean) => {
    if (!connector.runnable) {
      setError('该连接器为目录项（CLI/待接入），Web 端暂不支持直接连接');
      return;
    }
    if (nextConnected) {
      if (connector.requiresAuth) {
        setConnectTarget(connector);
        return;
      }
      setBusyId(connector.id);
      setError(null);
      try {
        const updated = await connectConnector(connector.id);
        applyConnectors(connectors.map((c) => (c.id === updated.id ? updated : c)));
      } catch (err) {
        setError((err as Error).message);
      } finally {
        setBusyId(null);
      }
      return;
    }
    await handleDisconnect(connector);
  };

  return (
    <div className="market-tab-body">
      <p className="market-tab-intro">
        {TERM.connector}市场 · 连接外部应用与数据源（运行时 {TERM.runtimeMcp}）
      </p>
      <div className="market-toolbar">
        <button type="button" className="btn secondary" disabled title="即将推出">
          + 自定义{TERM.connector}
        </button>
      </div>

      {error && <p className="market-hint error">{error}</p>}
      {loading && connectors.length === 0 && <p className="market-hint">加载中…</p>}

      <div className="connector-grid">
        {connectors.map((connector) => (
          <article key={connector.id} className="connector-card market-capability-card">
            <header className="connector-card-header">
              <div className="connector-card-logo">{connector.name.slice(0, 1)}</div>
              <div className="connector-card-titles">
                <h3 className="market-capability-card__title">{connector.name}</h3>
                <ul className="market-meta-pills market-meta-pills-inline">
                  <li className="pill-connector">
                    {marketCapabilityPillLabel('connector', connector.id)}
                  </li>
                  {connector.toolCount != null && (
                    <li className="pill-muted">{connector.toolCount} 个工具</li>
                  )}
                </ul>
              </div>
              <ConnectorSwitch
                checked={connector.status === 'connected'}
                busy={busyId === connector.id}
                disabled={connector.status === 'error' || connector.runnable === false}
                label={statusLabel(connector)}
                onChange={(next) => void handleToggle(connector, next)}
              />
            </header>
            <div className="market-capability-card__body">
              <p className="market-capability-card__desc">{connector.description}</p>
              {(connector.invalidSchemaCount || connector.toolsLimitWarning) && (
                <ul className="market-meta-pills market-capability-card__pills">
                  {connector.invalidSchemaCount ? (
                    <li className="pill-warning">{connector.invalidSchemaCount} schema 无效</li>
                  ) : null}
                  {connector.toolsLimitWarning ? (
                    <li className="pill-warning">工具数较多</li>
                  ) : null}
                </ul>
              )}
              {connector.status === 'error' && connector.lastError && (
                <p className="market-hint error">{connector.lastError}</p>
              )}
            </div>
            <footer className="connector-card-actions market-capability-card__footer">
              {connector.status === 'error' && (
                <button
                  type="button"
                  className="btn primary"
                  disabled={busyId === connector.id}
                  onClick={() => void handleReconnect(connector)}
                >
                  {busyId === connector.id ? '重连中…' : '重连'}
                </button>
              )}
            </footer>
          </article>
        ))}
      </div>

      {!loading && connectors.length === 0 && (
        <p className="market-empty">
          暂无连接器。请在 workmate-api 启用 MCP（WORKMATE_MCP_ENABLED=true）。
        </p>
      )}

      <ConnectorAuthModal
        connector={connectTarget}
        busy={busyId === connectTarget?.id}
        onConnected={handleConnected}
        onRevoked={handleRevoked}
        onCancel={() => setConnectTarget(null)}
      />
    </div>
  );
}

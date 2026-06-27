import { useEffect, useMemo, useRef, useState } from 'react';
import { connectConnector, listConnectors, reconnectConnector } from '../../api/market';
import type { ConnectorInfo } from '../../types/market';
import { marketCapabilityPillLabel, TERM } from '../../lib/terminology';
import {
  loadRecentConnectorIds,
  sortConnectorsForPopover,
  touchRecentConnectorId,
} from '../../lib/connectorPopoverSort';
import { normalizeConnectorIds } from '../../lib/connectorId';
import { ConnectorSwitch } from '../ConnectorSwitch';
import { ConnectorAuthModal } from '../connector/ConnectorAuthModal';

interface ConnectorPopoverProps {
  disabled?: boolean;
  sessionId?: string | null;
  enabledConnectorIds?: string[];
  onEnabledConnectorIdsChange?: (connectorIds: string[]) => void;
  onManageAll?: () => void;
  catalogConnectors?: ConnectorInfo[];
  onCatalogConnectorsChange?: (connectors: ConnectorInfo[]) => void;
}

export function ConnectorPopover({
  disabled,
  sessionId,
  enabledConnectorIds = [],
  onEnabledConnectorIdsChange,
  onManageAll,
  catalogConnectors,
  onCatalogConnectorsChange,
}: ConnectorPopoverProps) {
  const [open, setOpen] = useState(false);
  const [connectors, setConnectors] = useState<ConnectorInfo[]>(catalogConnectors ?? []);
  const [loading, setLoading] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [connectTarget, setConnectTarget] = useState<ConnectorInfo | null>(null);
  const [recentConnectorIds, setRecentConnectorIds] = useState<string[]>(() => loadRecentConnectorIds());
  const rootRef = useRef<HTMLDivElement>(null);

  const markRecent = (connectorId: string) => {
    setRecentConnectorIds(touchRecentConnectorId(connectorId));
  };

  const applyConnectors = (next: ConnectorInfo[]) => {
    setConnectors(next);
    onCatalogConnectorsChange?.(next);
  };

  useEffect(() => {
    if (catalogConnectors?.length) {
      setConnectors(catalogConnectors);
    }
  }, [catalogConnectors]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const hasCache = (catalogConnectors?.length ?? connectors.length) > 0;
    setLoading(!hasCache);
    void listConnectors()
      .then(applyConnectors)
      .finally(() => setLoading(false));
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const onDocClick = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [open]);

  const normalizedEnabledIds = useMemo(
    () => normalizeConnectorIds(enabledConnectorIds ?? []),
    [enabledConnectorIds],
  );

  const setSessionEnabled = (connectorId: string, enabled: boolean) => {
    const current = normalizedEnabledIds;
    const next = enabled
      ? [...new Set([...current, connectorId])]
      : current.filter((id) => id !== connectorId);
    if (enabled) {
      markRecent(connectorId);
    }
    onEnabledConnectorIdsChange?.(next);
  };

  const sortedConnectors = useMemo(
    () => sortConnectorsForPopover(connectors, normalizedEnabledIds, recentConnectorIds),
    [connectors, normalizedEnabledIds, recentConnectorIds],
  );

  const enabledConnectors = useMemo(
    () => sortedConnectors.filter((connector) => normalizedEnabledIds.includes(connector.id)),
    [sortedConnectors, normalizedEnabledIds],
  );

  const availableConnectors = useMemo(
    () => sortedConnectors.filter((connector) => !normalizedEnabledIds.includes(connector.id)),
    [sortedConnectors, normalizedEnabledIds],
  );

  const scopeLabel = sessionId ? '本会话' : '新建任务';

  const ensureGatewayConnected = async (connector: ConnectorInfo): Promise<ConnectorInfo> => {
    if (connector.status === 'connected') {
      return connector;
    }
    if (connector.status === 'error') {
      return reconnectConnector(connector.id);
    }
    return connectConnector(connector.id);
  };

  const handleToggle = async (connector: ConnectorInfo, nextEnabled: boolean) => {
    if (!connector.runnable) {
      setError('该连接器为目录项（CLI/待接入），Web 端暂不支持直接连接');
      return;
    }

    setError(null);

    if (!nextEnabled) {
      setSessionEnabled(connector.id, false);
      return;
    }

    if (connector.requiresAuth && connector.status !== 'connected') {
      setConnectTarget(connector);
      return;
    }

    setBusyId(connector.id);
    try {
      const updated = await ensureGatewayConnected(connector);
      applyConnectors(connectors.map((c) => (c.id === updated.id ? updated : c)));
      if (updated.status === 'connected') {
        setSessionEnabled(connector.id, true);
      }
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const handleAuthConnected = (updated: ConnectorInfo) => {
    applyConnectors(connectors.map((c) => (c.id === updated.id ? updated : c)));
    setConnectTarget(null);
    if (updated.status === 'connected') {
      setSessionEnabled(updated.id, true);
    }
  };

  const gatewayStatusLabel = (connector: ConnectorInfo) => {
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

  const gatewayStatusClass = (connector: ConnectorInfo) => {
    if (!connector.runnable) {
      return 'status-disconnected';
    }
    if (connector.status === 'connected') {
      return 'status-connected';
    }
    if (connector.status === 'error') {
      return 'status-error';
    }
    return 'status-disconnected';
  };

  const renderGatewayStatus = (connector: ConnectorInfo, sessionEnabled: boolean) => {
    if (!connector.runnable) {
      return (
        <span className={`connector-popover-status ${gatewayStatusClass(connector)}`}>
          目录
        </span>
      );
    }
    if (!sessionEnabled) {
      if (connector.status === 'error') {
        return (
          <span className={`connector-popover-status ${gatewayStatusClass(connector)}`}>
            连接失败
          </span>
        );
      }
      return null;
    }
    return (
      <span className={`connector-popover-status ${gatewayStatusClass(connector)}`}>
        {gatewayStatusLabel(connector)}
      </span>
    );
  };

  const renderConnectorRow = (connector: ConnectorInfo) => {
    const sessionEnabled = normalizedEnabledIds.includes(connector.id);
    return (
      <div key={connector.id} className="dock-popover-connector-row">
        <div className="dock-popover-connector-main">
          <div className="dock-popover-connector-head">
            <span className="dock-popover-item-label">{connector.name}</span>
            {renderGatewayStatus(connector, sessionEnabled)}
          </div>
          <ul className="market-meta-pills market-meta-pills-inline dock-popover-meta-pills">
            <li className="pill-connector">
              {marketCapabilityPillLabel('connector', connector.id)}
            </li>
            {sessionEnabled && connector.toolCount != null && (
              <li className="pill-muted">{connector.toolCount} 个工具</li>
            )}
            {sessionEnabled && connector.invalidSchemaCount != null && connector.invalidSchemaCount > 0 && (
              <li className="pill-warning">
                {connector.invalidSchemaCount} schema 无效
              </li>
            )}
            {sessionEnabled && connector.toolsLimitWarning && (
              <li className="pill-warning">工具数较多</li>
            )}
            {!sessionEnabled && connector.runnable && connector.status !== 'error' && (
              <li className="pill-muted">开启后将自动连接</li>
            )}
          </ul>
          {connector.status === 'error' && connector.lastError && (
            <span className="dock-popover-item-error" title={connector.lastError}>
              {connector.lastError}
            </span>
          )}
        </div>
        <ConnectorSwitch
          compact
          checked={sessionEnabled}
          busy={busyId === connector.id}
          disabled={connector.runnable === false}
          label={sessionEnabled ? '已启用' : '未启用'}
          onChange={(next) => void handleToggle(connector, next)}
        />
      </div>
    );
  };

  const sessionEnabledCount = normalizedEnabledIds.length;

  return (
    <div className="dock-popover-anchor" ref={rootRef}>
      <button
        type="button"
        className={`dock-pill${open ? ' open' : ''}${sessionEnabledCount > 0 ? ' active' : ''}`}
        disabled={disabled}
        aria-expanded={open}
        onClick={() => setOpen((prev) => !prev)}
      >
        <span className="dock-pill-icon" aria-hidden>🔗</span>
        {TERM.connectApps}
        {sessionEnabledCount > 0 ? ` · ${sessionEnabledCount}` : ''} ▾
      </button>
      {open && (
        <div className="dock-popover dock-popover-wide dock-popover-connectors" role="menu">
          {error && <p className="dock-popover-hint error">{error}</p>}
          {loading && connectors.length === 0 && <p className="dock-popover-hint">加载中…</p>}
          {!loading || enabledConnectors.length > 0 || availableConnectors.length > 0 ? (
            <>
              <p className="dock-popover-section-title">
                {TERM.connectApps} · {scopeLabel}已启用
                {sessionEnabledCount > 0 ? ` (${sessionEnabledCount})` : ''}
              </p>
              {enabledConnectors.length > 0
                ? enabledConnectors.map(renderConnectorRow)
                : (
                  <p className="dock-popover-hint">
                    尚未启用连接器，可从下方添加；开启后 Agent 才能调用对应 MCP 工具。
                  </p>
                )}
              {availableConnectors.length > 0 && (
                <>
                  <div className="dock-popover-divider" />
                  <p className="dock-popover-section-title">添加连接器</p>
                  {availableConnectors.map(renderConnectorRow)}
                </>
              )}
            </>
          ) : null}
          {!loading && connectors.length === 0 && (
            <p className="dock-popover-hint">暂无可用 MCP 连接器</p>
          )}
          {onManageAll && (
            <>
              <div className="dock-popover-divider" />
              <button
                type="button"
                role="menuitem"
                className="dock-popover-item dock-popover-link"
                onClick={() => {
                  onManageAll();
                  setOpen(false);
                }}
              >
                管理全部连接器
              </button>
            </>
          )}
        </div>
      )}
      <ConnectorAuthModal
        connector={connectTarget}
        busy={busyId === connectTarget?.id}
        onConnected={handleAuthConnected}
        onRevoked={(updated) => {
          applyConnectors(connectors.map((c) => (c.id === updated.id ? updated : c)));
        }}
        onCancel={() => setConnectTarget(null)}
      />
    </div>
  );
}

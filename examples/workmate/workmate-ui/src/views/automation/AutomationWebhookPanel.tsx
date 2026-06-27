import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  getWebhookConfig,
  listWebhookDeliveries,
  sendWebhookTest,
  type WebhookChannelConfig,
  type WebhookDelivery,
} from '../../api/automation';
import { formatRelativeTime } from '../../lib/formatRelativeTime';
import { getTenantQuota } from '../../api/tenant';
import { formatQuotaExceededToast, isQuotaOrSessionLimitError } from '../../lib/quotaAlert';

const API_BASE = import.meta.env.VITE_API_BASE ?? '';

function resolveWebhookUrl(path: string): string {
  if (API_BASE) {
    return `${API_BASE.replace(/\/$/, '')}${path}`;
  }
  if (typeof window !== 'undefined') {
    return `${window.location.origin}${path}`;
  }
  return path;
}

export function AutomationWebhookPanel() {
  const [channels, setChannels] = useState<WebhookChannelConfig[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [channelId, setChannelId] = useState('generic');
  const [secret, setSecret] = useState('');
  const [text, setText] = useState('生成今日市场摘要');
  const [title, setTitle] = useState('Webhook 测试');
  const [testBusy, setTestBusy] = useState(false);
  const [testResult, setTestResult] = useState<string | null>(null);
  const [copyToast, setCopyToast] = useState<string | null>(null);
  const [quotaToast, setQuotaToast] = useState<string | null>(null);
  const [deliveries, setDeliveries] = useState<WebhookDelivery[]>([]);
  const [deliveriesLoading, setDeliveriesLoading] = useState(false);

  const refreshDeliveries = useCallback(async () => {
    setDeliveriesLoading(true);
    try {
      setDeliveries(await listWebhookDeliveries(20));
    } catch {
      setDeliveries([]);
    } finally {
      setDeliveriesLoading(false);
    }
  }, []);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const config = await getWebhookConfig();
      setChannels(config.channels);
      if (config.channels.length > 0 && !config.channels.some((c) => c.id === channelId)) {
        setChannelId(config.channels[0].id);
      }
      await refreshDeliveries();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [channelId, refreshDeliveries]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const selected = useMemo(
    () => channels.find((channel) => channel.id === channelId) ?? channels[0] ?? null,
    [channels, channelId],
  );

  const webhookUrl = selected ? resolveWebhookUrl(selected.path) : '';

  const handleCopyUrl = async () => {
    if (!webhookUrl) {
      return;
    }
    try {
      await navigator.clipboard.writeText(webhookUrl);
      setCopyToast('已复制 URL');
      window.setTimeout(() => setCopyToast(null), 2000);
    } catch {
      setCopyToast('复制失败');
      window.setTimeout(() => setCopyToast(null), 2000);
    }
  };

  const handleTest = async () => {
    if (!selected) {
      return;
    }
    setTestBusy(true);
    setTestResult(null);
    setError(null);
    try {
      const body =
        selected.id === 'feishu'
          ? { challenge: 'workmate-ui-test', token: secret || undefined }
          : { text: text.trim(), title: title.trim() || 'Webhook 测试' };
      const result = await sendWebhookTest(selected.id, body, secret || undefined);
      setTestResult(JSON.stringify(result, null, 2));
      await refreshDeliveries();
    } catch (err) {
      const message = (err as Error).message;
      if (isQuotaOrSessionLimitError(message)) {
        try {
          const quota = await getTenantQuota();
          setQuotaToast(formatQuotaExceededToast(quota, message));
        } catch {
          setQuotaToast(message);
        }
        window.setTimeout(() => setQuotaToast(null), 5000);
      } else {
        setError(message);
      }
    } finally {
      setTestBusy(false);
    }
  };

  return (
    <section className="nav-shell-section automation-webhook-panel" aria-label="IM Webhook">
      <h2 className="nav-shell-section-title">IM Webhook（W42 / W46）</h2>
      <p className="muted automation-webhook-hint">
        将飞书、企微或通用 HTTP 回调指向下方 URL。密钥通过环境变量配置，界面仅显示是否已设置。
      </p>

      {error && <p className="market-hint error" role="alert">{error}</p>}
      {quotaToast && <p className="app-archive-toast automation-webhook-quota-toast" role="alert">{quotaToast}</p>}
      {copyToast && <p className="automation-webhook-toast" role="status">{copyToast}</p>}

      {loading ? (
        <p className="muted">加载 Webhook 配置…</p>
      ) : channels.length === 0 ? (
        <p className="muted">未配置 Webhook 通道。</p>
      ) : (
        <>
          <ul className="automation-webhook-channel-list">
            {channels.map((channel) => (
              <li key={channel.id} className="automation-webhook-channel-card">
                <div className="automation-webhook-channel-head">
                  <strong>{channel.id}</strong>
                  <span className={`automation-webhook-badge${channel.enabled ? ' enabled' : ''}`}>
                    {channel.enabled ? '已启用' : '未启用'}
                  </span>
                  <span className="muted">
                    密钥 {channel.secretConfigured ? '已配置' : '未配置（可选）'}
                  </span>
                </div>
                <code className="automation-webhook-url">{resolveWebhookUrl(channel.path)}</code>
              </li>
            ))}
          </ul>

          <div className="automation-webhook-test">
            <h3 className="automation-webhook-test-title">发送测试</h3>
            <label className="connector-connect-field">
              <span>通道</span>
              <select
                className="connector-connect-input"
                value={channelId}
                onChange={(e) => setChannelId(e.target.value)}
              >
                {channels.map((channel) => (
                  <option key={channel.id} value={channel.id}>
                    {channel.id}
                  </option>
                ))}
              </select>
            </label>
            <label className="connector-connect-field">
              <span>Webhook 密钥（Header: X-WorkMate-Webhook-Secret）</span>
              <input
                className="connector-connect-input"
                type="password"
                value={secret}
                onChange={(e) => setSecret(e.target.value)}
                placeholder={selected?.secretConfigured ? '输入服务端配置的密钥' : '未配置时可留空'}
                autoComplete="off"
              />
            </label>
            {selected?.id === 'feishu' ? (
              <p className="muted">飞书测试将发送 challenge 校验包（不会创建会话）。</p>
            ) : (
              <>
                <label className="connector-connect-field">
                  <span>标题</span>
                  <input
                    className="connector-connect-input"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                  />
                </label>
                <label className="connector-connect-field">
                  <span>消息 text</span>
                  <textarea
                    className="connector-connect-input automation-prompt-input"
                    rows={2}
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                  />
                </label>
              </>
            )}
            <div className="automation-webhook-test-actions">
              <button
                type="button"
                className="btn secondary"
                disabled={!webhookUrl}
                onClick={() => void handleCopyUrl()}
              >
                复制 URL
              </button>
              <button
                type="button"
                className="btn primary"
                disabled={testBusy || !selected?.enabled}
                onClick={() => void handleTest()}
              >
                {testBusy ? '发送中…' : '发送测试'}
              </button>
            </div>
            {testResult && (
              <pre className="nav-shell-code-block automation-webhook-result">{testResult}</pre>
            )}
          </div>

          <section className="automation-webhook-deliveries" aria-label="Webhook 触发历史">
            <div className="automation-webhook-deliveries-head">
              <h3 className="automation-webhook-test-title">最近触发</h3>
              <button
                type="button"
                className="btn ghost sm"
                disabled={deliveriesLoading}
                onClick={() => void refreshDeliveries()}
              >
                {deliveriesLoading ? '刷新中…' : '刷新'}
              </button>
            </div>
            {deliveriesLoading && deliveries.length === 0 ? (
              <p className="muted">加载触发记录…</p>
            ) : deliveries.length === 0 ? (
              <p className="muted">暂无 Webhook 触发记录</p>
            ) : (
              <ul className="automation-webhook-delivery-list">
                {deliveries.map((delivery) => (
                  <li key={delivery.id} className="automation-webhook-delivery-item">
                    <div className="automation-webhook-delivery-head">
                      <strong>{delivery.channel}</strong>
                      <span className={`automation-webhook-outcome outcome-${delivery.outcome.toLowerCase()}`}>
                        {delivery.outcome}
                      </span>
                      <time className="muted" dateTime={delivery.createdAt}>
                        {formatRelativeTime(delivery.createdAt)}
                      </time>
                    </div>
                    {delivery.message && <p className="muted">{delivery.message}</p>}
                    {delivery.sessionId && (
                      <p className="mono muted">session: {delivery.sessionId.slice(0, 8)}…</p>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </section>
  );
}

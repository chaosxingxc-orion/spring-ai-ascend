import { useCallback, useEffect, useState } from 'react';
import {
  getSecurityPolicy,
  resetSecurityPolicy,
  runNetworkCheck,
  updateSecurityPolicy,
  type NetworkCheckReport,
  type SecurityPolicy,
} from '../../api/security';

function linesToList(value: string): string[] {
  return value
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);
}

function listToLines(values: string[]): string {
  return values.join('\n');
}

export function SecurityCenterPanel({ onOpenAudit }: { onOpenAudit?: () => void }) {
  const [policy, setPolicy] = useState<SecurityPolicy | null>(null);
  const [network, setNetwork] = useState<NetworkCheckReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [checkingNetwork, setCheckingNetwork] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setPolicy(await getSecurityPolicy());
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const handleSave = async () => {
    if (!policy) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      setPolicy(await updateSecurityPolicy(policy));
      setMessage('安全策略已保存');
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const handleReset = async () => {
    if (!window.confirm('恢复默认安全策略？')) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      setPolicy(await resetSecurityPolicy());
      setMessage('已恢复默认策略');
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const handleNetworkCheck = async () => {
    setCheckingNetwork(true);
    setError(null);
    try {
      setNetwork(await runNetworkCheck());
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setCheckingNetwork(false);
    }
  };

  const updateListField = (field: keyof SecurityPolicy, value: string) => {
    if (!policy) {
      return;
    }
    setPolicy({ ...policy, [field]: linesToList(value) });
  };

  return (
    <div className="settings-panel security-center-panel">
      <p className="settings-panel-desc">
        配置域名与命令策略，并诊断出站网络连通性。高危 bash 命令仍会走审批流程。
      </p>

      {error && <p className="memory-settings-error" role="alert">{error}</p>}
      {message && <p className="settings-field-hint" role="status">{message}</p>}

      {loading ? (
        <p className="memory-settings-empty">加载中…</p>
      ) : policy ? (
        <>
          <section className="security-policy-section">
            <h3>域名策略</h3>
            <label className="settings-field">
              <span className="settings-field-label">允许列表（每行一条，空=不限制）</span>
              <textarea
                rows={3}
                value={listToLines(policy.domainAllowList)}
                onChange={(event) => updateListField('domainAllowList', event.target.value)}
              />
            </label>
            <label className="settings-field">
              <span className="settings-field-label">拒绝列表</span>
              <textarea
                rows={3}
                value={listToLines(policy.domainDenyList)}
                onChange={(event) => updateListField('domainDenyList', event.target.value)}
              />
            </label>
          </section>

          <section className="security-policy-section">
            <h3>Bash 命令策略</h3>
            <label className="settings-field">
              <span className="settings-field-label">需审批模式（正则，每行一条）</span>
              <textarea
                rows={4}
                value={listToLines(policy.bashAskPatterns)}
                onChange={(event) => updateListField('bashAskPatterns', event.target.value)}
              />
            </label>
            <label className="settings-field">
              <span className="settings-field-label">阻断模式（正则，每行一条）</span>
              <textarea
                rows={4}
                value={listToLines(policy.bashBlockPatterns)}
                onChange={(event) => updateListField('bashBlockPatterns', event.target.value)}
              />
            </label>
            <label className="settings-field">
              <span className="settings-field-label">文件阻断路径模式</span>
              <textarea
                rows={3}
                value={listToLines(policy.fileBlockPatterns)}
                onChange={(event) => updateListField('fileBlockPatterns', event.target.value)}
              />
            </label>
          </section>

          <div className="settings-actions-row">
            <button type="button" className="btn primary" disabled={saving} onClick={() => void handleSave()}>
              {saving ? '保存中…' : '保存策略'}
            </button>
            <button type="button" className="btn ghost" disabled={saving} onClick={() => void handleReset()}>
              恢复默认
            </button>
            {onOpenAudit && (
              <button type="button" className="btn ghost" onClick={onOpenAudit}>
                打开审计日志
              </button>
            )}
          </div>

          <section className="security-network-section">
            <h3>网络诊断</h3>
            <p className="settings-field-hint">检测本地 API 与公网出站连通性，并显示代理配置。</p>
            <button
              type="button"
              className="btn ghost"
              disabled={checkingNetwork}
              onClick={() => void handleNetworkCheck()}
            >
              {checkingNetwork ? '诊断中…' : '运行网络诊断'}
            </button>
            {network && (
              <div className="security-network-report">
                <p>代理模式：{network.proxyMode}</p>
                <ul>
                  {network.checks.map((check) => (
                    <li key={check.target}>
                      {check.reachable ? '✓' : '✗'} {check.target} — {check.detail} ({check.latencyMs}ms)
                      {check.policyAllowed === false ? ' · 域名策略拦截' : ''}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </section>
        </>
      ) : null}
    </div>
  );
}

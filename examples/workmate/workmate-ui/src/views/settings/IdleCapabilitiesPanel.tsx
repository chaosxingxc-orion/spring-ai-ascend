import { useCallback, useEffect, useState } from 'react';
import { listIdleCapabilities, type IdleCapabilityItem } from '../../api/capability';

/** W47-C5 — read-only idle connector/skill suggestions in settings (90 days). */
export function IdleCapabilitiesPanel() {
  const [items, setItems] = useState<IdleCapabilityItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await listIdleCapabilities(90));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return (
    <section className="settings-panel idle-capabilities-panel">
      <h3>闲置能力建议</h3>
      <p className="settings-panel-desc">
        以下连接器或技能已安装但超过 90 天未使用。此为只读建议，可在市场或连接器页手动禁用。
      </p>
      {error && <p className="memory-settings-error" role="alert">{error}</p>}
      {loading ? (
        <p className="memory-settings-empty">加载中…</p>
      ) : items.length === 0 ? (
        <p className="memory-settings-empty">暂无闲置能力。</p>
      ) : (
        <ul className="idle-capabilities-settings-list">
          {items.map((item) => (
            <li key={`${item.type}:${item.id}`}>
              <strong>{item.name}</strong>
              <span className="idle-capability-meta">
                {item.type} · {item.idleDays >= 999 ? '从未使用' : `${item.idleDays} 天未用`}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

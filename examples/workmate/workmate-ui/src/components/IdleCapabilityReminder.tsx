import { useEffect, useState } from 'react';
import { disableIdleCapability, listIdleCapabilities, type IdleCapabilityItem } from '../api/capability';

interface IdleCapabilityReminderProps {
  onDismiss?: () => void;
}

export function IdleCapabilityReminder({ onDismiss }: IdleCapabilityReminderProps) {
  const [items, setItems] = useState<IdleCapabilityItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyKey, setBusyKey] = useState<string | null>(null);
  const [hidden, setHidden] = useState(false);

  useEffect(() => {
    void listIdleCapabilities(30)
      .then(setItems)
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, []);

  if (hidden || loading || items.length === 0) {
    return null;
  }

  const handleDisable = async (item: IdleCapabilityItem) => {
    const key = `${item.type}:${item.id}`;
    setBusyKey(key);
    try {
      await disableIdleCapability(item.type, item.id);
      setItems((prev) => prev.filter((entry) => `${entry.type}:${entry.id}` !== key));
    } finally {
      setBusyKey(null);
    }
  };

  const handleDismiss = () => {
    setHidden(true);
    onDismiss?.();
  };

  return (
    <aside className="idle-capability-reminder" role="status">
      <header>
        <strong>闲置能力提醒</strong>
        <button type="button" className="btn ghost compact" onClick={handleDismiss}>
          稍后
        </button>
      </header>
      <p>以下能力已安装/连接但超过 30 天未使用，可考虑禁用以简化环境。</p>
      <ul>
        {items.slice(0, 5).map((item) => {
          const key = `${item.type}:${item.id}`;
          return (
            <li key={key}>
              <span>{item.name}</span>
              <span className="idle-capability-meta">{item.type} · {item.idleDays >= 999 ? '从未使用' : `${item.idleDays} 天未用`}</span>
              <button
                type="button"
                className="btn ghost compact"
                disabled={busyKey === key}
                onClick={() => void handleDisable(item)}
              >
                {busyKey === key ? '…' : '禁用'}
              </button>
            </li>
          );
        })}
      </ul>
    </aside>
  );
}

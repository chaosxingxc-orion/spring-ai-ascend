import { useCallback, useEffect, useState } from 'react';
import { clearMemory, getMemory, updateMemorySettings } from '../../api/client';
import type { MemoryStatus } from '../../types/api';
import { MEMORY_CONTENT_HASH } from '../../lib/paths';

/** Memory settings panel — embedded in SettingsView or standalone page. */
export function MemorySettingsPanel() {
  const [memory, setMemory] = useState<MemoryStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [clearing, setClearing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setMemory(await getMemory());
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (loading || !memory) {
      return;
    }
    if (window.location.hash.replace(/^#/, '') !== MEMORY_CONTENT_HASH) {
      return;
    }
    const node = document.getElementById(MEMORY_CONTENT_HASH);
    node?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [loading, memory]);

  const handleToggleEnabled = async () => {
    if (!memory) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      setMemory(await updateMemorySettings(!memory.enabled, memory.autoCapture));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const handleToggleAutoCapture = async () => {
    if (!memory) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      setMemory(await updateMemorySettings(memory.enabled, !memory.autoCapture));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const handleClear = async () => {
    if (!window.confirm('确定清除全部长期记忆？此操作不可恢复。')) {
      return;
    }
    setClearing(true);
    setError(null);
    try {
      setMemory(await clearMemory());
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setClearing(false);
    }
  };

  return (
    <div className="settings-panel memory-settings-panel">
      <p className="settings-panel-desc">
        跨会话记住你的偏好与稳定事实。默认关闭，开启后可在新会话顶部看到已加载的记忆。
      </p>

      {error && <p className="memory-settings-error" role="alert">{error}</p>}

      {loading ? (
        <p className="memory-settings-empty">加载中…</p>
      ) : memory ? (
        <>
          <section className="memory-settings-controls">
            <label className="memory-settings-toggle">
              <input
                type="checkbox"
                checked={memory.enabled}
                disabled={saving}
                onChange={() => void handleToggleEnabled()}
              />
              <span>启用长期记忆</span>
            </label>
            <label className="memory-settings-toggle">
              <input
                type="checkbox"
                checked={memory.autoCapture}
                disabled={saving || !memory.enabled}
                onChange={() => void handleToggleAutoCapture()}
              />
              <span>会话结束后自动沉淀</span>
            </label>
            <button
              type="button"
              className="memory-settings-clear"
              disabled={clearing || !memory.hasContent}
              onClick={() => void handleClear()}
            >
              {clearing ? '清除中…' : '清除全部记忆'}
            </button>
          </section>

          <section id={MEMORY_CONTENT_HASH} className="memory-settings-content">
            <h2>当前记忆（{memory.charCount} 字）</h2>
            {memory.content?.trim() ? (
              <pre className="memory-settings-preview">{memory.content}</pre>
            ) : (
              <p className="memory-settings-empty">暂无记忆内容</p>
            )}
          </section>
        </>
      ) : null}
    </div>
  );
}

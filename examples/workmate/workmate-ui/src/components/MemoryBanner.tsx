import { useState } from 'react';
import { MemoryPreviewBlock } from './canvas/MemoryPreviewBlock';

interface MemoryBannerProps {
  enabled: boolean;
  injectPreview?: string;
  compact?: boolean;
  onOpenSettings?: () => void;
  onViewMemory?: () => void;
  onRememberSession?: () => void;
  rememberBusy?: boolean;
  canRememberSession?: boolean;
}

/** 会话顶部「记忆已加载 / 记忆已开启」可折叠条 */
export function MemoryBanner({
  enabled,
  injectPreview,
  compact = false,
  onOpenSettings,
  onViewMemory,
  onRememberSession,
  rememberBusy = false,
  canRememberSession = false,
}: MemoryBannerProps) {
  const [expanded, setExpanded] = useState(false);

  if (!enabled) {
    return null;
  }

  const hasInjectPreview = Boolean(injectPreview?.trim());
  const showRemember = Boolean(onRememberSession && canRememberSession);
  const allowExpand = hasInjectPreview || Boolean(onOpenSettings);

  if (compact && !hasInjectPreview) {
    return (
      <section className="memory-banner memory-banner-compact" aria-label="长期记忆">
        <div className="memory-banner-header">
          <span className="memory-banner-icon" aria-hidden>🧠</span>
          <span className="memory-banner-label">记忆已开启</span>
          {onViewMemory && (
            <button type="button" className="memory-banner-view-btn" onClick={onViewMemory}>
              查看记忆
            </button>
          )}
        </div>
      </section>
    );
  }

  return (
    <section className="memory-banner" aria-label="长期记忆">
      <div className="memory-banner-header">
        <button
          type="button"
          className="memory-banner-toggle"
          aria-expanded={expanded}
          onClick={() => allowExpand && setExpanded((open) => !open)}
          disabled={!allowExpand}
        >
          <span className="memory-banner-icon" aria-hidden>🧠</span>
          <span className="memory-banner-label">
            {hasInjectPreview ? '记忆已加载' : '记忆已开启'}
          </span>
          <span className="memory-banner-chevron" aria-hidden>{expanded ? '▲' : '▼'}</span>
        </button>
        {showRemember && (
          <button
            type="button"
            className="memory-banner-remember-btn"
            disabled={rememberBusy}
            onClick={onRememberSession}
          >
            {rememberBusy ? '记住中…' : '记住本次对话'}
          </button>
        )}
        {onViewMemory && (
          <button type="button" className="memory-banner-view-btn" onClick={onViewMemory}>
            查看记忆
          </button>
        )}
      </div>
      {expanded && (
        <div className="memory-banner-body">
          {hasInjectPreview ? (
            <MemoryPreviewBlock section="注入内容" preview={injectPreview!} />
          ) : (
            <p className="memory-banner-hint">
              开启后，新会话会自动注入已保存的偏好与事实。点击「记住本次对话」可手动沉淀当前会话。
            </p>
          )}
          {onOpenSettings && (
            <button type="button" className="memory-banner-settings-link" onClick={onOpenSettings}>
              管理记忆设置
            </button>
          )}
        </div>
      )}
    </section>
  );
}

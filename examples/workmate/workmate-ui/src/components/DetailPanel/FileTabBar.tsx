import { useCallback, useEffect, useState, type RefObject } from 'react';

export type FileTabPreviewMode = 'browser' | 'report' | 'source' | null;

interface FileTabBarProps {
  tabs: string[];
  activePath: string | null;
  previewMode?: FileTabPreviewMode;
  previewRootRef?: RefObject<HTMLElement | null>;
  onSelect: (path: string) => void;
  onClose: (path: string) => void;
}

function tabLabel(path: string): string {
  const parts = path.split('/');
  return parts[parts.length - 1] || path;
}

/** W50-F4 — fullscreen preview with Esc exit + state sync. */
export function FileTabBar({
  tabs,
  activePath,
  previewMode = null,
  previewRootRef,
  onSelect,
  onClose,
}: FileTabBarProps) {
  const [isFullscreen, setIsFullscreen] = useState(false);

  useEffect(() => {
    const onFullscreenChange = () => {
      setIsFullscreen(Boolean(document.fullscreenElement));
    };
    document.addEventListener('fullscreenchange', onFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', onFullscreenChange);
  }, []);

  useEffect(() => {
    if (!isFullscreen) {
      return undefined;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && document.fullscreenElement) {
        void document.exitFullscreen();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [isFullscreen]);

  const handleFullscreen = useCallback(async () => {
    const root = previewRootRef?.current;
    if (!root) {
      return;
    }
    if (document.fullscreenElement) {
      await document.exitFullscreen();
      return;
    }
    await root.requestFullscreen();
  }, [previewRootRef]);

  if (tabs.length === 0) {
    return (
      <header className="detail-tab-bar detail-tab-bar-empty">
        <span className="detail-tab-placeholder">选择产物预览</span>
      </header>
    );
  }

  const showPreviewBadge = Boolean(activePath && previewMode === 'browser');

  return (
    <header className="detail-tab-bar">
      <div className="detail-tab-scroll">
        {tabs.map((path) => (
          <div
            key={path}
            className={[
              'detail-tab',
              activePath === path ? 'active' : '',
              activePath === path && previewMode === 'browser' ? 'preview-active' : '',
            ].filter(Boolean).join(' ')}
          >
            <button
              type="button"
              className="detail-tab-label"
              onClick={() => onSelect(path)}
              title={path}
            >
              {tabLabel(path)}
            </button>
            <button
              type="button"
              className="detail-tab-close"
              aria-label={`关闭 ${tabLabel(path)}`}
              onClick={() => onClose(path)}
            >
              ×
            </button>
          </div>
        ))}
        {showPreviewBadge && (
          <span className="detail-tab-preview-badge" title="浏览器预览">
            预览
          </span>
        )}
      </div>
      {previewRootRef && activePath && (
        <button
          type="button"
          className="detail-tab-action"
          aria-label={isFullscreen ? '退出全屏' : '全屏预览'}
          title={isFullscreen ? '退出全屏 (Esc)' : '全屏预览'}
          onClick={() => void handleFullscreen()}
        >
          {isFullscreen ? '⤢' : '⛶'}
        </button>
      )}
    </header>
  );
}

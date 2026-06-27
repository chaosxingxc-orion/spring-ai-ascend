import { buildArtifactPreviewUrl } from '../../lib/previewUrl';

interface BrowserPreviewProps {
  sessionId: string;
  path: string;
  onViewSource?: () => void;
}

export function BrowserPreview({ sessionId, path, onViewSource }: BrowserPreviewProps) {
  const src = buildArtifactPreviewUrl(sessionId, path);

  return (
    <div className="browser-preview">
      <header className="browser-preview-bar no-print">
        <code className="browser-preview-url" title={src}>{src}</code>
        <div className="browser-preview-actions">
          <button
            type="button"
            className="btn ghost sm"
            onClick={() => void navigator.clipboard.writeText(src)}
          >
            复制地址
          </button>
          {onViewSource && (
            <button type="button" className="btn ghost sm" onClick={onViewSource}>
              查看源码
            </button>
          )}
        </div>
      </header>
      <iframe
        className="browser-preview-frame"
        src={src}
        sandbox="allow-scripts"
        title={`预览 ${path}`}
      />
    </div>
  );
}

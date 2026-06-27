import { useState } from 'react';
import { buildArtifactPreviewUrl } from '../../lib/previewUrl';

interface ImagePreviewProps {
  sessionId: string;
  path: string;
  alt?: string;
}

/** W39-C2 — 图片 fit 预览（浅色棋盘底，避免「全黑」误判）。 */
export function ImagePreview({ sessionId, path, alt }: ImagePreviewProps) {
  const src = buildArtifactPreviewUrl(sessionId, path);
  const fileName = path.split('/').pop() ?? path;
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');

  return (
    <div className="media-preview media-preview-image">
      <header className="media-preview-bar no-print">
        <span className="media-preview-filename" title={path}>{fileName}</span>
      </header>
      <div className="media-preview-image-stage" aria-busy={status === 'loading'}>
        {status === 'loading' && <p className="detail-hint muted media-preview-status">加载图片…</p>}
        {status === 'error' && (
          <p className="detail-hint error media-preview-status">图片加载失败，请检查文件是否存在。</p>
        )}
        <img
          src={src}
          alt={alt ?? path}
          className="media-preview-image-el"
          hidden={status !== 'ready'}
          onLoad={() => setStatus('ready')}
          onError={() => setStatus('error')}
        />
      </div>
    </div>
  );
}

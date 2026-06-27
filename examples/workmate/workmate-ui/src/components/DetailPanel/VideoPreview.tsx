import { useState } from 'react';
import { buildArtifactPreviewUrl } from '../../lib/previewUrl';

interface VideoPreviewProps {
  sessionId: string;
  path: string;
  mime?: string;
}

/** W39-C5 — 常见视频格式预览。 */
export function VideoPreview({ sessionId, path, mime }: VideoPreviewProps) {
  const src = buildArtifactPreviewUrl(sessionId, path);
  const fileName = path.split('/').pop() ?? path;
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');

  return (
    <div className="media-preview media-preview-video">
      <header className="media-preview-bar no-print">
        <span className="media-preview-filename" title={path}>{fileName}</span>
      </header>
      <div className="media-preview-video-stage">
        {status === 'loading' && <p className="detail-hint muted media-preview-status">加载视频…</p>}
        {status === 'error' && (
          <p className="detail-hint error media-preview-status">视频加载失败，请检查格式或文件大小。</p>
        )}
        <video
          className="media-preview-video-el"
          controls
          src={src}
          hidden={status !== 'ready'}
          onLoadedData={() => setStatus('ready')}
          onError={() => setStatus('error')}
        >
          {mime && <source src={src} type={mime} />}
          当前浏览器不支持视频预览。
        </video>
      </div>
    </div>
  );
}

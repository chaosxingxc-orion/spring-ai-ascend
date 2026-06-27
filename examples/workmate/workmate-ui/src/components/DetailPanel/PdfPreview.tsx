import { useState } from 'react';
import { buildArtifactPreviewUrl } from '../../lib/previewUrl';

interface PdfPreviewProps {
  sessionId: string;
  path: string;
}

/** W39-C3 / W50-F3 — PDF iframe preview with loading + error states. */
export function PdfPreview({ sessionId, path }: PdfPreviewProps) {
  const src = buildArtifactPreviewUrl(sessionId, path);
  const fileName = path.split('/').pop() ?? path;
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');

  return (
    <div className="media-preview media-preview-pdf">
      <header className="media-preview-bar no-print">
        <span className="media-preview-filename" title={path}>{fileName}</span>
        <a className="btn ghost sm" href={src} target="_blank" rel="noopener noreferrer">
          新窗口打开
        </a>
      </header>
      <div className="media-preview-pdf-stage" aria-busy={status === 'loading'}>
        {status === 'loading' && <p className="detail-hint muted media-preview-status">加载 PDF…</p>}
        {status === 'error' && (
          <p className="detail-hint error media-preview-status">
            PDF 预览失败。请使用「新窗口打开」或检查文件是否超过 10MB。
          </p>
        )}
        <iframe
          className="media-preview-pdf-frame"
          src={src}
          sandbox="allow-scripts"
          title={`PDF ${path}`}
          hidden={status === 'error'}
          onLoad={() => setStatus('ready')}
          onError={() => setStatus('error')}
        />
      </div>
    </div>
  );
}

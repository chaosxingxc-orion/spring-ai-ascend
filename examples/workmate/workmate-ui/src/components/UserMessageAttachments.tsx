import type { UserAttachment } from '../types/events';
import { buildArtifactPreviewUrl } from '../lib/previewUrl';
import { imageAttachments } from '../lib/userAttachments';

interface UserMessageAttachmentsProps {
  sessionId?: string;
  attachments?: UserAttachment[];
}

/** R1 — 用户消息图片附件预览（workspace 路径 → preview URL） */
export function UserMessageAttachments({ sessionId, attachments }: UserMessageAttachmentsProps) {
  const images = imageAttachments(attachments);
  if (images.length === 0 || !sessionId) {
    return null;
  }

  return (
    <div className="user-message-attachments" aria-label="消息附件">
      {images.map((item) => {
        const src = buildArtifactPreviewUrl(sessionId, item.path);
        const label = item.name ?? item.path;
        return (
          <a
            key={`${item.path}-${label}`}
            className="user-message-attachment"
            href={src}
            target="_blank"
            rel="noopener noreferrer"
            title={label}
          >
            <img src={src} alt={label} className="user-message-attachment-img" loading="lazy" />
          </a>
        );
      })}
    </div>
  );
}

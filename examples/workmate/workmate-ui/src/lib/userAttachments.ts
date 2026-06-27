import type { UserAttachment } from '../types/events';

const IMAGE_MIME = /^image\//i;
const IMAGE_EXT = /\.(png|jpe?g|gif|webp|svg)$/i;

function isRecord(value: unknown): value is Record<string, unknown> {
  return value != null && typeof value === 'object' && !Array.isArray(value);
}

function readString(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function isImageAttachment(path: string, mime?: string): boolean {
  if (mime && IMAGE_MIME.test(mime)) {
    return true;
  }
  return IMAGE_EXT.test(path);
}

function normalizeAttachment(raw: Record<string, unknown>): UserAttachment | null {
  const path = readString(raw.path) ?? readString(raw.url) ?? readString(raw.uri);
  if (!path) {
    return null;
  }
  const name = readString(raw.name) ?? path.split('/').pop();
  const mime = readString(raw.mime) ?? readString(raw.contentType);
  return { path, name, mime };
}

export function parseUserAttachments(raw: unknown): UserAttachment[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw
    .filter(isRecord)
    .map(normalizeAttachment)
    .filter((item): item is UserAttachment => item != null);
}

export function imageAttachments(attachments: UserAttachment[] | undefined): UserAttachment[] {
  if (!attachments?.length) {
    return [];
  }
  return attachments.filter((item) => isImageAttachment(item.path, item.mime));
}

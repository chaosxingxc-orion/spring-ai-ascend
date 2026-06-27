import type { UserAttachment } from '../types/events';

const API_BASE = import.meta.env.VITE_API_BASE ?? '';

export async function uploadSessionAttachment(
  sessionId: string,
  file: File,
): Promise<UserAttachment> {
  const form = new FormData();
  form.append('file', file);
  const response = await fetch(`${API_BASE}/api/v1/sessions/${sessionId}/attachments`, {
    method: 'POST',
    body: form,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `HTTP ${response.status}`);
  }
  return response.json() as Promise<UserAttachment>;
}

export async function uploadSessionAttachments(
  sessionId: string,
  files: File[],
): Promise<UserAttachment[]> {
  const uploads = files.map((file) => uploadSessionAttachment(sessionId, file));
  return Promise.all(uploads);
}

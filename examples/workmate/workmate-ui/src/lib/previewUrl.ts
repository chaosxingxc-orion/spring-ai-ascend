const API_BASE = import.meta.env.VITE_API_BASE ?? '';

/** Path-based preview URL so iframe-relative assets resolve under the same session. */
export function buildArtifactPreviewUrl(sessionId: string, path: string): string {
  const segments = path.split('/').map((segment) => encodeURIComponent(segment));
  return `${API_BASE}/api/v1/sessions/${sessionId}/preview/${segments.join('/')}`;
}

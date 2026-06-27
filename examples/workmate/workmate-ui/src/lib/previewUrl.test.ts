import { describe, expect, it } from 'vitest';
import { buildArtifactPreviewUrl } from './previewUrl';

describe('buildArtifactPreviewUrl', () => {
  it('encodes path segments for preview endpoint', () => {
    expect(buildArtifactPreviewUrl('abc-123', 'site/index.html')).toBe(
      '/api/v1/sessions/abc-123/preview/site/index.html',
    );
  });

  it('encodes special characters in segments', () => {
    expect(buildArtifactPreviewUrl('s1', 'a b/file.html')).toBe(
      '/api/v1/sessions/s1/preview/a%20b/file.html',
    );
  });
});

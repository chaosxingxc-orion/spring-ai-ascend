import { describe, expect, it } from 'vitest';
import {
  isBinaryPreviewPath,
  isImagePath,
  isOfficePath,
  isPdfPath,
  isVideoPath,
  previewKind,
} from './fileLanguage';

describe('fileLanguage media helpers', () => {
  it('detects image paths', () => {
    expect(isImagePath('out/chart.png')).toBe(true);
    expect(isImagePath('photo.jpg', 'image/jpeg')).toBe(true);
    expect(isImagePath('readme.md')).toBe(false);
  });

  it('detects pdf and video paths', () => {
    expect(isPdfPath('report.pdf')).toBe(true);
    expect(isVideoPath('clip.mp4')).toBe(true);
    expect(isVideoPath('clip.webm', 'video/webm')).toBe(true);
  });

  it('detects office documents', () => {
    expect(isOfficePath('deck.pptx')).toBe(true);
    expect(
      isOfficePath(
        'sheet.xlsx',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      ),
    ).toBe(true);
  });

  it('classifies preview kind', () => {
    expect(previewKind('a.png')).toBe('image');
    expect(previewKind('b.pdf')).toBe('pdf');
    expect(previewKind('c.mp4')).toBe('video');
    expect(previewKind('d.docx')).toBe('office');
    expect(previewKind('index.html')).toBe(null);
  });

  it('marks binary preview paths', () => {
    expect(isBinaryPreviewPath('a.png')).toBe(true);
    expect(isBinaryPreviewPath('b.docx')).toBe(true);
    expect(isBinaryPreviewPath('index.html')).toBe(false);
  });
});

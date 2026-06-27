import { describe, expect, it } from 'vitest';
import { imageAttachments, parseUserAttachments } from './userAttachments';

describe('userAttachments', () => {
  it('parses workspace image attachments', () => {
    const rows = parseUserAttachments([
      { path: 'notes/diagram.png', name: 'diagram.png', mime: 'image/png' },
      { path: 'readme.md' },
    ]);
    expect(rows).toHaveLength(2);
    expect(imageAttachments(rows)).toHaveLength(1);
    expect(imageAttachments(rows)[0]?.path).toBe('notes/diagram.png');
  });
});

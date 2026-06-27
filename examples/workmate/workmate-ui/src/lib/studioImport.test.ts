import { describe, expect, it } from 'vitest';
import { expertImportPayloadToStudioBody } from './studioImport';

describe('studioImport', () => {
  it('maps import payload to studio write body', () => {
    const body = expertImportPayloadToStudioBody({
      id: 'my-agent',
      name: 'My Agent',
      description: 'Desc',
      promptContent: 'Prompt body',
      category: 'custom',
    });
    expect(body.expertType).toBe('agent');
    expect(body.tags).toEqual(['imported', 'draft']);
  });
});

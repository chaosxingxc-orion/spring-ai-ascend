import type { ExpertImportPayload } from '../api/import';
import type { StudioExpertWriteBody } from '../types/studio';

export function expertImportPayloadToStudioBody(payload: ExpertImportPayload): StudioExpertWriteBody {
  return {
    id: payload.id,
    name: payload.name,
    description: payload.description,
    expertType: 'agent',
    promptContent: payload.promptContent,
    defaultInitPrompt: payload.defaultInitPrompt,
    category: payload.category ?? 'custom',
    tags: payload.tags?.length ? payload.tags : ['imported', 'draft'],
    skillCompatibility: [],
    preloadSkills: [],
    quickPrompts: [],
  };
}

export interface StudioImportResult {
  id: string;
  expertType?: string;
}

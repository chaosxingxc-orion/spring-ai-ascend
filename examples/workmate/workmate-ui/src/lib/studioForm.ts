import type { OfficeAssetSource, StudioExpertSource, StudioExpertWriteBody } from '../types/studio';

export function parseCommaList(value: string): string[] {
  return value
    .split(/[,，\n]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export function joinCommaList(items: string[] | undefined): string {
  return (items ?? []).join(', ');
}

export function sourceLabel(source: OfficeAssetSource): string {
  switch (source) {
    case 'BUILTIN':
      return '内置';
    case 'MARKET':
      return '市场';
    case 'IMPORT':
      return '导入';
    case 'DRAFT':
      return '草稿';
  }
}

export function expertSourceToForm(source: StudioExpertSource): StudioExpertWriteBody {
  const expert = source.summary;
  return {
    id: expert.id,
    name: expert.name,
    description: expert.description,
    expertType: expert.expertType || 'agent',
    promptContent: source.promptContent.replace(/\n$/, ''),
    promptFile: source.promptFile,
    defaultInitPrompt: expert.defaultInitPrompt ?? '',
    category: expert.category ?? 'custom',
    tags: expert.tags ?? [],
    skillCompatibility: expert.skillCompatibility ?? [],
    preloadSkills: expert.preloadSkills ?? [],
    quickPrompts: expert.quickPrompts ?? [],
    maxTurns: expert.maxTurns ?? null,
    displayName: expert.displayName,
    profession: expert.profession,
  };
}

export function isNewStudioId(id: string): boolean {
  return id === 'new';
}

export function normalizeExpertWriteBody(form: StudioExpertWriteBody, expertId: string): StudioExpertWriteBody {
  return {
    ...form,
    id: isNewStudioId(expertId) ? form.id : expertId,
    expertType: form.expertType || 'agent',
    category: form.category || 'custom',
    tags: form.tags?.length ? form.tags : ['draft'],
    skillCompatibility: form.skillCompatibility ?? [],
    preloadSkills: form.preloadSkills ?? [],
    quickPrompts: form.quickPrompts ?? [],
  };
}

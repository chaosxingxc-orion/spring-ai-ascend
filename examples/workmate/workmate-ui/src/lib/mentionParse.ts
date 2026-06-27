import type { MentionApiPayload, MentionRef, MentionType } from '../types/mention';

const VALID_TYPES: MentionType[] = ['file', 'skill', 'member', 'connector'];

export function detectMentionTrigger(
  value: string,
  cursor: number,
): { trigger: '@' | '/' | null; query: string; start: number } {
  const before = value.slice(0, cursor);
  const atMatch = before.match(/(?:^|\s)@([^\s@]*)$/);
  if (atMatch) {
    return { trigger: '@', query: atMatch[1] ?? '', start: before.length - (atMatch[1]?.length ?? 0) - 1 };
  }
  const slashMatch = before.match(/(?:^|\s)\/([^\s/]*)$/);
  if (slashMatch) {
    return { trigger: '/', query: slashMatch[1] ?? '', start: before.length - (slashMatch[1]?.length ?? 0) - 1 };
  }
  return { trigger: null, query: '', start: -1 };
}

export function mentionToApiPayload(mentions: MentionRef[]): MentionApiPayload[] {
  return mentions.map((mention) => ({
    type: mention.type,
    id: mention.id,
    path: mention.path,
    label: mention.label,
  }));
}

export function parseMentionsFromServer(raw: unknown): MentionRef[] | undefined {
  if (!Array.isArray(raw)) {
    return undefined;
  }
  const mentions: MentionRef[] = [];
  for (const item of raw) {
    if (!item || typeof item !== 'object') {
      continue;
    }
    const record = item as Record<string, unknown>;
    const type = record.type;
    const id = record.id;
    const label = record.label;
    if (typeof type !== 'string' || typeof id !== 'string') {
      continue;
    }
    if (!VALID_TYPES.includes(type as MentionType)) {
      continue;
    }
    mentions.push({
      type: type as MentionType,
      id,
      path: typeof record.path === 'string' ? record.path : undefined,
      label: typeof label === 'string' && label.trim() ? label : id,
    });
  }
  return mentions.length > 0 ? mentions : undefined;
}

export function mentionIcon(type: MentionType): string {
  switch (type) {
    case 'file':
      return '📄';
    case 'skill':
      return '⚒';
    case 'member':
      return '👤';
    case 'connector':
      return '🔌';
    default:
      return '@';
  }
}

export function mentionGroupLabel(type: MentionType): string {
  switch (type) {
    case 'file':
      return '工作区文件';
    case 'skill':
      return '技能';
    case 'member':
      return '团队成员';
    case 'connector':
      return '连接器';
    default:
      return '提及';
  }
}

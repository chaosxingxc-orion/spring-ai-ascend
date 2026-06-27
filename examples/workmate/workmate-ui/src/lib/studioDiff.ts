import { sourceLabel } from '../lib/studioForm';
import type { OfficeAssetSource } from '../types/studio';

const FIELD_LABELS: Record<string, string> = {
  name: '名称',
  description: '描述',
  category: '分类',
  promptContent: 'Prompt',
  skillContent: '技能正文',
  welcomeYaml: 'welcome.yaml',
  title: '标题',
  initPrompt: 'initPrompt',
  placements: 'placements',
  accent: 'accent',
  expertId: 'expertId',
  defaultInitPrompt: '默认首条消息',
  maxTurns: 'maxTurns',
  tags: '标签',
  skillCompatibility: '推荐技能',
  new: '新建草稿',
};

export function studioDiffFieldLabel(field: string): string {
  return FIELD_LABELS[field] ?? field;
}

export function studioDiffBaselineLabel(source: OfficeAssetSource | null): string {
  if (!source) {
    return '无原始版本';
  }
  return sourceLabel(source);
}

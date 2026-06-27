import type { Expert } from '../types/api';
import { isTeamExpertType } from './terminology';

export const EXPERT_MARKET_CATEGORIES = [
  { id: '全部', label: '全部' },
  { id: 'opc', label: 'OPC 专区' },
  { id: 'finance', label: '金融投资' },
  { id: 'product', label: '产品设计' },
  { id: 'engineering', label: '技术工程' },
] as const;

export type ExpertMarketCategoryId = (typeof EXPERT_MARKET_CATEGORIES)[number]['id'];

/** 专家市场类型筛选：全部 / 单专家 / 专家团 */
export type ExpertMarketKind = 'all' | 'agent' | 'team';

export function parseExpertMarketKind(value: string | null | undefined): ExpertMarketKind {
  if (value === 'agent' || value === 'team') {
    return value;
  }
  return 'all';
}

export function expertMarketKindLabel(kind: ExpertMarketKind): string {
  switch (kind) {
    case 'agent':
      return '专家';
    case 'team':
      return '专家团';
    default:
      return '全部';
  }
}

export function expertMatchesKind(expert: Expert, kind: ExpertMarketKind): boolean {
  if (kind === 'all') {
    return true;
  }
  if (kind === 'team') {
    return isTeamExpertType(expert.expertType);
  }
  return !isTeamExpertType(expert.expertType);
}

export function expertMarketCategoryLabel(categoryId: string): string {
  return EXPERT_MARKET_CATEGORIES.find((item) => item.id === categoryId)?.label ?? categoryId;
}

/** Zone pills (e.g. opc) match tag or category; others match category field. */
export function expertMatchesCategory(expert: Expert, categoryId: string): boolean {
  if (categoryId === '全部') {
    return true;
  }
  if (categoryId === 'opc') {
    return expert.category === categoryId || expert.tags.includes(categoryId);
  }
  return expert.category === categoryId;
}

export function expertIsBeta(expert: Expert): boolean {
  return Boolean(expert.beta) || expert.tags.includes('beta');
}

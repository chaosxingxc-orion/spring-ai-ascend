/**
 * 产品术语 ↔ 运行时概念映射（用于副文案、类型标识，不替换主界面名称）
 * 专家 → agent · 专家团 → multi-agent · 技能 → skills · 连接器 → MCP
 */
export const TERM = {
  expert: '专家',
  expertTeam: '专家团',
  skill: '技能',
  connector: '连接器',
  /** Dock 工具条「连应用」→ 运行时 MCP */
  connectApps: '连应用',
  runtimeAgent: 'agent',
  runtimeMultiAgent: 'multi-agent',
  runtimeSkills: 'skills',
  runtimeMcp: 'MCP',
} as const;

export function isTeamExpertType(expertType: string | undefined | null): boolean {
  return expertType === 'team';
}

/** 卡片/列表主标签：专家 | 专家团 */
export function expertKindLabel(expertType: string | undefined | null): string {
  return isTeamExpertType(expertType) ? TERM.expertTeam : TERM.expert;
}

/** 副文案运行时类型：agent | multi-agent */
export function expertRuntimeType(expertType: string | undefined | null): string {
  return isTeamExpertType(expertType) ? TERM.runtimeMultiAgent : TERM.runtimeAgent;
}

export function summonActionLabel(expertType: string | undefined | null): string {
  return isTeamExpertType(expertType) ? '召唤专家团' : '召唤专家';
}

/** skillCompatibility 在 UI 上表示推荐连接器（运行时 MCP），不是已安装技能 */
export function formatConnectorLabel(connectorId: string): string {
  return connectorId.endsWith('-mcp') ? connectorId.replace(/-mcp$/, '') : connectorId;
}

/** @deprecated 使用 formatConnectorLabel */
export const formatMcpCompatibility = formatConnectorLabel;

export type MarketCapabilityKind = 'skill' | 'plugin' | 'connector';

/** 市场卡片元信息前缀：skills · id / 插件 · id / 连接器 · id */
export function marketCapabilityPillLabel(kind: MarketCapabilityKind, id: string): string {
  switch (kind) {
    case 'skill':
      return `${TERM.runtimeSkills} · ${id}`;
    case 'plugin':
      return `插件 · ${id}`;
    case 'connector':
      return `${TERM.connector} · ${formatConnectorLabel(id)}`;
  }
}

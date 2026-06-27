import type { MentionMenuItem } from '../components/MentionMenu';
import type { ConnectorInfo, SkillInfo } from '../types/market';

export function skillsToMentionItems(skills: SkillInfo[]): MentionMenuItem[] {
  return skills.map((skill) => ({
    type: 'skill',
    id: skill.id,
    label: skill.name,
    hint: skill.category ?? skill.id,
  }));
}

export function connectorsToMentionItems(connectors: ConnectorInfo[]): MentionMenuItem[] {
  return connectors.map((connector) => ({
    type: 'connector',
    id: connector.id,
    label: connector.name,
    hint: connector.status,
  }));
}

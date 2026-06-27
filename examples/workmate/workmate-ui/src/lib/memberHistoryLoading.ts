import type { ChatItem } from '../types/events';
import type { TeamState } from './teamStatus';
import { filterMemberChatItems } from './memberChatProjection';

export interface MemberHistoryLoadingInput {
  memberId?: string;
  memberItems: ChatItem[];
  team?: TeamState | null;
  teamHistoryLoading?: boolean;
}

/** True when a member was started but its sub-run timeline is not hydrated yet. */
export function shouldShowMemberHistoryLoading({
  memberId,
  memberItems,
  team,
  teamHistoryLoading = false,
}: MemberHistoryLoadingInput): boolean {
  if (!memberId) {
    return false;
  }
  if (memberItems.length > 0) {
    return false;
  }
  if (teamHistoryLoading) {
    return true;
  }
  if (!team) {
    return false;
  }
  const member = team.members.find((entry) => entry.id === memberId);
  if (!member) {
    return false;
  }
  const active = team.activeMemberIds?.includes(memberId) ?? false;
  return Boolean(
    active
    || member.status === 'running'
    || member.hasStarted
    || team.recentlyCompletedMemberIds?.includes(memberId),
  );
}

export function memberHistoryLoadingForMember(
  memberId: string,
  chatItems: ChatItem[],
  team: TeamState | null | undefined,
  teamHistoryLoading: boolean,
): boolean {
  return shouldShowMemberHistoryLoading({
    memberId,
    memberItems: filterMemberChatItems(chatItems, { id: memberId, name: memberId }),
    team,
    teamHistoryLoading,
  });
}

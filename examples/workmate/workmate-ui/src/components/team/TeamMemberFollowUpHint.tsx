import type { TeamState } from '../../lib/teamStatus';
import { isTeamMemberFollowUpAvailable } from '../../lib/teamStatus';

interface TeamMemberFollowUpHintProps {
  team: TeamState;
}

/** Shown when the team run is done — directs users to main input @mention instead of mailbox bypass. */
export function TeamMemberFollowUpHint({ team }: TeamMemberFollowUpHintProps) {
  if (!isTeamMemberFollowUpAvailable(team)) {
    return null;
  }
  return (
    <p className="team-member-followup-hint">
      团队已完成。在下方输入框 <strong>@成员</strong> 续聊，由主理人重新派活；运行中的旁路 @成员 已关闭。
    </p>
  );
}

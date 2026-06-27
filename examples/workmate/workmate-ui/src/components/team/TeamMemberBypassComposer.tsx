import { useState } from 'react';
import { sendTeamMemberMessage } from '../../api/client';
import type { TeamState } from '../../lib/teamStatus';
import { delegationBarMembers, isTeamBypassMessagingAvailable } from '../../lib/teamStatus';

interface TeamMemberBypassComposerProps {
  sessionId?: string;
  team: TeamState;
}

/**
 * {@code @member} bypass composer: send a directed message straight to a team member (or the whole
 * team / the lead) of an in-flight team run, without waiting for the lead to delegate. Collapsed by
 * default; expands inline on the delegation bar meta row.
 */
export function TeamMemberBypassComposer({ sessionId, team }: TeamMemberBypassComposerProps) {
  const members = delegationBarMembers(team);
  const [open, setOpen] = useState(false);
  const [target, setTarget] = useState<string>('@all');
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const available = Boolean(sessionId) && isTeamBypassMessagingAvailable(team);

  if (!available) {
    return null;
  }

  const hint =
    target === '@main'
      ? '发给主理人：作为新消息进入其收件箱，会重新唤醒主理人继续协调。'
      : target === '@all'
        ? '广播给全体成员（不含发送者）。'
        : '直达该成员：其产出会回流给主理人，主理人据此继续协调。';
  const placeholder =
    target === '@main'
      ? '给主理人发一条消息…'
      : target === '@all'
        ? '向全体成员广播…'
        : '给成员发一条旁路消息…';

  const onSend = async () => {
    const message = text.trim();
    if (!message || busy || !sessionId) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await sendTeamMemberMessage(sessionId, target, message);
      setText('');
    } catch (err) {
      setError(err instanceof Error ? err.message : '发送失败');
    } finally {
      setBusy(false);
    }
  };

  if (!open) {
    return (
      <button
        type="button"
        className="team-member-bypass-toggle"
        title="旁路 @成员：直达成员或全体广播，不经过主理人派活"
        aria-label="旁路 @成员"
        onClick={() => setOpen(true)}
      >
        @成员
      </button>
    );
  }

  return (
    <div className="team-member-bypass team-member-bypass-open">
      <select
        className="team-member-bypass-target"
        value={target}
        title={hint}
        onChange={(event) => setTarget(event.target.value)}
        aria-label="发送目标"
      >
        <option value="@all">@全体</option>
        <option value="@main">@主理人</option>
        {members.map((member) => (
          <option key={member.id} value={`@${member.id}`}>
            @{member.name}
          </option>
        ))}
      </select>
      <input
        className="team-member-bypass-input"
        type="text"
        value={text}
        placeholder={placeholder}
        title={hint}
        onChange={(event) => setText(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && !event.nativeEvent.isComposing) {
            void onSend();
          }
          if (event.key === 'Escape') {
            setOpen(false);
          }
        }}
        disabled={busy}
      />
      <button
        type="button"
        className="team-member-bypass-send"
        onClick={() => void onSend()}
        disabled={busy || !text.trim()}
      >
        发送
      </button>
      <button
        type="button"
        className="team-member-bypass-close btn ghost sm"
        aria-label="收起旁路输入"
        onClick={() => {
          setOpen(false);
          setError(null);
        }}
      >
        ✕
      </button>
      {error && <span className="team-member-bypass-error">{error}</span>}
    </div>
  );
}

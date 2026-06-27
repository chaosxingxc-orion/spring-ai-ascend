import { useEffect, useMemo, useState } from 'react';
import { ToolCard } from '../ToolCard';
import type { MemberTraceItem } from '../../lib/memberEventProjection';
import {
  chatItemsToMemberTrace,
  isMemberLifecycleStatusText,
  summarizeMemberChatItems,
} from '../../lib/memberChatProjection';
import {
  delegationInputFromCard,
  loadDelegationCardsForMember,
  sameDelegationCards,
  type MemberDelegationChatItem,
} from '../../lib/delegationInput';
import { MemberHistoryLoadingBanner } from '../team/MemberHistoryLoadingBanner';
import { MemberTaskMessage } from '../MemberTaskMessage';
import { PlainTextContent } from '../PlainTextContent';
import type { ChatItem } from '../../types/events';

interface AgentMemberViewerProps {
  sessionId: string;
  memberId: string;
  memberName: string;
  /** Main chat timeline — same source as center member focus view (no separate run_events poll). */
  items: ChatItem[];
  streaming?: boolean;
  historyLoading?: boolean;
  onClose?: () => void;
}

type MemberTimelineRow =
  | { kind: 'delegation'; seq: number; card: MemberDelegationChatItem }
  | { kind: 'trace'; seq: number; item: MemberTraceItem };

/** W39-B3 — member trace projected from the main chat timeline. */
export function AgentMemberViewer({
  sessionId,
  memberId,
  memberName,
  items,
  streaming = false,
  historyLoading = false,
  onClose,
}: AgentMemberViewerProps) {
  const [delegationCards, setDelegationCards] = useState<MemberDelegationChatItem[]>([]);
  const [collapsedHistory, setCollapsedHistory] = useState(true);

  useEffect(() => {
    setCollapsedHistory(true);
  }, [sessionId, memberId]);

  const trace = useMemo(() => chatItemsToMemberTrace(items, memberId), [items, memberId]);
  const summary = useMemo(() => summarizeMemberChatItems(items, memberId), [items, memberId]);

  useEffect(() => {
    let cancelled = false;
    void loadDelegationCardsForMember(sessionId, memberId, items, memberName).then((next) => {
      if (!cancelled) {
        setDelegationCards((prev) => (sameDelegationCards(prev, next) ? prev : next));
      }
    });
    return () => {
      cancelled = true;
    };
  }, [sessionId, memberId, memberName, items]);

  const timeline = useMemo((): MemberTimelineRow[] => {
    const rows: MemberTimelineRow[] = [
      ...delegationCards.map((card) => ({
        kind: 'delegation' as const,
        seq: card.seq ?? 0,
        card,
      })),
      ...trace.map((item) => ({
        kind: 'trace' as const,
        seq: item.seq,
        item,
      })),
    ];
    return rows.sort((a, b) => a.seq - b.seq);
  }, [delegationCards, trace]);

  const title = useMemo(() => `${memberName} · 成员轨迹`, [memberName]);
  const visibleTimeline = useMemo(() => {
    if (!collapsedHistory || timeline.length <= 18) {
      return timeline;
    }
    const recentToolIds = new Set(
      timeline
        .filter((row): row is Extract<MemberTimelineRow, { kind: 'trace' }> =>
          row.kind === 'trace' && row.item.kind === 'tool')
        .slice(-12)
        .map((row) => row.item.id),
    );
    return timeline.filter((row) => {
      if (row.kind === 'delegation') {
        return true;
      }
      return row.item.kind !== 'tool' || recentToolIds.has(row.item.id);
    });
  }, [collapsedHistory, timeline]);
  const hiddenCount = Math.max(timeline.length - visibleTimeline.length, 0);
  const hasContent = visibleTimeline.length > 0;

  return (
    <section className="agent-member-viewer" aria-label={title}>
      <header className="agent-member-viewer-header">
        <h3>{title}</h3>
        {streaming && <span className="stream-stage-chip stage-tool">同步中</span>}
        {onClose && (
          <button type="button" className="btn ghost sm" onClick={onClose}>
            返回预览
          </button>
        )}
      </header>
      {historyLoading && (
        <MemberHistoryLoadingBanner memberName={memberName} />
      )}
      {!hasContent && !streaming && !historyLoading && (
        <p className="detail-hint muted">暂无成员轨迹事件</p>
      )}
      {!hasContent && streaming && !historyLoading && (
        <p className="detail-hint muted">成员工作中…</p>
      )}
      {summary && (
        <div className="agent-member-summary-row">
          <span>首启 #{summary.firstStartSeq ?? '—'}</span>
          <span>末完 #{summary.lastCompletedSeq ?? '—'}</span>
          <span>工具 {summary.toolCalls}</span>
          <span>错误 {summary.errorCount}</span>
          {summary.unscheduled && <span className="unscheduled">未调度</span>}
          {hiddenCount > 0 && (
            <button
              type="button"
              className="agent-member-history-toggle"
              onClick={() => setCollapsedHistory((prev) => !prev)}
            >
              {collapsedHistory ? `展开旧记录 ${hiddenCount} 条` : '收起旧记录'}
            </button>
          )}
        </div>
      )}
      {hasContent && (
        <ol className="agent-member-trace-list">
          {visibleTimeline.map((row) => (
            <li
              key={row.kind === 'delegation' ? row.card.id : `${row.item.seq}-${row.item.id}`}
              className="agent-member-trace-item"
            >
              {row.kind === 'delegation' ? (
                <MemberTaskMessage
                  delegation={delegationInputFromCard(row.card)}
                  memberName={memberName}
                  round={row.card.round}
                />
              ) : (
                <MemberTraceRow item={row.item} />
              )}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

function MemberTraceRow({ item }: { item: MemberTraceItem }) {
  if (item.kind === 'tool' && item.toolName && item.status) {
    return (
      <div className="agent-member-trace-row">
        <span className="agent-member-trace-seq">#{item.seq}</span>
        <ToolCard
          toolName={item.toolName}
          status={item.status}
          args={item.args}
          result={item.result}
        />
      </div>
    );
  }

  if (item.kind === 'reasoning' && isMemberLifecycleStatusText(item.text)) {
    return (
      <div className="agent-member-trace-row">
        <span className="agent-member-trace-seq">#{item.seq}</span>
        <div className="agent-member-trace-text agent-member-trace-status">
          <span className="agent-member-trace-label">状态</span>
          <PlainTextContent source={item.text ?? ''} className="agent-member-trace-plain" />
        </div>
      </div>
    );
  }

  if (item.kind === 'reasoning') {
    return (
      <div className="agent-member-trace-row">
        <span className="agent-member-trace-seq">#{item.seq}</span>
        <div className="agent-member-trace-text agent-member-trace-reasoning">
          <span className="agent-member-trace-label">思考</span>
          <PlainTextContent source={item.text ?? ''} className="agent-member-trace-plain" />
        </div>
      </div>
    );
  }

  return (
    <div className="agent-member-trace-row">
      <span className="agent-member-trace-seq">#{item.seq}</span>
      <div className="agent-member-trace-text agent-member-trace-assistant">
        <PlainTextContent source={item.text ?? ''} className="agent-member-trace-plain" />
      </div>
    </div>
  );
}

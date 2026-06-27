interface MemberHistoryLoadingBannerProps {
  memberName?: string;
}

/** reference-style indicator while sub-agent run_events / JSONL history hydrates. */
export function MemberHistoryLoadingBanner({ memberName }: MemberHistoryLoadingBannerProps) {
  const label = memberName ? `正在加载 ${memberName} 的子 Agent 历史…` : '正在加载子 Agent 历史…';
  return (
    <div className="member-history-loading" role="status" aria-live="polite">
      <span className="member-history-loading-spinner" aria-hidden />
      <span>{label}</span>
    </div>
  );
}

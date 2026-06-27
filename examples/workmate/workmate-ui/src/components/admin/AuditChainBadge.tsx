import type { AuditVerifyResult } from '../../types/api';

interface AuditChainBadgeProps {
  result: AuditVerifyResult | null;
  loading?: boolean;
}

export function AuditChainBadge({ result, loading }: AuditChainBadgeProps) {
  if (loading) {
    return <span className="audit-chain-badge loading">校验中…</span>;
  }
  if (!result) {
    return <span className="audit-chain-badge unknown">未校验</span>;
  }
  if (result.ok) {
    return (
      <span className="audit-chain-badge ok" title={`已验证至 #${result.verifiedThroughSeq}`}>
        链完整 ✓
      </span>
    );
  }
  return (
    <span
      className="audit-chain-badge broken"
      title={`断链 #${result.brokenSeqGlobal} · ${result.field}`}
    >
      链异常 ✕ #{result.brokenSeqGlobal}
    </span>
  );
}

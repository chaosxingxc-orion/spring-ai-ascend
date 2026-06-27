import type { AuditVerifyResult } from '../../types/api';
import { AuditChainBadge } from './AuditChainBadge';

interface AuditVerifyDetailProps {
  result: AuditVerifyResult | null;
  loading?: boolean;
  onRefresh?: () => void;
  refreshBusy?: boolean;
}

/** W46-B5 — expanded verify outcome below the audit header badge. */
export function AuditVerifyDetail({
  result,
  loading = false,
  onRefresh,
  refreshBusy = false,
}: AuditVerifyDetailProps) {
  return (
    <section className="audit-verify-detail" aria-label="哈希链校验结果">
      <div className="audit-verify-detail-head">
        <AuditChainBadge result={result} loading={loading} />
        {onRefresh && (
          <button
            type="button"
            className="btn ghost sm"
            disabled={refreshBusy || loading}
            onClick={onRefresh}
          >
            {refreshBusy || loading ? '校验中…' : '重新校验'}
          </button>
        )}
      </div>
      {result && !loading && (
        <div className={`audit-verify-detail-body${result.ok ? ' ok' : ' broken'}`}>
          {result.ok ? (
            <p>
              链完整，已验证至全局序号 <strong>#{result.verifiedThroughSeq}</strong>。
              导出 NDJSON 段文件可用于离线复核。
            </p>
          ) : (
            <>
              <p>
                链在序号 <strong>#{result.brokenSeqGlobal ?? '?'}</strong> 处不一致
                {result.field ? `（字段 ${result.field}）` : ''}。
              </p>
              {(result.expected || result.actual) && (
                <dl className="audit-verify-diff">
                  {result.expected != null && (
                    <div>
                      <dt>期望值</dt>
                      <dd className="mono">{result.expected}</dd>
                    </div>
                  )}
                  {result.actual != null && (
                    <div>
                      <dt>实际值</dt>
                      <dd className="mono">{result.actual}</dd>
                    </div>
                  )}
                </dl>
              )}
            </>
          )}
        </div>
      )}
    </section>
  );
}

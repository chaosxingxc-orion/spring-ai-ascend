import { useCallback, useEffect, useMemo, useState } from 'react';
import { exportAuditSegment, listAuditEntries, verifyAuditChain } from '../../api/client';
import type { AuditEntry, AuditVerifyResult } from '../../types/api';
import { AuditVerifyDetail } from '../../components/admin/AuditVerifyDetail';
import { formatDateTime, useLocaleKey } from '../../lib/formatLocale';

const CATEGORY_OPTIONS = [
  { value: '', label: '全部分类' },
  { value: 'approval', label: '审批' },
  { value: 'commandSafety', label: '命令安全' },
  { value: 'fileSafety', label: '文件安全' },
  { value: 'mcp', label: 'MCP' },
  { value: 'network', label: '网络' },
  { value: 'sandbox', label: '沙箱' },
  { value: 'security', label: '安全' },
  { value: 'system', label: '系统' },
];

const DECISION_OPTIONS = [
  { value: '', label: '全部决策' },
  { value: 'approved', label: '已批准' },
  { value: 'rejected', label: '已拒绝' },
  { value: 'blocked', label: '已拦截' },
  { value: 'failed', label: '失败' },
  { value: 'info', label: '信息' },
];

const TIME_OPTIONS = [
  { value: 'all', label: '全部时间' },
  { value: '24h', label: '近 24 小时' },
  { value: '7d', label: '近 7 天' },
];

function timeRange(value: string): { from?: string; to?: string } {
  if (value === 'all') {
    return {};
  }
  const to = new Date();
  const from = new Date(to);
  if (value === '24h') {
    from.setHours(from.getHours() - 24);
  } else {
    from.setDate(from.getDate() - 7);
  }
  return { from: from.toISOString(), to: to.toISOString() };
}

function formatWhen(iso: string): string {
  try {
    return formatDateTime(iso);
  } catch {
    return iso;
  }
}

export function AuditLogView() {
  useLocaleKey();
  const [category, setCategory] = useState('');
  const [decision, setDecision] = useState('');
  const [timeFilter, setTimeFilter] = useState('7d');
  const [query, setQuery] = useState('');
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [verify, setVerify] = useState<AuditVerifyResult | null>(null);
  const [verifyLoading, setVerifyLoading] = useState(false);
  const [exportBusy, setExportBusy] = useState(false);
  const [exportSegment, setExportSegment] = useState(() => new Date().toISOString().slice(0, 10));

  const range = useMemo(() => timeRange(timeFilter), [timeFilter]);

  const loadPage = useCallback(
    async (pageCursor?: number) => {
      setLoading(true);
      setError(null);
      try {
        const page = await listAuditEntries({
          category: category || undefined,
          decision: decision || undefined,
          from: range.from,
          to: range.to,
          q: query.trim() || undefined,
          cursor: pageCursor,
          limit: 50,
        });
        setEntries((prev) => (pageCursor == null ? page.entries : [...prev, ...page.entries]));
        setNextCursor(page.nextCursor);
      } catch (err) {
        setError((err as Error).message);
      } finally {
        setLoading(false);
      }
    },
    [category, decision, query, range.from, range.to],
  );

  const refreshVerify = useCallback(async () => {
    setVerifyLoading(true);
    try {
      setVerify(await verifyAuditChain());
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setVerifyLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadPage();
    void refreshVerify();
  }, [loadPage, refreshVerify]);

  const handleExport = async () => {
    const segment = exportSegment.trim();
    if (!segment) {
      return;
    }
    setExportBusy(true);
    try {
      const body = await exportAuditSegment(segment);
      const blob = new Blob([body], { type: 'application/x-ndjson' });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `audit-${segment}.jsonl`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setExportBusy(false);
    }
  };

  return (
    <main className="audit-log-page">
      <header className="audit-log-header">
        <div>
          <h1>安全中心 · 审计日志</h1>
          <p className="muted">只读哈希链视图 — 不可清空、不可篡改（WORM）</p>
        </div>
      </header>

      <AuditVerifyDetail
        result={verify}
        loading={verifyLoading}
        onRefresh={() => void refreshVerify()}
        refreshBusy={verifyLoading}
      />

      <div className="audit-log-toolbar">
        <select value={category} onChange={(event) => setCategory(event.target.value)} aria-label="分类">
          {CATEGORY_OPTIONS.map((option) => (
            <option key={option.value || 'all'} value={option.value}>{option.label}</option>
          ))}
        </select>
        <select value={decision} onChange={(event) => setDecision(event.target.value)} aria-label="决策">
          {DECISION_OPTIONS.map((option) => (
            <option key={option.value || 'all'} value={option.value}>{option.label}</option>
          ))}
        </select>
        <select value={timeFilter} onChange={(event) => setTimeFilter(event.target.value)} aria-label="时间">
          {TIME_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
        <input
          type="search"
          className="audit-log-search"
          placeholder="搜索事件名 / runId"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        <button type="button" className="btn ghost sm" onClick={() => void loadPage()}>
          应用筛选
        </button>
        <label className="audit-export-date">
          <input
            type="date"
            value={exportSegment}
            onChange={(event) => setExportSegment(event.target.value)}
            aria-label="导出日期段"
          />
        </label>
        <button type="button" className="btn ghost sm" disabled={exportBusy} onClick={() => void handleExport()}>
          {exportBusy ? '导出中…' : '导出 NDJSON 段'}
        </button>
      </div>

      {error && <div className="audit-log-error">{error}</div>}

      <div className="audit-log-table-wrap">
        <table className="audit-log-table">
          <thead>
            <tr>
              <th>#</th>
              <th>时间</th>
              <th>分类</th>
              <th>决策</th>
              <th>事件</th>
              <th>会话</th>
              <th>entry hash</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr key={entry.seqGlobal}>
                <td>{entry.seqGlobal}</td>
                <td>{formatWhen(entry.createdAt)}</td>
                <td><span className="audit-pill">{entry.category}</span></td>
                <td><span className={`audit-pill decision-${entry.decision}`}>{entry.decision}</span></td>
                <td>{entry.eventName}</td>
                <td className="mono">{entry.sessionId.slice(0, 8)}…</td>
                <td className="mono" title={entry.entryHash}>{entry.entryHash.slice(0, 12)}…</td>
              </tr>
            ))}
          </tbody>
        </table>
        {entries.length === 0 && !loading && (
          <p className="audit-log-empty muted">暂无审计条目（运行任务后将自动投影到哈希链）</p>
        )}
      </div>

      <footer className="audit-log-footer">
        {nextCursor != null && (
          <button
            type="button"
            className="btn primary sm"
            disabled={loading}
            onClick={() => void loadPage(nextCursor)}
          >
            加载更多
          </button>
        )}
        {loading && <span className="muted">加载中…</span>}
      </footer>
    </main>
  );
}

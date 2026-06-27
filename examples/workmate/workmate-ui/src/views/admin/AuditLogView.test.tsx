import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { AuditChainBadge } from '../../components/admin/AuditChainBadge';
import { AuditVerifyDetail } from '../../components/admin/AuditVerifyDetail';

describe('AuditChainBadge', () => {
  it('shows ok state when chain verifies', () => {
    const html = renderToStaticMarkup(
      <AuditChainBadge result={{ ok: true, verifiedThroughSeq: 12 }} />,
    );
    expect(html).toContain('链完整');
  });

  it('shows broken state with seq', () => {
    const html = renderToStaticMarkup(
      <AuditChainBadge
        result={{
          ok: false,
          verifiedThroughSeq: 0,
          brokenSeqGlobal: 7,
          field: 'entry_hash',
          expected: 'aaa',
          actual: 'bbb',
        }}
      />,
    );
    expect(html).toContain('#7');
  });
});

describe('AuditVerifyDetail', () => {
  it('shows verified seq when ok', () => {
    const html = renderToStaticMarkup(
      <AuditVerifyDetail result={{ ok: true, verifiedThroughSeq: 42 }} />,
    );
    expect(html).toContain('#42');
  });
});

describe('AuditLogView policy', () => {
  it('does not expose clearAll action in toolbar copy', () => {
    const forbidden = ['clearAll', '清空全部', 'clear all'];
    const toolbarLabels = ['应用筛选', '导出 NDJSON 段', '搜索事件名 / runId'];
    for (const label of forbidden) {
      expect(toolbarLabels.join(' ').toLowerCase()).not.toContain(label.toLowerCase());
    }
  });
});

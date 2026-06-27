import { useCallback, useEffect, useState } from 'react';
import { DiffEditor } from '@monaco-editor/react';
import type { StudioAssetDiff } from '../../types/studio';
import { monacoEditorTheme } from '../../lib/monacoLanguage';
import { sourceLabel } from '../../lib/studioForm';
import { studioDiffBaselineLabel, studioDiffFieldLabel } from '../../lib/studioDiff';

interface StudioDiffPanelProps {
  title: string;
  loadDiff: () => Promise<StudioAssetDiff>;
  onRollback: () => Promise<void>;
  onExport?: () => Promise<void>;
  language?: 'markdown' | 'yaml';
  rollbackLabel?: string;
}

export function StudioDiffPanel({
  title,
  loadDiff,
  onRollback,
  onExport,
  language = 'markdown',
  rollbackLabel = '回退到原始',
}: StudioDiffPanelProps) {
  const [diff, setDiff] = useState<StudioAssetDiff | null>(null);
  const [expanded, setExpanded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [rollingBack, setRollingBack] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDiff(await loadDiff());
    } catch (err) {
      setError((err as Error).message);
      setDiff(null);
    } finally {
      setLoading(false);
    }
  }, [loadDiff]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  if (loading && !diff) {
    return <p className="muted dev-studio-hint">加载 diff…</p>;
  }

  if (!diff || !diff.hasDraft) {
    return null;
  }

  const showDiffEditor = diff.hasBaseline && diff.baselinePrompt !== diff.draftPrompt;

  const handleRollback = async () => {
    setRollingBack(true);
    setError(null);
    try {
      await onRollback();
      setExpanded(false);
      await refresh();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setRollingBack(false);
    }
  };

  return (
    <section className="dev-studio-diff-panel">
      <header className="dev-studio-diff-header">
        <div>
          <h2>{title}</h2>
          <p className="muted">
            当前：<span className={`dev-studio-badge dev-studio-badge-${diff.currentSource.toLowerCase()}`}>
              {sourceLabel(diff.currentSource)}
            </span>
            {diff.hasBaseline && (
              <>
                {' · 对比 '}
                {studioDiffBaselineLabel(diff.baselineSource)}
              </>
            )}
            {!diff.hasBaseline && ' · 新建草稿（无原始版本）'}
          </p>
        </div>
        <div className="dev-studio-diff-actions">
          {showDiffEditor && (
            <button type="button" className="btn ghost sm" onClick={() => setExpanded((value) => !value)}>
              {expanded ? '收起 diff' : '查看 diff'}
            </button>
          )}
          {diff.canRollback && (
            <button type="button" className="btn ghost sm" disabled={rollingBack} onClick={() => void handleRollback()}>
              {rollingBack ? '回退中…' : rollbackLabel}
            </button>
          )}
          {onExport && (
            <button
              type="button"
              className="btn ghost sm"
              disabled={exporting}
              onClick={() => {
                setExporting(true);
                setError(null);
                void onExport()
                  .catch((err) => setError((err as Error).message))
                  .finally(() => setExporting(false));
              }}
            >
              {exporting ? '导出中…' : '导出 office'}
            </button>
          )}
        </div>
      </header>

      {diff.changedFields.length > 0 && (
        <p className="dev-studio-diff-fields muted">
          变更字段：
          {diff.changedFields.map((field) => studioDiffFieldLabel(field)).join('、')}
        </p>
      )}

      {error && <div className="dev-studio-error">{error}</div>}

      {expanded && showDiffEditor && diff.baselinePrompt != null && (
        <div className="dev-studio-diff-editor">
          <DiffEditor
            height="320px"
            language={language}
            original={diff.baselinePrompt}
            modified={diff.draftPrompt}
            theme={monacoEditorTheme()}
            options={{
              readOnly: true,
              renderSideBySide: true,
              minimap: { enabled: false },
              scrollBeyondLastLine: false,
              wordWrap: 'on',
              fontSize: 13,
            }}
          />
        </div>
      )}
    </section>
  );
}

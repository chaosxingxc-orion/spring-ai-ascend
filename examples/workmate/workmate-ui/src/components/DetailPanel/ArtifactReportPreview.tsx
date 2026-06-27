import { useMemo } from 'react';
import type { FileContent } from '../../types/api';
import { isMarkdownPath } from '../../lib/fileLanguage';
import { detectStructuredTable } from '../../lib/structuredPreview';
import { extractReportTitle, simpleMarkdown } from '../../lib/simpleMarkdown';
import { formatDateLong, useLocaleKey } from '../../lib/formatLocale';
import { StructuredDataCard } from '../canvas/StructuredDataCard';

interface ArtifactReportPreviewProps {
  file: FileContent;
  brandLabel?: string;
  expertName?: string;
  onViewSource?: () => void;
  showSourceToggle?: boolean;
}

function fileDisplayName(path: string): string {
  const segment = path.split('/').pop();
  return segment ?? path;
}

/** 工件 Markdown / 结构化数据 — 业务报告式预览（UAT 交付物视图）。 */
export function ArtifactReportPreview({
  file,
  brandLabel = 'WorkMate',
  expertName,
  onViewSource,
  showSourceToggle = false,
}: ArtifactReportPreviewProps) {
  const isMarkdown = isMarkdownPath(file.path, file.mime);
  const structuredTable = !isMarkdown ? detectStructuredTable(file.content) : null;
  const localeKey = useLocaleKey();
  const fileName = fileDisplayName(file.path);
  const title = extractReportTitle(file.content, fileName);
  const generatedAt = useMemo(() => formatDateLong(), [localeKey]);

  const markdownHtml = useMemo(() => {
    if (!isMarkdown) {
      return '';
    }
    return simpleMarkdown(file.content);
  }, [file.content, isMarkdown]);

  const handlePrint = () => {
    window.print();
  };

  return (
    <article className="artifact-report" aria-label="业务报告预览">
      <header className="artifact-report-toolbar no-print">
        <span className="artifact-report-toolbar-label">报告预览</span>
        <div className="artifact-report-toolbar-actions">
          {showSourceToggle && onViewSource && (
            <button type="button" className="btn ghost sm" onClick={onViewSource}>
              查看源码
            </button>
          )}
          <button type="button" className="btn secondary sm" onClick={handlePrint}>
            打印 / 导出
          </button>
        </div>
      </header>

      <div className="artifact-report-sheet">
        <header className="artifact-report-brand">
          <div className="artifact-report-brand-mark" aria-hidden>W</div>
          <div className="artifact-report-brand-text">
            <span className="artifact-report-brand-name">{brandLabel}</span>
            <span className="artifact-report-brand-sub">智能协作工作台 · 业务交付物</span>
          </div>
        </header>

        <div className="artifact-report-head">
          <h1 className="artifact-report-title">{title}</h1>
          <dl className="artifact-report-meta">
            <div>
              <dt>生成时间</dt>
              <dd>{generatedAt}</dd>
            </div>
            {expertName && (
              <div>
                <dt>专家团队</dt>
                <dd>{expertName}</dd>
              </div>
            )}
            <div>
              <dt>文档</dt>
              <dd>{fileName}</dd>
            </div>
          </dl>
        </div>

        <div className="artifact-report-body">
          {structuredTable && (
            <StructuredDataCard title="数据摘要" table={structuredTable} />
          )}
          {isMarkdown && (
            <div
              className="artifact-report-markdown markdown-body"
              dangerouslySetInnerHTML={{ __html: markdownHtml }}
            />
          )}
          {!isMarkdown && !structuredTable && (
            <pre className="artifact-report-fallback">{file.content}</pre>
          )}
        </div>

        <footer className="artifact-report-footer">
          <p>
            本报告由 AI 协作生成，仅供内部参考；对外使用前请经合规与业务负责人确认。
          </p>
          {file.truncated && <p className="artifact-report-truncated">内容已截断，完整版请下载源文件。</p>}
        </footer>
      </div>
    </article>
  );
}

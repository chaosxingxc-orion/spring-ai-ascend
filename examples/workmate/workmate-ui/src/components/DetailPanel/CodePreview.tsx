import { useMemo } from 'react';
import type { FileContent } from '../../types/api';
import { isMarkdownPath, languageFromPath } from '../../lib/fileLanguage';
import { highlightCode } from '../../lib/prismHighlight';
import { simpleMarkdown } from '../../lib/simpleMarkdown';

interface CodePreviewProps {
  file: FileContent;
}

export function CodePreview({ file }: CodePreviewProps) {
  const lang = languageFromPath(file.path, file.mime);
  const isMarkdown = isMarkdownPath(file.path, file.mime);

  const markdownHtml = useMemo(() => {
    if (!isMarkdown) {
      return '';
    }
    return simpleMarkdown(file.content);
  }, [file.content, isMarkdown]);

  const highlighted = useMemo(() => {
    if (isMarkdown) {
      return '';
    }
    return highlightCode(file.content, lang);
  }, [file.content, isMarkdown, lang]);

  return (
    <div className="code-preview">
      <header className="code-preview-header">
        <span className="code-preview-path" title={file.path}>{file.path}</span>
        {file.truncated && <span className="badge">已截断</span>}
        <span className="code-preview-lang">{lang}</span>
      </header>
      {isMarkdown ? (
        <div
          className="code-preview-markdown markdown-body"
          dangerouslySetInnerHTML={{ __html: markdownHtml }}
        />
      ) : (
        <pre className="code-preview-pre">
          <code
            className={`language-${lang}`}
            dangerouslySetInnerHTML={{ __html: highlighted }}
          />
        </pre>
      )}
    </div>
  );
}

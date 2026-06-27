import Editor from '@monaco-editor/react';
import type { FileContent } from '../../types/api';
import { isMarkdownPath } from '../../lib/fileLanguage';
import { monacoEditorTheme, monacoLanguageFromPath, preferMonacoEditor } from '../../lib/monacoLanguage';
import { CodePreview } from './CodePreview';

interface CodeViewerProps {
  file: FileContent;
}

export function CodeViewer({ file }: CodeViewerProps) {
  const isMarkdown = isMarkdownPath(file.path, file.mime);
  const useMonaco = !isMarkdown && preferMonacoEditor(file.content.length, file.truncated);

  if (!useMonaco) {
    return <CodePreview file={file} />;
  }

  const language = monacoLanguageFromPath(file.path, file.mime);

  return (
    <div className="code-viewer">
      <header className="code-preview-header">
        <span className="code-preview-path" title={file.path}>{file.path}</span>
        {file.truncated && <span className="badge">已截断</span>}
        <span className="code-preview-lang">{language}</span>
      </header>
      <div className="code-viewer-editor">
        <Editor
          height="100%"
          language={language}
          value={file.content}
          theme={monacoEditorTheme()}
          options={{
            readOnly: true,
            minimap: { enabled: false },
            scrollBeyondLastLine: false,
            wordWrap: 'on',
            fontSize: 13,
            lineNumbers: 'on',
          }}
        />
      </div>
    </div>
  );
}

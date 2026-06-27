import { PromptEditor } from './PromptEditor';

export interface StudioSourceFile {
  id: string;
  label: string;
  language: 'markdown' | 'yaml' | string;
  value: string;
  readOnly?: boolean;
}

interface StudioSourceEditorProps {
  files: StudioSourceFile[];
  activeFileId: string;
  onActiveFileChange: (fileId: string) => void;
  onFileChange: (fileId: string, value: string) => void;
  editorKeyPrefix: string;
}

export function StudioSourceEditor({
  files,
  activeFileId,
  onActiveFileChange,
  onFileChange,
  editorKeyPrefix,
}: StudioSourceEditorProps) {
  const activeFile = files.find((file) => file.id === activeFileId) ?? files[0];
  if (!activeFile) {
    return null;
  }

  return (
    <section className="dev-studio-source-editor">
      <div className="dev-studio-source-tabs" role="tablist" aria-label="源文件">
        {files.map((file) => (
          <button
            key={file.id}
            type="button"
            role="tab"
            aria-selected={file.id === activeFile.id}
            className={`dev-studio-source-tab${file.id === activeFile.id ? ' active' : ''}`}
            onClick={() => onActiveFileChange(file.id)}
          >
            {file.label}
            {file.readOnly ? <span className="dev-studio-source-tab-badge">只读</span> : null}
          </button>
        ))}
      </div>
      <PromptEditor
        editorKey={`${editorKeyPrefix}-${activeFile.id}`}
        label={activeFile.label}
        language={activeFile.language}
        value={activeFile.value}
        readOnly={activeFile.readOnly}
        hideHeader
        onChange={(value) => onFileChange(activeFile.id, value)}
      />
    </section>
  );
}

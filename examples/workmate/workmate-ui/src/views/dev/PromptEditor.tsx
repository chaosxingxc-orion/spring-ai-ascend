import Editor from '@monaco-editor/react';
import { useEffect, useRef } from 'react';
import type { editor } from 'monaco-editor';
import { monacoEditorTheme } from '../../lib/monacoLanguage';

interface PromptEditorProps {
  value: string;
  onChange: (value: string) => void;
  language?: string;
  readOnly?: boolean;
  label?: string;
  editorKey?: string;
  hideHeader?: boolean;
  height?: string;
  compact?: boolean;
}

export function PromptEditor({
  value,
  onChange,
  language = 'markdown',
  readOnly = false,
  label = 'Prompt',
  editorKey,
  hideHeader = false,
  height = '360px',
  compact = false,
}: PromptEditorProps) {
  const mountKey = editorKey ?? label;
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);
  const acceptingChangesRef = useRef(false);

  useEffect(() => {
    acceptingChangesRef.current = false;
    const timer = window.setTimeout(() => {
      acceptingChangesRef.current = true;
    }, 0);
    return () => window.clearTimeout(timer);
  }, [mountKey]);

  useEffect(() => {
    const editor = editorRef.current;
    if (!editor) {
      return;
    }
    const current = editor.getValue();
    if (current !== value) {
      editor.setValue(value);
    }
  }, [mountKey, value]);

  return (
    <section className={`dev-studio-editor-pane${compact ? ' dev-studio-editor-pane-compact' : ''}`}>
      {!hideHeader && (
        <header className="dev-studio-editor-pane-header">
          <h2>{label}</h2>
        </header>
      )}
      <div className="dev-studio-monaco-wrap">
        <Editor
          key={mountKey}
          height={height}
          language={language}
          defaultValue={value}
          onMount={(editor) => {
            editorRef.current = editor;
          }}
          onChange={(next) => {
            if (!acceptingChangesRef.current) {
              return;
            }
            onChange(next ?? '');
          }}
          theme={monacoEditorTheme()}
          options={{
            readOnly,
            minimap: { enabled: false },
            scrollBeyondLastLine: false,
            wordWrap: 'on',
            fontSize: 13,
            lineNumbers: 'on',
          }}
        />
      </div>
    </section>
  );
}

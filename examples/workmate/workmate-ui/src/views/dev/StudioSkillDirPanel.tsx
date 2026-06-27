import { useCallback, useEffect, useMemo, useState } from 'react';
import { getStudioSkillFileContent, listStudioSkillFiles } from '../../api/studio';
import { monacoLanguageFromPath } from '../../lib/monacoLanguage';
import type { StudioSkillFileEntry } from '../../types/studio';
import { PromptEditor } from './PromptEditor';

const PRIMARY_FILES = new Set(['skill.yaml', 'skill.yml', 'SKILL.md', 'skill.md']);

interface StudioSkillDirPanelProps {
  skillId: string;
}

export function StudioSkillDirPanel({ skillId }: StudioSkillDirPanelProps) {
  const [files, setFiles] = useState<StudioSkillFileEntry[]>([]);
  const [query, setQuery] = useState('');
  const [selectedPath, setSelectedPath] = useState<string | null>(null);
  const [content, setContent] = useState('');
  const [binary, setBinary] = useState(false);
  const [truncated, setTruncated] = useState(false);
  const [loadingList, setLoadingList] = useState(true);
  const [loadingFile, setLoadingFile] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadFiles = useCallback(async () => {
    setLoadingList(true);
    setError(null);
    try {
      const entries = await listStudioSkillFiles(skillId);
      setFiles(entries.filter((entry) => !PRIMARY_FILES.has(entry.path.split('/').pop() ?? entry.path)));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoadingList(false);
    }
  }, [skillId]);

  useEffect(() => {
    void loadFiles();
  }, [loadFiles]);

  useEffect(() => {
    if (files.length === 0) {
      setSelectedPath(null);
      return;
    }
    setSelectedPath((current) => {
      if (current && files.some((file) => file.path === current)) {
        return current;
      }
      return files[0]?.path ?? null;
    });
  }, [files]);

  useEffect(() => {
    if (!selectedPath) {
      setContent('');
      setBinary(false);
      setTruncated(false);
      return;
    }
    let cancelled = false;
    setLoadingFile(true);
    setError(null);
    void getStudioSkillFileContent(skillId, selectedPath)
      .then((file) => {
        if (cancelled) {
          return;
        }
        setContent(file.content);
        setBinary(file.binary);
        setTruncated(file.truncated);
      })
      .catch((err: Error) => {
        if (!cancelled) {
          setError(err.message);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingFile(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [skillId, selectedPath]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) {
      return files;
    }
    return files.filter((file) => file.path.toLowerCase().includes(q));
  }, [files, query]);

  if (loadingList) {
    return <p className="muted">加载技能目录文件…</p>;
  }

  if (files.length === 0) {
    return null;
  }

  return (
    <section className="dev-studio-skill-dir">
      <header className="dev-studio-skill-dir-header">
        <h2>目录文件</h2>
        <p className="muted">scripts、references、tools 等附属文件（只读预览）</p>
      </header>
      <div className="dev-studio-skill-dir-layout">
        <aside className="dev-studio-skill-dir-list-pane">
          <input
            type="search"
            className="dev-studio-search dev-studio-skill-dir-search"
            placeholder="搜索路径…"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
          <ul className="dev-studio-skill-dir-list" role="listbox" aria-label="技能目录文件">
            {filtered.map((file) => (
              <li key={file.path}>
                <button
                  type="button"
                  role="option"
                  aria-selected={selectedPath === file.path}
                  className={`dev-studio-skill-dir-item${selectedPath === file.path ? ' active' : ''}`}
                  onClick={() => setSelectedPath(file.path)}
                >
                  <span className="dev-studio-skill-dir-path">{file.path}</span>
                  {!file.textReadable && <span className="dev-studio-source-tab-badge">二进制</span>}
                </button>
              </li>
            ))}
          </ul>
          {filtered.length === 0 && <p className="muted dev-studio-empty">没有匹配的文件</p>}
        </aside>
        <div className="dev-studio-skill-dir-editor">
          {!selectedPath && <p className="muted dev-studio-empty">选择左侧文件查看内容</p>}
          {selectedPath && loadingFile && <p className="muted">加载文件…</p>}
          {selectedPath && !loadingFile && binary && (
            <p className="muted dev-studio-empty">该文件为二进制或不可文本预览，请使用「导出 office」下载完整目录。</p>
          )}
          {selectedPath && !loadingFile && !binary && (
            <>
              <div className="dev-studio-skill-dir-file-label">
                <code>{selectedPath}</code>
              </div>
              {truncated && <p className="dev-studio-message">文件较大，仅显示前 512KB。</p>}
              <PromptEditor
                editorKey={`${skillId}-${selectedPath}`}
                label={selectedPath}
                language={monacoLanguageFromPath(selectedPath)}
                value={content}
                readOnly
                hideHeader
                compact
                height="200px"
                onChange={() => {}}
              />
            </>
          )}
        </div>
      </div>
      {error && <div className="dev-studio-error">{error}</div>}
    </section>
  );
}

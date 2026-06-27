import { isHtmlPath } from '../lib/fileLanguage';

interface OpenResultCardProps {
  path: string;
  name: string;
  mime: string;
  preferredTab?: 'browser' | 'source' | 'changes';
  onOpen?: (path: string, tab?: 'browser' | 'changes') => void;
}

export function OpenResultCard({ path, name, mime, preferredTab, onOpen }: OpenResultCardProps) {
  const isHtml = preferredTab === 'browser' || isHtmlPath(path, mime);
  const actionLabel = isHtml ? '在右侧预览' : '在右栏打开';

  return (
    <article className="open-result-card">
      <div className="open-result-card-body">
        <span className="open-result-icon" aria-hidden>{isHtml ? '🌐' : '📄'}</span>
        <div className="open-result-copy">
          <span className="open-result-title">已生成工件</span>
          <span className="open-result-path" title={path}>{name}</span>
        </div>
      </div>
      {onOpen && (
        <button
          type="button"
          className="btn primary sm open-result-cta"
          onClick={() => onOpen(path, isHtml ? 'browser' : preferredTab === 'changes' ? 'changes' : undefined)}
        >
          {actionLabel}
        </button>
      )}
    </article>
  );
}

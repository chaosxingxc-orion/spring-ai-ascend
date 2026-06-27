import { useCallback, useMemo, type MouseEvent } from 'react';
import { prepareAssistantMarkdown } from '../lib/assistantMarkdown';
import { simpleMarkdown } from '../lib/simpleMarkdown';

interface MarkdownContentProps {
  source: string;
  className?: string;
  knownWorkspacePaths?: ReadonlySet<string>;
  onWorkspaceFileClick?: (path: string) => void;
}

export function MarkdownContent({
  source,
  className,
  knownWorkspacePaths,
  onWorkspaceFileClick,
}: MarkdownContentProps) {
  const prepared = useMemo(() => prepareAssistantMarkdown(source), [source]);
  const html = useMemo(
    () => simpleMarkdown(prepared, { knownWorkspacePaths }),
    [knownWorkspacePaths, prepared],
  );

  const handleClick = useCallback((event: MouseEvent<HTMLDivElement>) => {
    if (!onWorkspaceFileClick) {
      return;
    }
    const target = event.target as HTMLElement | null;
    const button = target?.closest<HTMLButtonElement>('button.md-ws-file');
    const path = button?.dataset.wsPath;
    if (path) {
      event.preventDefault();
      onWorkspaceFileClick(path);
    }
  }, [onWorkspaceFileClick]);

  return (
    <div
      className={className}
      onClick={handleClick}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}

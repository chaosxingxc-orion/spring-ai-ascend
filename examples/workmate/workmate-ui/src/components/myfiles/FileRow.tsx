import { useEffect, useRef, useState } from 'react';
import type { MyFile } from '../../types/api';
import { formatDateTime, formatFileSize } from '../../lib/formatLocale';

export type MyFileAction =
  | 'openTask'
  | 'rename'
  | 'move'
  | 'download'
  | 'favorite'
  | 'unfavorite'
  | 'share'
  | 'delete';

interface FileRowProps {
  file: MyFile;
  busy?: boolean;
  onAction: (action: MyFileAction, file: MyFile) => void;
}

export function FileRow({ file, busy = false, onAction }: FileRowProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!menuOpen) {
      return;
    }
    const close = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [menuOpen]);

  const run = (action: MyFileAction) => {
    setMenuOpen(false);
    onAction(action, file);
  };

  return (
    <tr className="myfiles-row">
      <td className="myfiles-cell-name" data-label="">
        <button
          type="button"
          className="myfiles-name-btn"
          title={file.path}
          onClick={() => onAction('openTask', file)}
        >
          {file.favorite && <span className="myfiles-star" aria-label="已收藏">★</span>}
          <span>{file.name}</span>
        </button>
        <span className="myfiles-path muted">{file.path}</span>
      </td>
      <td className="myfiles-cell-task" data-label="所属任务">
        <button type="button" className="btn ghost sm" onClick={() => onAction('openTask', file)}>
          {file.sessionTitle}
        </button>
      </td>
      <td className="myfiles-cell-time" data-label="更新时间">{formatDateTime(file.updatedAt)}</td>
      <td className="myfiles-cell-size" data-label="大小">{formatFileSize(file.size)}</td>
      <td className="myfiles-cell-actions">
        <div className="myfiles-actions" ref={menuRef}>
          <button
            type="button"
            className="btn ghost sm"
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            disabled={busy}
            onClick={() => setMenuOpen((open) => !open)}
          >
            ⋯
          </button>
          {menuOpen && (
            <div className="myfiles-menu" role="menu">
              <button type="button" role="menuitem" onClick={() => run('openTask')}>
                打开所属任务
              </button>
              <button type="button" role="menuitem" onClick={() => run('rename')}>
                重命名
              </button>
              <button type="button" role="menuitem" onClick={() => run('move')}>
                移动
              </button>
              <button type="button" role="menuitem" onClick={() => run('download')}>
                下载
              </button>
              <button type="button" role="menuitem" onClick={() => run(file.favorite ? 'unfavorite' : 'favorite')}>
                {file.favorite ? '取消收藏' : '收藏'}
              </button>
              <button type="button" role="menuitem" onClick={() => run('share')}>
                复制分享链接
              </button>
              <button type="button" role="menuitem" className="danger" onClick={() => run('delete')}>
                删除
              </button>
            </div>
          )}
        </div>
      </td>
    </tr>
  );
}

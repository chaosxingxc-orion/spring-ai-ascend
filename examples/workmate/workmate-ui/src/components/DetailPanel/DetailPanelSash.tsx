import { useCallback, useEffect, useRef } from 'react';
import {
  DETAIL_PANEL_WIDTH_MAX,
  DETAIL_PANEL_WIDTH_MIN,
  DETAIL_PANEL_WIDTH_DEFAULT,
} from '../../lib/detailPanelPrefs';

interface DetailPanelSashProps {
  width: number;
  onWidthChange: (width: number) => void;
}

/** W39-A2 — 右栏宽度拖拽（Sash 280–480px）。 */
export function DetailPanelSash({ onWidthChange }: DetailPanelSashProps) {
  const draggingRef = useRef(false);

  const onMouseMove = useCallback(
    (event: MouseEvent) => {
      if (!draggingRef.current) {
        return;
      }
      const next = window.innerWidth - event.clientX;
      onWidthChange(Math.min(DETAIL_PANEL_WIDTH_MAX, Math.max(DETAIL_PANEL_WIDTH_MIN, next)));
    },
    [onWidthChange],
  );

  const stopDragging = useCallback(() => {
    draggingRef.current = false;
    document.body.classList.remove('detail-sash-dragging');
  }, []);

  useEffect(() => {
    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', stopDragging);
    return () => {
      window.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('mouseup', stopDragging);
    };
  }, [onMouseMove, stopDragging]);

  return (
    <button
      type="button"
      className="detail-panel-sash"
      aria-label="拖拽调整右栏宽度"
      title="拖拽调整宽度；双击恢复默认"
      onMouseDown={(event) => {
        event.preventDefault();
        draggingRef.current = true;
        document.body.classList.add('detail-sash-dragging');
      }}
      onDoubleClick={() => onWidthChange(DETAIL_PANEL_WIDTH_DEFAULT)}
    />
  );
}

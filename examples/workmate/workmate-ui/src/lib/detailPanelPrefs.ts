export const DETAIL_PANEL_WIDTH_MIN = 280;
export const DETAIL_PANEL_WIDTH_MAX = 480;
export const DETAIL_PANEL_WIDTH_DEFAULT = 360;

const VISIBLE_KEY = 'workmate.detailPanel.visible';
const WIDTH_KEY = 'workmate.detailPanel.width';

function clampWidth(width: number): number {
  return Math.min(DETAIL_PANEL_WIDTH_MAX, Math.max(DETAIL_PANEL_WIDTH_MIN, width));
}

export function loadDetailPanelVisible(): boolean {
  const stored = localStorage.getItem(VISIBLE_KEY);
  if (stored === null) {
    // Collapsed by default — the workbench opens focused on the conversation;
    // the user (or an explicit open-artifact action) expands the panel on demand.
    return false;
  }
  return stored === 'true';
}

export function saveDetailPanelVisible(visible: boolean): void {
  localStorage.setItem(VISIBLE_KEY, String(visible));
}

export function loadDetailPanelWidth(): number {
  const stored = localStorage.getItem(WIDTH_KEY);
  if (!stored) {
    return DETAIL_PANEL_WIDTH_DEFAULT;
  }
  const parsed = Number.parseInt(stored, 10);
  return Number.isFinite(parsed) ? clampWidth(parsed) : DETAIL_PANEL_WIDTH_DEFAULT;
}

export function saveDetailPanelWidth(width: number): void {
  localStorage.setItem(WIDTH_KEY, String(clampWidth(width)));
}

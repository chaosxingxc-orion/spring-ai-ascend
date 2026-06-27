import type { WorkmateDesktopBridge } from '../types/desktopBridge';

/** Electron preload 暴露的 `window.workmateDesktop`（Web 上为 undefined）。 */
export function getDesktopBridge(): WorkmateDesktopBridge | undefined {
  return window.workmateDesktop;
}

export function hasDesktopBridge(): boolean {
  return Boolean(getDesktopBridge());
}

export type { WorkmateDesktopBridge };

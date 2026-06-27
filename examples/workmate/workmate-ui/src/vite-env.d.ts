/// <reference types="vite/client" />

import type { WorkmateDesktopBridge } from './types/desktopBridge';

interface ImportMetaEnv {
  readonly VITE_API_BASE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare global {
  interface Window {
    workmateDesktop?: WorkmateDesktopBridge;
  }
}

export {};

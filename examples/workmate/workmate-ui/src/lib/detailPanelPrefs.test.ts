import { describe, expect, it, beforeEach } from 'vitest';
import {
  DETAIL_PANEL_WIDTH_DEFAULT,
  DETAIL_PANEL_WIDTH_MAX,
  DETAIL_PANEL_WIDTH_MIN,
  loadDetailPanelVisible,
  loadDetailPanelWidth,
  saveDetailPanelVisible,
  saveDetailPanelWidth,
} from './detailPanelPrefs';

class MemoryStorage implements Storage {
  private store = new Map<string, string>();

  get length() {
    return this.store.size;
  }

  clear(): void {
    this.store.clear();
  }

  getItem(key: string): string | null {
    return this.store.get(key) ?? null;
  }

  key(index: number): string | null {
    return [...this.store.keys()][index] ?? null;
  }

  removeItem(key: string): void {
    this.store.delete(key);
  }

  setItem(key: string, value: string): void {
    this.store.set(key, value);
  }
}

describe('detailPanelPrefs', () => {
  beforeEach(() => {
    globalThis.localStorage = new MemoryStorage();
  });

  it('defaults collapsed with default width', () => {
    expect(loadDetailPanelVisible()).toBe(false);
    expect(loadDetailPanelWidth()).toBe(DETAIL_PANEL_WIDTH_DEFAULT);
  });

  it('respects a stored visible preference', () => {
    saveDetailPanelVisible(true);
    expect(loadDetailPanelVisible()).toBe(true);
  });

  it('persists visibility', () => {
    saveDetailPanelVisible(false);
    expect(loadDetailPanelVisible()).toBe(false);
  });

  it('clamps width', () => {
    saveDetailPanelWidth(999);
    expect(loadDetailPanelWidth()).toBe(DETAIL_PANEL_WIDTH_MAX);
    saveDetailPanelWidth(100);
    expect(loadDetailPanelWidth()).toBe(DETAIL_PANEL_WIDTH_MIN);
  });
});

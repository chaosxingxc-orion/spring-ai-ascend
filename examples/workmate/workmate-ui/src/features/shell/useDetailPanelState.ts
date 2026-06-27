import { useCallback, useEffect, useState } from 'react';
import {
  loadDetailPanelVisible,
  loadDetailPanelWidth,
  saveDetailPanelVisible,
  saveDetailPanelWidth,
} from '../../lib/detailPanelPrefs';

export const DETAIL_PANEL_MIN_VIEWPORT = 1100;

export function useDetailPanelState() {
  const [detailPanelVisible, setDetailPanelVisible] = useState(loadDetailPanelVisible);
  const [detailPanelWidth, setDetailPanelWidth] = useState(loadDetailPanelWidth);
  const [detailPanelSupported, setDetailPanelSupported] = useState(() => {
    if (typeof window === 'undefined') {
      return true;
    }
    return window.matchMedia(`(min-width: ${DETAIL_PANEL_MIN_VIEWPORT + 1}px)`).matches;
  });
  const [artifactAutoOpenPath, setArtifactAutoOpenPath] = useState<string | null>(null);
  const [artifactAutoOpenMode, setArtifactAutoOpenMode] = useState<'preview' | 'changes' | null>(null);
  const [detailFocusTab, setDetailFocusTab] = useState<'artifacts' | 'files' | 'changes' | null>(null);
  const [detailMemberFocus, setDetailMemberFocus] = useState<{ id: string; name: string } | null>(null);

  useEffect(() => {
    if (typeof window === 'undefined') {
      return undefined;
    }
    const media = window.matchMedia(`(min-width: ${DETAIL_PANEL_MIN_VIEWPORT + 1}px)`);
    const sync = () => setDetailPanelSupported(media.matches);
    sync();
    media.addEventListener('change', sync);
    return () => media.removeEventListener('change', sync);
  }, []);

  const handleOpenDetailTab = useCallback((tab: 'artifacts' | 'changes') => {
    if (!detailPanelSupported) {
      return;
    }
    setDetailPanelVisible(true);
    saveDetailPanelVisible(true);
    setDetailFocusTab(tab);
  }, [detailPanelSupported]);

  const handleOpenTeamMember = useCallback((memberId: string, memberName: string) => {
    setDetailPanelVisible(true);
    saveDetailPanelVisible(true);
    setDetailMemberFocus({ id: memberId, name: memberName });
  }, []);

  const handleOpenArtifact = useCallback((path: string, tab?: 'browser' | 'changes') => {
    setDetailPanelVisible(true);
    saveDetailPanelVisible(true);
    setArtifactAutoOpenPath(path);
    setArtifactAutoOpenMode(tab === 'changes' ? 'changes' : 'preview');
  }, []);

  const handleOpenChanges = useCallback((path: string) => {
    setDetailPanelVisible(true);
    saveDetailPanelVisible(true);
    setArtifactAutoOpenPath(path);
    setArtifactAutoOpenMode('changes');
  }, []);

  const handleToggleDetailPanel = useCallback(() => {
    if (!detailPanelSupported) {
      return;
    }
    setDetailPanelVisible((visible) => {
      const next = !visible;
      saveDetailPanelVisible(next);
      return next;
    });
  }, [detailPanelSupported]);

  const handleDetailPanelWidthChange = useCallback((width: number) => {
    setDetailPanelWidth(width);
    saveDetailPanelWidth(width);
  }, []);

  return {
    detailPanelVisible,
    setDetailPanelVisible,
    detailPanelWidth,
    detailPanelSupported,
    artifactAutoOpenPath,
    setArtifactAutoOpenPath,
    artifactAutoOpenMode,
    setArtifactAutoOpenMode,
    detailFocusTab,
    setDetailFocusTab,
    detailMemberFocus,
    setDetailMemberFocus,
    handleOpenDetailTab,
    handleOpenTeamMember,
    handleOpenArtifact,
    handleOpenChanges,
    handleToggleDetailPanel,
    handleDetailPanelWidthChange,
  };
}

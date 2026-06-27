import { useCallback, useEffect, useMemo, useState } from 'react';
import { listConnectors, listSkills } from '../../api/market';
import { searchWorkspaceFiles } from '../../api/client';
import type { Expert, Artifact } from '../../types/api';
import type { ConnectorInfo, SkillInfo } from '../../types/market';
import type { MentionRef } from '../../types/mention';
import type { MentionMenuItem } from '../../components/MentionMenu';
import { detectMentionTrigger } from '../../lib/mentionParse';
import { connectorsToMentionItems, skillsToMentionItems } from '../../lib/marketMentionItems';

interface UseMentionMenuOptions {
  sessionId?: string | null;
  expert?: Expert | null;
  catalogSkills?: SkillInfo[];
  catalogConnectors?: ConnectorInfo[];
  onCatalogSkillsChange?: (skills: SkillInfo[]) => void;
  onCatalogConnectorsChange?: (connectors: ConnectorInfo[]) => void;
}

function artifactToMenuItem(artifact: Artifact): MentionMenuItem {
  return {
    type: 'file',
    id: artifact.path,
    path: artifact.path,
    label: artifact.name,
    hint: artifact.path,
  };
}

export function useMentionMenu({
  sessionId,
  expert,
  catalogSkills,
  catalogConnectors,
  onCatalogSkillsChange,
  onCatalogConnectorsChange,
}: UseMentionMenuOptions) {
  const [open, setOpen] = useState(false);
  const [trigger, setTrigger] = useState<'@' | '/' | null>(null);
  const [query, setQuery] = useState('');
  const [tokenStart, setTokenStart] = useState(-1);
  const [activeIndex, setActiveIndex] = useState(0);
  const [loading, setLoading] = useState(false);
  const [files, setFiles] = useState<MentionMenuItem[]>([]);
  const [skills, setSkills] = useState<MentionMenuItem[]>([]);
  const [connectors, setConnectors] = useState<MentionMenuItem[]>([]);
  const [pendingMentions, setPendingMentions] = useState<MentionRef[]>([]);

  const members = useMemo<MentionMenuItem[]>(() => {
    if (!expert?.members?.length) {
      return [];
    }
    return expert.members.map((member) => ({
      type: 'member' as const,
      id: member.id,
      label: member.nickname ?? member.name,
      hint: member.role ?? member.expertId,
    }));
  }, [expert]);

  useEffect(() => {
    if (!open || trigger !== '/') {
      return;
    }
    if (catalogSkills?.length) {
      setSkills(skillsToMentionItems(catalogSkills));
    }
    setLoading(!catalogSkills?.length);
    void listSkills()
      .then((items: SkillInfo[]) => {
        const mapped = skillsToMentionItems(items);
        setSkills(mapped);
        onCatalogSkillsChange?.(items);
      })
      .finally(() => setLoading(false));
  }, [open, trigger, catalogSkills, onCatalogSkillsChange]);

  useEffect(() => {
    if (!open || trigger !== '@') {
      return;
    }
    if (catalogConnectors?.length) {
      setConnectors(connectorsToMentionItems(catalogConnectors));
    }
    setLoading(!catalogConnectors?.length);
    void listConnectors()
      .then((items: ConnectorInfo[]) => {
        const mapped = connectorsToMentionItems(items);
        setConnectors(mapped);
        onCatalogConnectorsChange?.(items);
      })
      .finally(() => setLoading(false));
  }, [open, trigger, catalogConnectors, onCatalogConnectorsChange]);

  useEffect(() => {
    if (!open || trigger !== '@' || !sessionId) {
      return;
    }
    setLoading(true);
    const delay = query.trim() ? 150 : 0;
    const handle = window.setTimeout(() => {
      void searchWorkspaceFiles(sessionId, query, 30)
        .then((artifacts: Artifact[]) => setFiles(artifacts.map(artifactToMenuItem)))
        .catch(() => setFiles([]))
        .finally(() => setLoading(false));
    }, delay);
    return () => window.clearTimeout(handle);
  }, [open, query, sessionId, trigger]);

  const items = useMemo(() => {
    const q = query.trim().toLowerCase();
    const match = (item: MentionMenuItem) =>
      !q ||
      item.label.toLowerCase().includes(q) ||
      item.id.toLowerCase().includes(q) ||
      item.hint?.toLowerCase().includes(q);

    if (trigger === '/') {
      return skills.filter(match);
    }
    if (trigger === '@') {
      return [...files, ...members, ...connectors].filter(match);
    }
    return [];
  }, [connectors, files, members, query, skills, trigger]);

  useEffect(() => {
    setActiveIndex(0);
  }, [items.length, query, trigger]);

  const closeMenu = useCallback(() => {
    setOpen(false);
    setTrigger(null);
    setQuery('');
    setTokenStart(-1);
  }, []);

  const syncFromTextarea = useCallback((value: string, cursor: number) => {
    const detected = detectMentionTrigger(value, cursor);
    if (detected.trigger) {
      setOpen(true);
      setTrigger(detected.trigger);
      setQuery(detected.query);
      setTokenStart(detected.start);
    } else {
      closeMenu();
    }
  }, [closeMenu]);

  const selectItem = useCallback(
    (item: MentionMenuItem, textarea: HTMLTextAreaElement) => {
      const mention: MentionRef = {
        type: item.type,
        id: item.id,
        path: item.path,
        label: item.label,
      };
      setPendingMentions((prev) => {
        if (prev.some((existing) => existing.type === mention.type && existing.id === mention.id)) {
          return prev;
        }
        return [...prev, mention];
      });
      if (tokenStart >= 0) {
        const before = textarea.value.slice(0, tokenStart);
        const after = textarea.value.slice(textarea.selectionStart);
        textarea.value = `${before}${after}`.replace(/\s{2,}/g, ' ').trimStart();
        const cursor = before.length;
        textarea.setSelectionRange(cursor, cursor);
      }
      closeMenu();
      textarea.focus();
    },
    [closeMenu, tokenStart],
  );

  const removeMention = useCallback((index: number) => {
    setPendingMentions((prev) => prev.filter((_, i) => i !== index));
  }, []);

  const clearMentions = useCallback(() => {
    setPendingMentions([]);
  }, []);

  const addMention = useCallback((mention: MentionRef) => {
    setPendingMentions((prev) => {
      if (prev.some((existing) => existing.type === mention.type && existing.id === mention.id)) {
        return prev;
      }
      return [...prev, mention];
    });
  }, []);

  const moveActive = useCallback(
    (delta: number) => {
      if (items.length === 0) {
        return;
      }
      setActiveIndex((prev) => (prev + delta + items.length) % items.length);
    },
    [items.length],
  );

  return {
    open,
    trigger,
    query,
    items,
    loading,
    activeIndex,
    pendingMentions,
    syncFromTextarea,
    closeMenu,
    selectItem,
    removeMention,
    clearMentions,
    addMention,
    moveActive,
    setActiveIndex,
  };
}

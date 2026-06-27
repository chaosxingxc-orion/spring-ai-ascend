import { useEffect, useMemo, useRef, useState } from 'react';
import type { ChatItem } from '../types/events';
import { findSearchHits } from '../lib/sessionSearch';

interface SessionChatSearchProps {
  items: ChatItem[];
  open: boolean;
  onClose: () => void;
  onActiveHitChange: (itemId: string | null) => void;
}

/** F5 — in-session transcript search with next/previous navigation. */
export function SessionChatSearch({
  items,
  open,
  onClose,
  onActiveHitChange,
}: SessionChatSearchProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState('');
  const [cursor, setCursor] = useState(0);

  const hits = useMemo(() => findSearchHits(items, query), [items, query]);

  useEffect(() => {
    if (open) {
      inputRef.current?.focus();
    } else {
      setQuery('');
      setCursor(0);
      onActiveHitChange(null);
    }
  }, [open, onActiveHitChange]);

  useEffect(() => {
    if (!open) {
      return;
    }
    if (hits.length === 0) {
      onActiveHitChange(null);
      return;
    }
    const safeCursor = ((cursor % hits.length) + hits.length) % hits.length;
    if (safeCursor !== cursor) {
      setCursor(safeCursor);
      return;
    }
    onActiveHitChange(hits[safeCursor]?.itemId ?? null);
  }, [cursor, hits, onActiveHitChange, open]);

  useEffect(() => {
    setCursor(0);
  }, [query]);

  if (!open) {
    return null;
  }

  const statusLabel =
    query.trim() === ''
      ? '输入关键词'
      : hits.length === 0
        ? '无匹配'
        : `${cursor + 1}/${hits.length}`;

  return (
    <div className="session-chat-search" role="search">
      <input
        ref={inputRef}
        type="search"
        className="session-chat-search-input"
        placeholder="搜索会话内容…"
        value={query}
        aria-label="搜索会话内容"
        onChange={(event) => setQuery(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            event.preventDefault();
            onClose();
          }
          if (event.key === 'Enter') {
            event.preventDefault();
            if (event.shiftKey) {
              setCursor((value) => value - 1);
            } else {
              setCursor((value) => value + 1);
            }
          }
        }}
      />
      <span className="session-chat-search-status" aria-live="polite">
        {statusLabel}
      </span>
      <button
        type="button"
        className="btn ghost sm"
        disabled={hits.length === 0}
        aria-label="上一个匹配"
        onClick={() => setCursor((value) => value - 1)}
      >
        ↑
      </button>
      <button
        type="button"
        className="btn ghost sm"
        disabled={hits.length === 0}
        aria-label="下一个匹配"
        onClick={() => setCursor((value) => value + 1)}
      >
        ↓
      </button>
      <button type="button" className="btn ghost sm" aria-label="关闭搜索" onClick={onClose}>
        关闭
      </button>
    </div>
  );
}

import { useCallback, useEffect, useRef, useState } from 'react';

const BOTTOM_THRESHOLD_PX = 80;

function isNearBottom(element: HTMLElement): boolean {
  return element.scrollHeight - element.scrollTop - element.clientHeight < BOTTOM_THRESHOLD_PX;
}

interface UseChatScrollOptions {
  itemCount: number;
  streaming: boolean;
}

/** F5 — autoscroll while pinned; show scroll-to-bottom when user reads history. */
export function useChatScroll({ itemCount, streaming }: UseChatScrollOptions) {
  const containerRef = useRef<HTMLDivElement>(null);
  const pinnedRef = useRef(true);
  const [showScrollButton, setShowScrollButton] = useState(false);

  const scrollToBottom = useCallback((behavior: ScrollBehavior = 'smooth') => {
    const element = containerRef.current;
    if (!element) {
      return;
    }
    const anchor = element.querySelector('.chat-messages-anchor');
    if (anchor instanceof HTMLElement) {
      anchor.scrollIntoView({ behavior, block: 'end' });
    } else {
      element.scrollTo({ top: element.scrollHeight, behavior });
    }
    pinnedRef.current = true;
    setShowScrollButton(false);
  }, []);

  const onScroll = useCallback(() => {
    const element = containerRef.current;
    if (!element) {
      return;
    }
    const pinned = isNearBottom(element);
    pinnedRef.current = pinned;
    setShowScrollButton(!pinned);
  }, []);

  useEffect(() => {
    if (pinnedRef.current) {
      scrollToBottom('auto');
    } else if (streaming) {
      setShowScrollButton(true);
    }
  }, [itemCount, streaming, scrollToBottom]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    const content = container.querySelector('.chat-messages-workmate');
    if (!content || !(content instanceof HTMLElement)) {
      return;
    }

    const maybeScroll = () => {
      if (pinnedRef.current) {
        scrollToBottom('auto');
      }
    };

    const observers: Array<ResizeObserver | MutationObserver> = [];
    if (typeof ResizeObserver !== 'undefined') {
      const resizeObserver = new ResizeObserver(maybeScroll);
      resizeObserver.observe(content);
      observers.push(resizeObserver);
    }

    if (streaming && typeof MutationObserver !== 'undefined') {
      const mutationObserver = new MutationObserver(maybeScroll);
      mutationObserver.observe(content, {
        childList: true,
        subtree: true,
        characterData: true,
      });
      observers.push(mutationObserver);
    }

    return () => {
      for (const observer of observers) {
        observer.disconnect();
      }
    };
  }, [itemCount, streaming, scrollToBottom]);

  return {
    containerRef,
    showScrollButton,
    scrollToBottom,
    onScroll,
  };
}

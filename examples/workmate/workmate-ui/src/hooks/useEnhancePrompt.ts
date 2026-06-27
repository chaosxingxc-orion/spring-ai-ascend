import { useCallback, useRef, useState } from 'react';
import { enhancePrompt } from '../api/client';

export function useEnhancePrompt(expertId?: string) {
  const [enhancing, setEnhancing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const enhance = useCallback(
    async (text: string): Promise<string | null> => {
      const trimmed = text.trim();
      if (!trimmed) {
        return null;
      }
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;
      setEnhancing(true);
      setError(null);
      try {
        const result = await enhancePrompt(trimmed, expertId, controller.signal);
        return result.text;
      } catch (err) {
        if ((err as Error).name === 'AbortError') {
          return null;
        }
        setError((err as Error).message);
        return null;
      } finally {
        if (abortRef.current === controller) {
          abortRef.current = null;
        }
        setEnhancing(false);
      }
    },
    [expertId],
  );

  const cancel = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setEnhancing(false);
  }, []);

  return { enhancing, error, enhance, cancel };
}

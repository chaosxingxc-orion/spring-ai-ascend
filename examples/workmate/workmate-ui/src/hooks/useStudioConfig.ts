import { useCallback, useEffect, useState } from 'react';
import { getStudioConfig } from '../api/studio';
import type { StudioConfig } from '../types/studio';

const DEFAULT_CONFIG: StudioConfig = { enabled: true, auditEnabled: true };

export function useStudioConfig() {
  const [config, setConfig] = useState<StudioConfig>(DEFAULT_CONFIG);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setConfig(await getStudioConfig());
    } catch {
      setConfig(DEFAULT_CONFIG);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { config, loading, refresh };
}

import { useCallback, useEffect, useMemo, useState } from 'react';
import { listArtifacts } from '../api/client';
import { findTeamBlackboardPath } from '../lib/artifactDisplay';

export interface SessionArtifactsInfo {
  knownWorkspacePaths: ReadonlySet<string>;
  artifactCount: number;
  blackboardPath: string | null;
}

/** Workspace artifacts for markdown links, turn chips, and blackboard CTAs. */
export function useSessionArtifacts(
  sessionId: string | undefined,
  refreshKey = 0,
): SessionArtifactsInfo {
  const [paths, setPaths] = useState<string[]>([]);

  const load = useCallback(async () => {
    if (!sessionId) {
      setPaths([]);
      return;
    }
    try {
      const items = await listArtifacts(sessionId);
      setPaths(items.map((item) => item.path));
    } catch {
      setPaths([]);
    }
  }, [sessionId]);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  return useMemo(
    () => ({
      knownWorkspacePaths: new Set(paths),
      artifactCount: paths.length,
      blackboardPath: findTeamBlackboardPath(paths),
    }),
    [paths],
  );
}

/** Workspace file paths for assistant markdown link styling (shared with DetailPanel refresh). */
export function useSessionArtifactPaths(
  sessionId: string | undefined,
  refreshKey = 0,
): ReadonlySet<string> {
  return useSessionArtifacts(sessionId, refreshKey).knownWorkspacePaths;
}

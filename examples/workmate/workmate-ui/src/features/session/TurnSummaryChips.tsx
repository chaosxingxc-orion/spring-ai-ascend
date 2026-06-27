interface TurnSummaryChipsProps {
  artifactCount: number;
  sessionArtifactCount?: number;
  changeCount: number;
  previewPath?: string;
  teamBlackboardPath?: string;
  onOpenArtifacts?: () => void;
  onOpenChanges?: () => void;
  onOpenPreview?: (path: string) => void;
  onOpenBlackboard?: (path: string) => void;
}

/** W37-B4 — assistant turn footer chips (artifacts / changes / preview). */
export function TurnSummaryChips({
  artifactCount,
  sessionArtifactCount = 0,
  changeCount,
  previewPath,
  teamBlackboardPath,
  onOpenArtifacts,
  onOpenChanges,
  onOpenPreview,
  onOpenBlackboard,
}: TurnSummaryChipsProps) {
  const effectiveArtifactCount = Math.max(artifactCount, sessionArtifactCount);

  if (effectiveArtifactCount === 0 && changeCount === 0 && !previewPath && !teamBlackboardPath) {
    return null;
  }

  return (
    <div className="turn-summary-chips" aria-label="本轮摘要">
      {effectiveArtifactCount > 0 && onOpenArtifacts && (
        <button type="button" className="turn-summary-chip" onClick={onOpenArtifacts}>
          任务产生制品 ({effectiveArtifactCount})
        </button>
      )}
      {teamBlackboardPath && onOpenBlackboard && (
        <button
          type="button"
          className="turn-summary-chip turn-summary-chip-link"
          onClick={() => onOpenBlackboard(teamBlackboardPath)}
        >
          打开团队黑板
        </button>
      )}
      {changeCount > 0 && onOpenChanges && (
        <button type="button" className="turn-summary-chip" onClick={onOpenChanges}>
          文件变更 ({changeCount})
        </button>
      )}
      {previewPath && onOpenPreview && (
        <button
          type="button"
          className="turn-summary-chip turn-summary-chip-link"
          onClick={() => onOpenPreview(previewPath)}
        >
          打开网页预览 →
        </button>
      )}
    </div>
  );
}

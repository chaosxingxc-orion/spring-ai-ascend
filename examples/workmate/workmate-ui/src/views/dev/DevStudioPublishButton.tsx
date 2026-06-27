import { useCallback, useEffect, useState } from 'react';
import { getStudioDraftMeta, publishStudioExpert, publishStudioPlaybook, publishStudioSkill, publishStudioWelcome, studioReload } from '../../api/studio';
import type { StudioDraftMeta } from '../../types/studio';

interface DevStudioPublishButtonProps {
  assetType: 'expert' | 'skill' | 'playbook' | 'welcome';
  assetId: string;
  disabled?: boolean;
  onPublished?: (meta: StudioDraftMeta) => void;
}

export function DevStudioPublishButton({ assetType, assetId, disabled, onPublished }: DevStudioPublishButtonProps) {
  const [meta, setMeta] = useState<StudioDraftMeta | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refreshMeta = useCallback(async () => {
    if (assetType === 'welcome') {
      try {
        setMeta(await getStudioDraftMeta('welcome', 'welcome'));
      } catch {
        setMeta(null);
      }
      return;
    }
    if (!assetId || assetId === 'new') {
      setMeta(null);
      return;
    }
    try {
      setMeta(await getStudioDraftMeta(assetType, assetId));
    } catch {
      setMeta(null);
    }
  }, [assetType, assetId]);

  useEffect(() => {
    void refreshMeta();
  }, [refreshMeta]);

  const handlePublish = async () => {
    setBusy(true);
    setError(null);
    try {
      const result =
        assetType === 'expert'
          ? await publishStudioExpert(assetId)
          : assetType === 'skill'
            ? await publishStudioSkill(assetId)
            : assetType === 'playbook'
              ? await publishStudioPlaybook(assetId)
              : await publishStudioWelcome();
      await studioReload();
      setMeta(result);
      onPublished?.(result);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const status = meta?.status ?? 'draft';

  return (
    <div className="dev-studio-publish">
      <span className={`dev-studio-badge dev-studio-badge-${status}`}>{status === 'published' ? '已发布' : '草稿'}</span>
      <button type="button" className="btn secondary sm" disabled={disabled || busy} onClick={() => void handlePublish()}>
        {busy ? '发布中…' : '发布'}
      </button>
      {error && <span className="dev-studio-error">{error}</span>}
    </div>
  );
}

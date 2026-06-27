import { useCallback, useEffect, useState } from 'react';
import {
  downloadStudioWelcomeExport,
  getStudioWelcomeDiff,
  getStudioWelcomeSource,
  rollbackStudioWelcome,
  updateStudioWelcome,
  validateStudioWelcome,
} from '../../api/studio';
import { sourceLabel } from '../../lib/studioForm';
import type { OfficeAssetSource } from '../../types/studio';
import { PromptEditor } from './PromptEditor';
import { StudioDiffPanel } from './StudioDiffPanel';
import { DevStudioPublishButton } from './DevStudioPublishButton';

export function WelcomeEditorPanel() {
  const [yaml, setYaml] = useState('');
  const [source, setSource] = useState<OfficeAssetSource | null>(null);
  const [sourcePath, setSourcePath] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getStudioWelcomeSource();
      setYaml(data.welcomeYaml.replace(/\n$/, ''));
      setSource(data.source);
      setSourcePath(data.sourcePath);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const result = await updateStudioWelcome({ welcomeYaml: yaml });
      setYaml(result.welcomeYaml.replace(/\n$/, ''));
      setSource(result.source);
      setSourcePath(result.sourcePath);
      setMessage('已保存草稿');
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const handleValidate = async () => {
    try {
      const result = await validateStudioWelcome({ welcomeYaml: yaml });
      setMessage(result.valid ? '校验通过' : result.message);
      if (!result.valid) {
        setError(result.message);
      }
    } catch (err) {
      setError((err as Error).message);
    }
  };

  if (loading) {
    return <p className="muted">加载 welcome.yaml…</p>;
  }

  return (
    <div className="dev-studio-editor">
      <header className="dev-studio-editor-header">
        <div>
          <h1>welcome.yaml</h1>
          {source && (
            <p className="muted">
              来源：
              <span className={`dev-studio-badge dev-studio-badge-${source.toLowerCase()}`}>
                {sourceLabel(source)}
              </span>
              {sourcePath && <> · {sourcePath}</>}
            </p>
          )}
        </div>
        <div className="dev-studio-editor-actions">
          <button type="button" className="btn ghost sm" onClick={() => void handleValidate()}>
            校验
          </button>
          <DevStudioPublishButton assetType="welcome" assetId="welcome" disabled={saving} />
          <button type="button" className="btn primary sm" onClick={() => void handleSave()} disabled={saving}>
            {saving ? '保存中…' : '保存草稿'}
          </button>
        </div>
      </header>

      {error && <div className="dev-studio-error">{error}</div>}
      {message && !error && (
        <p className="dev-studio-message" role="status">
          {message}
        </p>
      )}

      <StudioDiffPanel
        title="草稿对比"
        language="yaml"
        loadDiff={getStudioWelcomeDiff}
        onRollback={async () => {
          await rollbackStudioWelcome();
          await load();
        }}
        onExport={() => downloadStudioWelcomeExport()}
      />

      <div className="dev-studio-editor-layout dev-studio-editor-layout-full">
        <PromptEditor label="welcome.yaml" language="yaml" value={yaml} onChange={setYaml} />
      </div>
    </div>
  );
}

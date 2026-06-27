import { useCallback, useEffect, useState } from 'react';
import {
  createStudioPlaybook,
  downloadStudioPlaybookExport,
  getStudioPlaybookDiff,
  getStudioPlaybookSource,
  rollbackStudioPlaybook,
  updateStudioPlaybook,
  validateStudioPlaybook,
} from '../../api/studio';
import { isNewStudioId, joinCommaList, parseCommaList, sourceLabel } from '../../lib/studioForm';
import type { OfficeAssetSource, StudioPlaybookWriteBody } from '../../types/studio';
import { PromptEditor } from './PromptEditor';
import { StudioDiffPanel } from './StudioDiffPanel';
import { DevStudioPublishButton } from './DevStudioPublishButton';

const EMPTY_FORM: StudioPlaybookWriteBody = {
  id: '',
  title: '',
  description: '',
  accent: '#2DB89A',
  expertId: '',
  initPrompt: '描述用户点击此 Playbook 时要发送的首条消息…',
  placements: ['home-best-practice'],
};

interface PlaybookEditorPanelProps {
  playbookId: string;
  onBack: () => void;
  onCreated: (playbookId: string) => void;
}

export function PlaybookEditorPanel({ playbookId, onBack, onCreated }: PlaybookEditorPanelProps) {
  const isNew = isNewStudioId(playbookId);
  const [form, setForm] = useState<StudioPlaybookWriteBody>(EMPTY_FORM);
  const [newId, setNewId] = useState('');
  const [source, setSource] = useState<OfficeAssetSource | null>(null);
  const [placementsInput, setPlacementsInput] = useState('home-best-practice');
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (isNew) {
      setForm(EMPTY_FORM);
      setSource(null);
      setPlacementsInput('home-best-practice');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const data = await getStudioPlaybookSource(playbookId);
      setForm({
        title: data.title,
        description: data.description ?? '',
        accent: data.accent ?? '',
        expertId: data.expertId ?? '',
        initPrompt: data.initPrompt.replace(/\n$/, ''),
        placements: data.placements,
      });
      setSource(data.source);
      setPlacementsInput(joinCommaList(data.placements));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [isNew, playbookId]);

  useEffect(() => {
    void load();
  }, [load]);

  const buildPayload = (): StudioPlaybookWriteBody => ({
    ...form,
    id: isNew ? newId.trim() : playbookId,
    description: form.description?.trim() || undefined,
    accent: form.accent?.trim() || undefined,
    expertId: form.expertId?.trim() || undefined,
    placements: parseCommaList(placementsInput),
  });

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const payload = buildPayload();
      const result = isNew
        ? await createStudioPlaybook(payload)
        : await updateStudioPlaybook(playbookId, payload);
      setSource(result.source);
      setMessage('已保存草稿');
      if (isNew) {
        onCreated(result.id);
      }
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const handleValidate = async () => {
    try {
      const result = await validateStudioPlaybook(buildPayload());
      setMessage(result.valid ? '校验通过' : result.message);
      if (!result.valid) {
        setError(result.message);
      }
    } catch (err) {
      setError((err as Error).message);
    }
  };

  if (loading) {
    return <p className="muted">加载 Playbook…</p>;
  }

  return (
    <div className="dev-studio-editor">
      <header className="dev-studio-editor-header">
        <div>
          <button type="button" className="btn ghost sm" onClick={onBack}>
            ← 返回列表
          </button>
          <h1>{isNew ? '新建 Playbook' : form.title || playbookId}</h1>
          {!isNew && source && (
            <p className="muted">
              来源：
              <span className={`dev-studio-badge dev-studio-badge-${source.toLowerCase()}`}>
                {sourceLabel(source)}
              </span>
            </p>
          )}
        </div>
        <div className="dev-studio-editor-actions">
          <button type="button" className="btn ghost sm" onClick={() => void handleValidate()}>
            校验
          </button>
          {!isNew && <DevStudioPublishButton assetType="playbook" assetId={playbookId} disabled={saving} />}
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

      {!isNew && (
        <StudioDiffPanel
          title="草稿对比"
          language="yaml"
          loadDiff={() => getStudioPlaybookDiff(playbookId)}
          onRollback={async () => {
            await rollbackStudioPlaybook(playbookId);
            await load();
          }}
          onExport={() => downloadStudioPlaybookExport(playbookId)}
        />
      )}

      <div className="dev-studio-editor-layout">
        <aside className="dev-studio-form-pane">
          {isNew && (
            <label className="dev-studio-field">
              <span>ID</span>
              <input value={newId} onChange={(event) => setNewId(event.target.value)} spellCheck={false} />
            </label>
          )}
          <label className="dev-studio-field">
            <span>标题</span>
            <input value={form.title} onChange={(event) => setForm((prev) => ({ ...prev, title: event.target.value }))} />
          </label>
          <label className="dev-studio-field">
            <span>描述</span>
            <textarea
              rows={2}
              value={form.description ?? ''}
              onChange={(event) => setForm((prev) => ({ ...prev, description: event.target.value }))}
            />
          </label>
          <label className="dev-studio-field">
            <span>Accent</span>
            <input
              value={form.accent ?? ''}
              onChange={(event) => setForm((prev) => ({ ...prev, accent: event.target.value }))}
              spellCheck={false}
            />
          </label>
          <label className="dev-studio-field">
            <span>Expert ID</span>
            <input
              value={form.expertId ?? ''}
              onChange={(event) => setForm((prev) => ({ ...prev, expertId: event.target.value }))}
              spellCheck={false}
            />
          </label>
          <label className="dev-studio-field">
            <span>Placements</span>
            <input value={placementsInput} onChange={(event) => setPlacementsInput(event.target.value)} spellCheck={false} />
          </label>
        </aside>
        <PromptEditor
          label="initPrompt"
          value={form.initPrompt}
          onChange={(initPrompt) => setForm((prev) => ({ ...prev, initPrompt }))}
        />
      </div>
    </div>
  );
}

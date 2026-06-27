import { useCallback, useEffect, useState } from 'react';
import {
  createStudioExpert,
  deleteStudioExpert,
  dryRunStudioExpert,
  forkStudioExpert,
  getStudioExpertDiff,
  getStudioExpertSource,
  downloadStudioExpertExport,
  rollbackStudioExpert,
  updateStudioExpert,
  validateStudioExpert,
} from '../../api/studio';
import {
  expertSourceToForm,
  isNewStudioId,
  joinCommaList,
  normalizeExpertWriteBody,
  parseCommaList,
  sourceLabel,
} from '../../lib/studioForm';
import type { OfficeAssetSource, StudioExpertWriteBody } from '../../types/studio';
import { StudioDiffPanel } from './StudioDiffPanel';
import { StudioExpertCapabilitiesPanel } from './StudioExpertCapabilitiesPanel';
import { StudioSourceEditor } from './StudioSourceEditor';
import { DevStudioPublishButton } from './DevStudioPublishButton';

const EMPTY_FORM: StudioExpertWriteBody = {
  id: '',
  name: '',
  description: '',
  expertType: 'agent',
  promptContent: '',
  category: 'custom',
  tags: ['draft'],
  skillCompatibility: [],
  defaultInitPrompt: '',
};

interface AgentEditorPanelProps {
  expertId: string;
  onBack: () => void;
  onOpenSession: (sessionId: string, initialMessage?: string) => void;
  onCreated: (expertId: string) => void;
  onOpenSkill?: (skillId: string) => void;
  onOpenRuntime?: () => void;
}

export function AgentEditorPanel({ expertId, onBack, onOpenSession, onCreated, onOpenSkill, onOpenRuntime }: AgentEditorPanelProps) {
  const isNew = isNewStudioId(expertId);
  const [form, setForm] = useState<StudioExpertWriteBody>(EMPTY_FORM);
  const [newId, setNewId] = useState('');
  const [source, setSource] = useState<OfficeAssetSource | null>(null);
  const [tagsInput, setTagsInput] = useState('');
  const [skillsInput, setSkillsInput] = useState('');
  const [connectorsInput, setConnectorsInput] = useState('');
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [expertYaml, setExpertYaml] = useState('');
  const [activeFileId, setActiveFileId] = useState('yaml');

  const load = useCallback(async () => {
    if (isNew) {
      setForm(EMPTY_FORM);
      setSource(null);
      setExpertYaml('');
      setTagsInput('draft');
      setSkillsInput('');
      setConnectorsInput('');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const data = await getStudioExpertSource(expertId);
      const nextForm = expertSourceToForm(data);
      setForm(nextForm);
      setExpertYaml(data.expertYaml);
      setSource(data.source);
      setTagsInput(joinCommaList(nextForm.tags));
      setSkillsInput(joinCommaList(nextForm.preloadSkills));
      setConnectorsInput(joinCommaList(nextForm.skillCompatibility));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [expertId, isNew]);

  useEffect(() => {
    void load();
  }, [load]);

  const buildPayload = (): StudioExpertWriteBody => {
    const id = isNew ? newId.trim() : expertId;
    return normalizeExpertWriteBody(
      {
        ...form,
        id,
        tags: parseCommaList(tagsInput),
        skillCompatibility: parseCommaList(connectorsInput),
        preloadSkills: parseCommaList(skillsInput),
      },
      isNew ? 'new' : expertId,
    );
  };

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const payload = buildPayload();
      const result = isNew
        ? await createStudioExpert(payload)
        : await updateStudioExpert(expertId, payload);
      setSource(result.source);
      setExpertYaml(result.expertYaml);
      setMessage('已保存草稿');
      if (isNew) {
        onCreated(result.summary.id);
      }
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const handleValidate = async () => {
    setError(null);
    setMessage(null);
    try {
      const result = await validateStudioExpert(buildPayload());
      setMessage(result.valid ? '校验通过' : result.message);
      if (!result.valid) {
        setError(result.message);
      }
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const handleFork = async () => {
    if (isNew) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const result = await forkStudioExpert(expertId);
      setSource(result.source);
      setExpertYaml(result.expertYaml);
      setForm(expertSourceToForm(result));
      setMessage('已 fork 为草稿');
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteDraft = async () => {
    if (isNew || source !== 'DRAFT') {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await deleteStudioExpert(expertId);
      setMessage('草稿已删除');
      onBack();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const handleDryRun = async () => {
    if (isNew) {
      setError('请先保存专家后再试跑');
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const payload = buildPayload();
      const saved = await updateStudioExpert(expertId, payload);
      setSource(saved.source);
      setMessage('已保存草稿，正在打开试跑会话…');
      const result = await dryRunStudioExpert(expertId);
      const initialMessage = payload.defaultInitPrompt?.trim() || undefined;
      onOpenSession(result.sessionId, initialMessage);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <p className="muted">加载专家源文件…</p>;
  }

  return (
    <div className="dev-studio-editor">
      <header className="dev-studio-editor-header">
        <div>
          <button type="button" className="btn ghost sm" onClick={onBack}>
            ← 返回列表
          </button>
          <h1>{isNew ? '新建专家' : form.name || expertId}</h1>
          {!isNew && source && (
            <p className="muted">
              来源：<span className={`dev-studio-badge dev-studio-badge-${source.toLowerCase()}`}>{sourceLabel(source)}</span>
              {' · '}
              <code>{expertId}</code>
            </p>
          )}
        </div>
        <div className="dev-studio-editor-actions">
          <button type="button" className="btn ghost sm" onClick={() => void handleValidate()}>
            校验
          </button>
          {!isNew && (
            <button type="button" className="btn ghost sm" onClick={() => void handleFork()} disabled={saving}>
              Fork
            </button>
          )}
          {!isNew && (
            <button type="button" className="btn ghost sm" onClick={() => void handleDryRun()} disabled={saving}>
              试跑
            </button>
          )}
          {source === 'DRAFT' && !isNew && (
            <button type="button" className="btn ghost sm" onClick={() => void handleDeleteDraft()} disabled={saving}>
              删除草稿
            </button>
          )}
          {!isNew && <DevStudioPublishButton assetType="expert" assetId={expertId} disabled={saving} />}
          <button type="button" className="btn primary sm" onClick={() => void handleSave()} disabled={saving}>
            {saving ? '保存中…' : '保存草稿'}
          </button>
        </div>
      </header>

      {error && <div className="dev-studio-error">{error}</div>}
      {message && !error && <p className="dev-studio-message" role="status">{message}</p>}

      {!isNew && (
        <StudioDiffPanel
          title="草稿对比"
          loadDiff={() => getStudioExpertDiff(expertId)}
          onRollback={async () => {
            await rollbackStudioExpert(expertId);
            onBack();
          }}
          onExport={() => downloadStudioExpertExport(expertId)}
        />
      )}

      <div className="dev-studio-editor-layout">
        <aside className="dev-studio-form-pane">
          {isNew && (
            <label className="dev-studio-field">
              <span>ID</span>
              <input
                value={newId}
                onChange={(event) => setNewId(event.target.value)}
                placeholder="my-custom-agent"
                spellCheck={false}
              />
            </label>
          )}
          <label className="dev-studio-field">
            <span>名称</span>
            <input
              value={form.name}
              onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))}
            />
          </label>
          <label className="dev-studio-field">
            <span>描述</span>
            <textarea
              rows={3}
              value={form.description}
              onChange={(event) => setForm((prev) => ({ ...prev, description: event.target.value }))}
            />
          </label>
          <label className="dev-studio-field">
            <span>分类</span>
            <input
              value={form.category ?? ''}
              onChange={(event) => setForm((prev) => ({ ...prev, category: event.target.value }))}
            />
          </label>
          <label className="dev-studio-field">
            <span>标签（逗号分隔）</span>
            <input value={tagsInput} onChange={(event) => setTagsInput(event.target.value)} />
          </label>
          <label className="dev-studio-field">
            <span>预载技能（preloadSkills）</span>
            <input value={skillsInput} onChange={(event) => setSkillsInput(event.target.value)} placeholder="excel-handler, web-access" />
          </label>
          <label className="dev-studio-field">
            <span>推荐连接器 / MCP（skillCompatibility）</span>
            <input value={connectorsInput} onChange={(event) => setConnectorsInput(event.target.value)} placeholder="qieman, github" />
          </label>
          <label className="dev-studio-field">
            <span>maxTurns</span>
            <input
              type="number"
              min={1}
              value={form.maxTurns ?? ''}
              onChange={(event) =>
                setForm((prev) => ({
                  ...prev,
                  maxTurns: event.target.value ? Number(event.target.value) : null,
                }))
              }
            />
          </label>
          <label className="dev-studio-field">
            <span>默认首条消息</span>
            <textarea
              rows={2}
              value={form.defaultInitPrompt ?? ''}
              onChange={(event) => setForm((prev) => ({ ...prev, defaultInitPrompt: event.target.value }))}
            />
          </label>
        </aside>
        <div className="dev-studio-skill-source-pane">
          <StudioSourceEditor
            editorKeyPrefix={expertId}
            activeFileId={activeFileId}
            onActiveFileChange={setActiveFileId}
            files={[
              {
                id: 'yaml',
                label: 'expert.yaml',
                language: 'yaml',
                value: expertYaml,
                readOnly: true,
              },
              {
                id: 'prompt',
                label: `Prompt（${form.promptFile ?? 'prompt.md'}）`,
                language: 'markdown',
                value: form.promptContent,
              },
            ]}
            onFileChange={(fileId, value) => {
              if (fileId === 'prompt') {
                setForm((prev) => ({ ...prev, promptContent: value }));
              }
            }}
          />
          {!isNew && (
            <StudioExpertCapabilitiesPanel
              expertId={expertId}
              onOpenSkill={onOpenSkill}
              onOpenRuntime={onOpenRuntime}
            />
          )}
        </div>
      </div>
    </div>
  );
}

import { useCallback, useEffect, useState } from 'react';
import {
  createStudioSkill,
  deleteStudioSkill,
  getStudioSkillDiff,
  getStudioSkillSource,
  downloadStudioSkillExport,
  rollbackStudioSkill,
  updateStudioSkill,
  validateStudioSkill,
} from '../../api/studio';
import { isNewStudioId, joinCommaList, parseCommaList, sourceLabel } from '../../lib/studioForm';
import type { OfficeAssetSource, StudioSkillWriteBody } from '../../types/studio';
import { StudioDiffPanel } from './StudioDiffPanel';
import { StudioSourceEditor } from './StudioSourceEditor';
import { StudioSkillDirPanel } from './StudioSkillDirPanel';
import { DevStudioPublishButton } from './DevStudioPublishButton';

const EMPTY_FORM: StudioSkillWriteBody = {
  id: '',
  name: '',
  description: '',
  category: 'custom',
  tags: ['draft'],
  skillContent: '# Skill title\n\nDescribe when and how to use this skill.',
};

interface SkillEditorPanelProps {
  skillId: string;
  onBack: () => void;
  onCreated: (skillId: string) => void;
}

export function SkillEditorPanel({ skillId, onBack, onCreated }: SkillEditorPanelProps) {
  const isNew = isNewStudioId(skillId);
  const [form, setForm] = useState<StudioSkillWriteBody>(EMPTY_FORM);
  const [newId, setNewId] = useState('');
  const [source, setSource] = useState<OfficeAssetSource | null>(null);
  const [skillYaml, setSkillYaml] = useState('');
  const [activeFileId, setActiveFileId] = useState('skill');
  const [tagsInput, setTagsInput] = useState('draft');
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (isNew) {
      setForm(EMPTY_FORM);
      setSource(null);
      setSkillYaml('');
      setTagsInput('draft');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const data = await getStudioSkillSource(skillId);
      setForm({
        name: data.summary.name,
        description: data.summary.description,
        category: data.summary.category,
        tags: data.summary.tags,
        skillContent: data.skillContent.replace(/\n$/, ''),
        skillFile: data.skillFile,
        source: data.summary.source,
      });
      setSkillYaml(data.skillYaml);
      setSource(data.source);
      setTagsInput(joinCommaList(data.summary.tags));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [isNew, skillId]);

  useEffect(() => {
    void load();
  }, [load]);

  const buildPayload = (): StudioSkillWriteBody => ({
    ...form,
    id: isNew ? newId.trim() : skillId,
    tags: parseCommaList(tagsInput),
    category: form.category || 'custom',
  });

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const payload = buildPayload();
      const result = isNew
        ? await createStudioSkill(payload)
        : await updateStudioSkill(skillId, payload);
      setSource(result.source);
      setSkillYaml(result.skillYaml);
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
    try {
      const result = await validateStudioSkill(buildPayload());
      setMessage(result.valid ? '校验通过' : result.message);
      if (!result.valid) {
        setError(result.message);
      }
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const handleDeleteDraft = async () => {
    if (isNew || source !== 'DRAFT') {
      return;
    }
    setSaving(true);
    try {
      await deleteStudioSkill(skillId);
      onBack();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <p className="muted">加载技能源文件…</p>;
  }

  const skillFileLabel = form.skillFile ?? 'SKILL.md';

  return (
    <div className="dev-studio-editor">
      <header className="dev-studio-editor-header">
        <div>
          <button type="button" className="btn ghost sm" onClick={onBack}>
            ← 返回列表
          </button>
          <h1>{isNew ? '新建技能' : form.name || skillId}</h1>
          {!isNew && source && (
            <p className="muted">
              来源：<span className={`dev-studio-badge dev-studio-badge-${source.toLowerCase()}`}>{sourceLabel(source)}</span>
            </p>
          )}
        </div>
        <div className="dev-studio-editor-actions">
          <button type="button" className="btn ghost sm" onClick={() => void handleValidate()}>
            校验
          </button>
          {source === 'DRAFT' && !isNew && (
            <button type="button" className="btn ghost sm" onClick={() => void handleDeleteDraft()} disabled={saving}>
              删除草稿
            </button>
          )}
          {!isNew && <DevStudioPublishButton assetType="skill" assetId={skillId} disabled={saving} />}
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
          loadDiff={() => getStudioSkillDiff(skillId)}
          onRollback={async () => {
            await rollbackStudioSkill(skillId);
            onBack();
          }}
          onExport={() => downloadStudioSkillExport(skillId)}
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
            <span>名称</span>
            <input value={form.name} onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))} />
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
            <span>标签</span>
            <input value={tagsInput} onChange={(event) => setTagsInput(event.target.value)} />
          </label>
        </aside>
        <div className="dev-studio-skill-source-pane">
          <StudioSourceEditor
            editorKeyPrefix={skillId}
            activeFileId={activeFileId}
            onActiveFileChange={setActiveFileId}
            files={[
              {
                id: 'yaml',
                label: 'skill.yaml',
                language: 'yaml',
                value: skillYaml,
                readOnly: true,
              },
              {
                id: 'skill',
                label: skillFileLabel,
                language: 'markdown',
                value: form.skillContent,
              },
            ]}
            onFileChange={(fileId, value) => {
              if (fileId === 'skill') {
                setForm((prev) => ({ ...prev, skillContent: value }));
              }
            }}
          />
          {!isNew && <StudioSkillDirPanel skillId={skillId} />}
        </div>
      </div>
    </div>
  );
}

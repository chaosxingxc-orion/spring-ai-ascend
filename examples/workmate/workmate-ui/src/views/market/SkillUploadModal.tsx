import { useState } from 'react';
import { uploadSkill, validateSkillUpload } from '../../api/import';
import { createStudioSkill, validateStudioSkill } from '../../api/studio';

interface SkillUploadModalProps {
  open: boolean;
  mode?: 'market' | 'studio';
  onUploaded: (skill: { id: string }) => void;
  onClose: () => void;
}

export function SkillUploadModal({ open, mode = 'market', onUploaded, onClose }: SkillUploadModalProps) {
  const [id, setId] = useState('my-custom-skill');
  const [name, setName] = useState('我的技能');
  const [description, setDescription] = useState('描述该技能的使用场景与约束');
  const [skillContent, setSkillContent] = useState(
    '# My Skill\n\nDescribe when and how the agent should use this skill.',
  );
  const [installAfterUpload, setInstallAfterUpload] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (!open) {
    return null;
  }

  const isStudio = mode === 'studio';

  const payload = {
    id: id.trim(),
    name: name.trim(),
    description: description.trim(),
    category: 'custom',
    tags: isStudio ? ['uploaded', 'draft'] : ['uploaded'],
    skillContent,
    install: installAfterUpload,
  };

  const handleUpload = async () => {
    setBusy(true);
    setError(null);
    try {
      if (isStudio) {
        const validation = await validateStudioSkill({
          id: payload.id,
          name: payload.name,
          description: payload.description,
          category: payload.category,
          tags: payload.tags,
          skillContent: payload.skillContent,
        });
        if (!validation.valid) {
          setError(validation.message);
          return;
        }
        const created = await createStudioSkill({
          id: payload.id,
          name: payload.name,
          description: payload.description,
          category: payload.category,
          tags: payload.tags,
          skillContent: payload.skillContent,
        });
        onUploaded({ id: created.summary.id });
        onClose();
        return;
      }
      const validation = await validateSkillUpload(payload);
      if (!validation.valid) {
        setError(validation.message);
        return;
      }
      const skill = await uploadSkill(payload);
      onUploaded(skill);
      onClose();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div
        className="modal import-modal"
        role="dialog"
        aria-labelledby="skill-upload-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="modal-header">
          <h2 id="skill-upload-title">{isStudio ? '上传技能草稿' : '上传技能包'}</h2>
          <button type="button" className="btn ghost" onClick={onClose} disabled={busy}>
            ×
          </button>
        </header>
        <div className="modal-body">
          <p className="market-hint">
            写入 <code>{isStudio ? 'office-drafts/skills' : 'office-imports/skills'}</code>
            {isStudio && ' · 上传后进入编辑器'}
          </p>
          <label className="import-field">
            <span>技能 ID</span>
            <input value={id} onChange={(event) => setId(event.target.value)} />
          </label>
          <label className="import-field">
            <span>名称</span>
            <input value={name} onChange={(event) => setName(event.target.value)} />
          </label>
          <label className="import-field">
            <span>描述</span>
            <input value={description} onChange={(event) => setDescription(event.target.value)} />
          </label>
          <label className="import-field">
            <span>SKILL.md 正文</span>
            <textarea
              className="import-textarea"
              rows={10}
              value={skillContent}
              onChange={(event) => setSkillContent(event.target.value)}
            />
          </label>
          {!isStudio && (
            <label className="import-field">
              <input
                type="checkbox"
                checked={installAfterUpload}
                onChange={(event) => setInstallAfterUpload(event.target.checked)}
              />
              <span>上传后立即安装到当前工作区</span>
            </label>
          )}
          {error && <p className="market-hint error">{error}</p>}
        </div>
        <footer className="modal-footer">
          <button type="button" className="btn secondary" disabled={busy} onClick={onClose}>
            取消
          </button>
          <button type="button" className="btn primary" disabled={busy} onClick={() => void handleUpload()}>
            {busy ? '上传中…' : isStudio ? '上传并编辑' : '上传'}
          </button>
        </footer>
      </div>
    </div>
  );
}

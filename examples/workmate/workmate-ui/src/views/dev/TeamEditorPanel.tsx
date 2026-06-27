import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  createStudioTeam,
  dryRunStudioTeam,
  getStudioExpertSource,
  getStudioTeam,
  previewStudioTeamRuntime,
  rollbackStudioTeam,
  getStudioTeamDiff,
  downloadStudioExpertExport,
  updateStudioTeam,
  validateStudioTeam,
} from '../../api/studio';
import { isNewStudioId, sourceLabel } from '../../lib/studioForm';
import {
  COORDINATION_PATTERNS,
  emptyTeamForm,
  patternHasLead,
  patternImpactHint,
  teamViewToForm,
  TEAM_RUNTIMES,
  trimPromptContent,
} from '../../lib/studioTeamForm';
import type { OfficeAssetSource, StudioRuntimePreview, StudioTeamMemberWriteBody, StudioTeamWriteBody } from '../../types/studio';
import { StudioDiffPanel } from './StudioDiffPanel';
import { StudioExpertCapabilitiesPanel } from './StudioExpertCapabilitiesPanel';
import { StudioSourceEditor } from './StudioSourceEditor';
import { TeamOrchestrationCanvas } from './TeamOrchestrationCanvas';

interface TeamEditorPanelProps {
  teamId: string;
  onBack: () => void;
  onOpenSession: (sessionId: string, initialMessage?: string) => void;
  onCreated: (teamId: string) => void;
  onOpenSkill?: (skillId: string) => void;
  onOpenRuntime?: () => void;
}

export function TeamEditorPanel({ teamId, onBack, onOpenSession, onCreated, onOpenSkill, onOpenRuntime }: TeamEditorPanelProps) {
  const isNew = isNewStudioId(teamId);
  const [form, setForm] = useState<StudioTeamWriteBody>(() => emptyTeamForm('new-team'));
  const [newId, setNewId] = useState('');
  const [source, setSource] = useState<OfficeAssetSource | null>(null);
  const [warnings, setWarnings] = useState<string[]>([]);
  const [runtimePreview, setRuntimePreview] = useState<StudioRuntimePreview | null>(null);
  const [selectedMemberId, setSelectedMemberId] = useState<string | null>(null);
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [teamExpertYaml, setTeamExpertYaml] = useState('');
  const [activeTeamFileId, setActiveTeamFileId] = useState('yaml');
  const [activeMemberFileId, setActiveMemberFileId] = useState('yaml');

  const pattern = form.coordination?.pattern ?? 'orchestrator';
  const runtime = form.teamRuntime ?? 'openjiuwen-team';
  const hasLead = patternHasLead(pattern);
  const selectedMember = useMemo(
    () => form.members.find((member) => member.id === selectedMemberId) ?? null,
    [form.members, selectedMemberId],
  );

  const hydrateMemberSources = async (members: StudioTeamMemberWriteBody[]) =>
    Promise.all(
      members.map(async (member) => {
        const hasPrompt = Boolean(member.promptContent?.trim());
        const hasYaml = Boolean(member.expertYaml?.trim());
        if ((hasPrompt && hasYaml) || !member.expertId) {
          return member;
        }
        try {
          const source = await getStudioExpertSource(member.expertId);
          return {
            ...member,
            promptContent: hasPrompt ? member.promptContent : trimPromptContent(source.promptContent),
            expertYaml: hasYaml ? member.expertYaml : source.expertYaml,
          };
        } catch {
          return member;
        }
      }),
    );

  const load = useCallback(async () => {
    if (isNew) {
      setForm(emptyTeamForm('new-team'));
      setSource(null);
      setWarnings([]);
      setRuntimePreview(null);
      setTeamExpertYaml('');
      setSelectedMemberId('member-a');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const view = await getStudioTeam(teamId);
      const nextForm = teamViewToForm(view);
      const members = await hydrateMemberSources(nextForm.members);
      setForm({ ...nextForm, members });
      setTeamExpertYaml(view.team.expertYaml);
      setSource(view.team.source);
      setWarnings(view.warnings);
      setRuntimePreview(view.runtimePreview);
      setSelectedMemberId(view.members[0]?.member.id ?? null);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [teamId, isNew]);

  useEffect(() => {
    void load();
  }, [load]);

  const buildPayload = (): StudioTeamWriteBody => {
    const id = isNew ? newId.trim() : teamId;
    return {
      ...form,
      id,
      coordination: {
        pattern,
        termination: form.coordination?.termination,
        acceptanceCriteria: form.coordination?.acceptanceCriteria,
      },
      members: form.members.map((member, index) => ({
        ...member,
        order: member.order ?? index + 1,
        expertId: member.expertId || `${id}__${member.id}`,
      })),
    };
  };

  const refreshRuntimePreview = async (payload: StudioTeamWriteBody) => {
    try {
      const preview = isNew
        ? await previewStudioTeamRuntime(payload.id || 'preview', payload)
        : await previewStudioTeamRuntime(teamId, payload);
      setRuntimePreview(preview);
    } catch {
      setRuntimePreview(null);
    }
  };

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const payload = buildPayload();
      const view = isNew ? await createStudioTeam(payload) : await updateStudioTeam(teamId, payload);
      setSource(view.team.source);
      setWarnings(view.warnings);
      setRuntimePreview(view.runtimePreview);
      const savedForm = teamViewToForm(view);
      setForm({ ...savedForm, members: await hydrateMemberSources(savedForm.members) });
      setTeamExpertYaml(view.team.expertYaml);
      setMessage('已保存草稿');
      if (isNew) {
        onCreated(view.team.summary.id);
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
      const result = await validateStudioTeam(buildPayload());
      setMessage(result.valid ? '校验通过' : result.message);
      if (!result.valid) {
        setError(result.message);
      }
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const handlePreviewRuntime = async () => {
    setError(null);
    try {
      await refreshRuntimePreview(buildPayload());
      setMessage('已刷新 runtime 预览');
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const handleDryRun = async () => {
    if (isNew) {
      setError('请先保存专家团后再试跑');
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const payload = buildPayload();
      const view = await updateStudioTeam(teamId, payload);
      setSource(view.team.source);
      setWarnings(view.warnings);
      setRuntimePreview(view.runtimePreview);
      const savedForm = teamViewToForm(view);
      setForm({ ...savedForm, members: await hydrateMemberSources(savedForm.members) });
      setTeamExpertYaml(view.team.expertYaml);
      setMessage('已保存草稿，正在打开试跑会话…');
      const result = await dryRunStudioTeam(teamId);
      const initialMessage = payload.defaultInitPrompt?.trim() || undefined;
      onOpenSession(result.sessionId, initialMessage);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const updateMember = (memberId: string, patch: Partial<StudioTeamMemberWriteBody>) => {
    setForm((prev) => ({
      ...prev,
      members: prev.members.map((member) => (member.id === memberId ? { ...member, ...patch } : member)),
    }));
  };

  useEffect(() => {
    setActiveMemberFileId('yaml');
  }, [selectedMemberId]);

  useEffect(() => {
    if (!selectedMember?.expertId) {
      return;
    }
    const needsPrompt = !selectedMember.promptContent?.trim();
    const needsYaml = !selectedMember.expertYaml?.trim();
    if (!needsPrompt && !needsYaml) {
      return;
    }
    let cancelled = false;
    void getStudioExpertSource(selectedMember.expertId)
      .then((source) => {
        if (cancelled) {
          return;
        }
        updateMember(selectedMember.id, {
          promptContent: needsPrompt ? trimPromptContent(source.promptContent) : selectedMember.promptContent,
          expertYaml: needsYaml ? source.expertYaml : selectedMember.expertYaml,
        });
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [
    selectedMember?.expertId,
    selectedMember?.id,
    selectedMember?.promptContent,
    selectedMember?.expertYaml,
  ]);

  const addMember = () => {
    const index = form.members.length + 1;
    const id = `member-${index}`;
    setForm((prev) => ({
      ...prev,
      members: [
        ...prev.members,
        {
          id,
          name: `成员 ${index}`,
          expertId: `${isNew ? newId.trim() || 'new-team' : teamId}__${id}`,
          role: `成员 ${index}`,
          order: index,
          backend: 'local',
          promptContent: `你是成员 ${index}。`,
        },
      ],
    }));
    setSelectedMemberId(id);
  };

  const removeMember = (memberId: string) => {
    if (form.members.length <= 2) {
      setError('专家团至少需要 2 名成员');
      return;
    }
    setForm((prev) => ({
      ...prev,
      members: prev.members.filter((member) => member.id !== memberId),
    }));
    if (selectedMemberId === memberId) {
      setSelectedMemberId(form.members.find((m) => m.id !== memberId)?.id ?? null);
    }
  };

  if (loading) {
    return <p className="muted">加载专家团…</p>;
  }

  return (
    <div className="dev-studio-editor dev-studio-team-editor">
      <header className="dev-studio-editor-header">
        <div>
          <button type="button" className="btn ghost sm" onClick={onBack}>
            ← 返回列表
          </button>
          <h1>{isNew ? '新建专家团' : form.name || teamId}</h1>
          {!isNew && source && (
            <p className="muted">
              来源：<span className={`dev-studio-badge dev-studio-badge-${source.toLowerCase()}`}>{sourceLabel(source)}</span>
              {' · '}
              <code>{teamId}</code>
            </p>
          )}
        </div>
        <div className="dev-studio-editor-actions">
          <button type="button" className="btn ghost sm" disabled={saving} onClick={() => void handleValidate()}>
            校验
          </button>
          <button type="button" className="btn ghost sm" disabled={saving} onClick={() => void handlePreviewRuntime()}>
            Runtime 预览
          </button>
          <button type="button" className="btn ghost sm" disabled={saving || isNew} onClick={() => void handleDryRun()}>
            试跑
          </button>
          <button type="button" className="btn primary sm" disabled={saving} onClick={() => void handleSave()}>
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
      {warnings.length > 0 && (
        <ul className="dev-studio-warnings">
          {warnings.map((warning) => (
            <li key={warning}>{warning}</li>
          ))}
        </ul>
      )}

      {!isNew && (
        <StudioDiffPanel
          title="团队草稿对比"
          loadDiff={() => getStudioTeamDiff(teamId)}
          onRollback={async () => {
            await rollbackStudioTeam(teamId);
            onBack();
          }}
          onExport={() => downloadStudioExpertExport(teamId)}
        />
      )}

      <div className="dev-studio-team-layout">
        <aside className="dev-studio-form-pane">
          {isNew && (
            <label className="dev-studio-field">
              团队 ID
              <input value={newId} onChange={(event) => setNewId(event.target.value)} placeholder="my-team-id" />
            </label>
          )}
          <label className="dev-studio-field">
            名称
            <input value={form.name} onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))} />
          </label>
          <label className="dev-studio-field">
            描述
            <textarea
              rows={2}
              value={form.description}
              onChange={(event) => setForm((prev) => ({ ...prev, description: event.target.value }))}
            />
          </label>
          <label className="dev-studio-field">
            拓扑 pattern
            <select
              value={pattern}
              onChange={(event) =>
                setForm((prev) => ({
                  ...prev,
                  coordination: { ...prev.coordination, pattern: event.target.value },
                }))
              }
            >
              {COORDINATION_PATTERNS.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </select>
          </label>
          <label className="dev-studio-field">
            Runtime
            <select
              value={runtime}
              onChange={(event) => setForm((prev) => ({ ...prev, teamRuntime: event.target.value }))}
            >
              {TEAM_RUNTIMES.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </select>
          </label>
          <p className="muted dev-studio-hint">{patternImpactHint(pattern, runtime)}</p>
          {runtimePreview && (
            <div className="dev-studio-runtime-preview">
              <strong>选路预览</strong>
              <p>{runtimePreview.hint}</p>
              <p className="muted">
                resolved: {runtimePreview.resolvedRuntime} · hasLead: {runtimePreview.hasLead ? '是' : '否'}
              </p>
            </div>
          )}
          {hasLead && (
            <>
              <h3 className="dev-studio-section-title">主理人</h3>
              <label className="dev-studio-field">
                姓名
                <input
                  value={form.lead?.name ?? ''}
                  onChange={(event) =>
                    setForm((prev) => ({
                      ...prev,
                      lead: { ...prev.lead, name: event.target.value, title: prev.lead?.title },
                    }))
                  }
                />
              </label>
              <label className="dev-studio-field">
                头衔 (zh)
                <input
                  value={form.lead?.title?.zh ?? ''}
                  onChange={(event) =>
                    setForm((prev) => ({
                      ...prev,
                      lead: {
                        name: prev.lead?.name ?? '',
                        title: { ...prev.lead?.title, zh: event.target.value },
                        avatar: prev.lead?.avatar,
                      },
                    }))
                  }
                />
              </label>
            </>
          )}
          <h3 className="dev-studio-section-title">成员</h3>
          <div className="dev-studio-member-list">
            {form.members.map((member) => (
              <button
                key={member.id}
                type="button"
                className={`dev-studio-member-chip${selectedMemberId === member.id ? ' active' : ''}`}
                onClick={() => setSelectedMemberId(member.id)}
              >
                {member.name}
              </button>
            ))}
            <button type="button" className="btn ghost sm" onClick={addMember}>
              + 成员
            </button>
          </div>
          {selectedMember && (
            <div className="dev-studio-member-editor">
              <label className="dev-studio-field">
                成员 ID
                <input value={selectedMember.id} readOnly />
              </label>
              <label className="dev-studio-field">
                名称
                <input
                  value={selectedMember.name}
                  onChange={(event) => updateMember(selectedMember.id, { name: event.target.value })}
                />
              </label>
              <label className="dev-studio-field">
                角色
                <input
                  value={selectedMember.role ?? ''}
                  onChange={(event) => updateMember(selectedMember.id, { role: event.target.value })}
                />
              </label>
              <label className="dev-studio-field">
                expertId
                <input
                  value={selectedMember.expertId ?? ''}
                  onChange={(event) => updateMember(selectedMember.id, { expertId: event.target.value })}
                />
              </label>
              <label className="dev-studio-field">
                order
                <input
                  type="number"
                  value={selectedMember.order ?? 1}
                  onChange={(event) =>
                    updateMember(selectedMember.id, { order: Number.parseInt(event.target.value, 10) || 1 })
                  }
                />
              </label>
              <button type="button" className="btn ghost sm" onClick={() => removeMember(selectedMember.id)}>
                删除成员
              </button>
            </div>
          )}
        </aside>

        <div className="dev-studio-team-canvas-pane">
          <TeamOrchestrationCanvas
            pattern={pattern}
            teamName={form.name}
            leadName={form.lead?.name}
            members={form.members}
            hasLead={hasLead}
            selectedMemberId={selectedMemberId}
            onSelectMember={setSelectedMemberId}
          />
          <div className="dev-studio-team-prompt-stack">
            <StudioSourceEditor
              editorKeyPrefix={`${teamId}-team`}
              activeFileId={activeTeamFileId}
              onActiveFileChange={setActiveTeamFileId}
              files={[
                {
                  id: 'yaml',
                  label: 'expert.yaml',
                  language: 'yaml',
                  value: teamExpertYaml,
                  readOnly: true,
                },
                {
                  id: 'prompt',
                  label: hasLead ? 'lead-prompt.md' : 'prompt.md',
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
                expertId={teamId}
                onOpenSkill={onOpenSkill}
                onOpenRuntime={onOpenRuntime}
              />
            )}
            {selectedMember && (
              <StudioSourceEditor
                editorKeyPrefix={`${teamId}-${selectedMember.id}`}
                activeFileId={activeMemberFileId}
                onActiveFileChange={setActiveMemberFileId}
                files={[
                  {
                    id: 'yaml',
                    label: `${selectedMember.id}/expert.yaml`,
                    language: 'yaml',
                    value: selectedMember.expertYaml ?? '',
                    readOnly: true,
                  },
                  {
                    id: 'prompt',
                    label: `${selectedMember.id}/prompt.md`,
                    language: 'markdown',
                    value: selectedMember.promptContent ?? '',
                  },
                ]}
                onFileChange={(fileId, value) => {
                  if (fileId === 'prompt') {
                    updateMember(selectedMember.id, { promptContent: value });
                  }
                }}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

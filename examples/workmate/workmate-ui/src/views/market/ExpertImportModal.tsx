import { useState } from 'react';
import type { ExpertImportPayload } from '../../api/import';
import { importExpert, importExpertZip, validateExpertImport } from '../../api/import';
import { createStudioExpert, importStudioExpertZip, validateStudioExpert } from '../../api/studio';
import { expertImportPayloadToStudioBody } from '../../lib/studioImport';
import type { StudioImportResult } from '../../lib/studioImport';

interface ExpertImportModalProps {
  open: boolean;
  busy?: boolean;
  /** market: 写入 office-imports；studio: 写入 office-drafts 并进入编辑器 */
  mode?: 'market' | 'studio';
  onImported: (result: StudioImportResult) => void;
  onClose: () => void;
}

const EXPERT_TEMPLATE = `id: my-custom-expert
name: 我的专家
description: 一句话描述专家职责
category: custom
tags: [imported]`;

export function ExpertImportModal({
  open,
  busy = false,
  mode = 'market',
  onImported,
  onClose,
}: ExpertImportModalProps) {
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [yamlDraft, setYamlDraft] = useState(EXPERT_TEMPLATE);
  const [promptDraft, setPromptDraft] = useState('你是 WorkMate 自定义专家。请根据用户任务给出结构化、可执行的建议。');
  const [defaultInitPrompt, setDefaultInitPrompt] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [localBusy, setLocalBusy] = useState(false);

  if (!open) {
    return null;
  }

  const working = busy || localBusy;
  const isStudio = mode === 'studio';
  const storageHint = isStudio ? 'office-drafts/experts' : 'office-imports/experts';

  const parseYamlMeta = () => {
    const lines = yamlDraft.split('\n');
    const readField = (key: string) => {
      const prefix = `${key}:`;
      const line = lines.find((item) => item.trim().startsWith(prefix));
      if (!line) {
        return '';
      }
      return line.slice(line.indexOf(':') + 1).trim();
    };
    return {
      id: readField('id'),
      name: readField('name'),
      description: readField('description'),
      category: readField('category') || 'custom',
      expertType: readField('expertType') || 'agent',
    };
  };

  const buildPayload = (): ExpertImportPayload => {
    const meta = parseYamlMeta();
    return {
      ...meta,
      expertType: 'agent',
      tags: ['imported'],
      promptContent: promptDraft,
      defaultInitPrompt: defaultInitPrompt.trim() || undefined,
    };
  };

  const emitImported = (id: string, expertType = 'agent') => {
    onImported({ id, expertType });
    onClose();
    setStep(1);
    setYamlDraft(EXPERT_TEMPLATE);
    setPromptDraft('你是 WorkMate 自定义专家。请根据用户任务给出结构化、可执行的建议。');
    setDefaultInitPrompt('');
    setError(null);
  };

  const handleValidate = async () => {
    setLocalBusy(true);
    setError(null);
    try {
      const payload = buildPayload();
      const result = isStudio
        ? await validateStudioExpert(expertImportPayloadToStudioBody(payload))
        : await validateExpertImport(payload);
      if (!result.valid) {
        setError(result.message);
        return;
      }
      setStep(3);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLocalBusy(false);
    }
  };

  const handleImport = async () => {
    setLocalBusy(true);
    setError(null);
    try {
      const payload = buildPayload();
      if (isStudio) {
        const created = await createStudioExpert(expertImportPayloadToStudioBody(payload));
        emitImported(created.summary.id, created.summary.expertType ?? 'agent');
        return;
      }
      const expert = await importExpert(payload);
      emitImported(expert.id, expert.expertType ?? 'agent');
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLocalBusy(false);
    }
  };

  const handleZipUpload = async (file: File) => {
    setLocalBusy(true);
    setError(null);
    try {
      if (isStudio) {
        const created = await importStudioExpertZip(file);
        emitImported(created.summary.id, created.summary.expertType ?? 'agent');
        return;
      }
      const expert = await importExpertZip(file);
      emitImported(expert.id, expert.expertType ?? 'agent');
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLocalBusy(false);
    }
  };

  const meta = parseYamlMeta();

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div
        className="modal import-modal"
        role="dialog"
        aria-labelledby="expert-import-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="modal-header">
          <h2 id="expert-import-title">{isStudio ? '导入专家草稿' : '导入自定义专家'}</h2>
          <button type="button" className="btn ghost" onClick={onClose} disabled={working}>
            ×
          </button>
        </header>
        <div className="modal-body">
          <p className="market-hint">
            步骤 {step}/3 · 写入 <code>{storageHint}</code>
            {isStudio && ' · 导入后进入编辑器'}
          </p>

          {step === 1 && (
            <>
              <label className="import-field">
                <span>从 ZIP 导入（含 expert.yaml + prompt.md）</span>
                <input
                  type="file"
                  accept=".zip,application/zip"
                  disabled={working}
                  onChange={(event) => {
                    const file = event.target.files?.[0];
                    if (file) {
                      void handleZipUpload(file);
                    }
                    event.target.value = '';
                  }}
                />
              </label>
              <p className="market-hint">或手动填写 YAML / Prompt 向导：</p>
              <label className="import-field">
                <span>专家元数据（YAML）</span>
                <textarea
                  className="import-textarea"
                  rows={8}
                  value={yamlDraft}
                  onChange={(event) => setYamlDraft(event.target.value)}
                />
              </label>
            </>
          )}

          {step === 2 && (
            <>
              <label className="import-field">
                <span>系统 Prompt（prompt.md）</span>
                <textarea
                  className="import-textarea"
                  rows={10}
                  value={promptDraft}
                  onChange={(event) => setPromptDraft(event.target.value)}
                />
              </label>
              <label className="import-field">
                <span>默认首条消息（可选）</span>
                <textarea
                  className="import-textarea"
                  rows={3}
                  value={defaultInitPrompt}
                  onChange={(event) => setDefaultInitPrompt(event.target.value)}
                />
              </label>
            </>
          )}

          {step === 3 && (
            <dl className="import-preview">
              <div><dt>ID</dt><dd>{meta.id}</dd></div>
              <div><dt>名称</dt><dd>{meta.name}</dd></div>
              <div><dt>描述</dt><dd>{meta.description}</dd></div>
              <div><dt>类型</dt><dd>agent</dd></div>
            </dl>
          )}

          {error && <p className="market-hint error">{error}</p>}
        </div>
        <footer className="modal-footer">
          <button type="button" className="btn secondary" disabled={working} onClick={onClose}>
            取消
          </button>
          {step > 1 && (
            <button
              type="button"
              className="btn secondary"
              disabled={working}
              onClick={() => setStep((current) => (current === 3 ? 2 : 1))}
            >
              上一步
            </button>
          )}
          {step === 1 && (
            <button type="button" className="btn primary" disabled={working} onClick={() => setStep(2)}>
              下一步
            </button>
          )}
          {step === 2 && (
            <button type="button" className="btn primary" disabled={working} onClick={() => void handleValidate()}>
              校验并预览
            </button>
          )}
          {step === 3 && (
            <button type="button" className="btn primary" disabled={working} onClick={() => void handleImport()}>
              {working ? '导入中…' : isStudio ? '导入并编辑' : '确认导入'}
            </button>
          )}
        </footer>
      </div>
    </div>
  );
}

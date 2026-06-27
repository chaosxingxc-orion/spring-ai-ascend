import { useEffect, useRef, useState } from 'react';
import type { ModelCatalog, ModelEffort } from '../../types/api';

interface ModelPopoverProps {
  catalog: ModelCatalog | null;
  modelId: string;
  effort: ModelEffort;
  disabled?: boolean;
  /** Session locked — show static label only when catalog missing. */
  readOnly?: boolean;
  /** Open popover for inspection without changing model (S02). */
  inspectOnly?: boolean;
  onModelChange: (modelId: string) => void;
  onEffortChange: (effort: ModelEffort) => void;
}

function modelLabel(catalog: ModelCatalog | null, modelId: string): string {
  if (!catalog) {
    return modelId || 'Auto';
  }
  const match = catalog.models.find((model) => model.id === modelId);
  if (match) {
    return match.displayName;
  }
  const fallback = catalog.models.find((model) => model.id === catalog.defaultModelId);
  return fallback?.displayName ?? 'Auto';
}

function effortLabel(catalog: ModelCatalog | null, effort: ModelEffort): string {
  const match = catalog?.efforts.find((item) => item.id === effort);
  return match?.label ?? '自动';
}

function ModelPopoverPanel({
  catalog,
  activeModelId,
  activeEffort,
  inspectOnly,
  onModelChange,
  onEffortChange,
}: {
  catalog: ModelCatalog;
  activeModelId: string;
  activeEffort: ModelEffort;
  inspectOnly: boolean;
  onModelChange: (modelId: string) => void;
  onEffortChange: (effort: ModelEffort) => void;
}) {
  return (
    <div className="dock-popover dock-popover-wide model-popover" role="menu">
      <p className="dock-popover-section-title">模型</p>
      {catalog.models.map((model) => (
        <button
          key={model.id}
          type="button"
          className={`dock-popover-item${model.id === activeModelId ? ' active' : ''}${
            inspectOnly ? ' model-popover-item-readonly' : ''
          }`}
          disabled={inspectOnly}
          onClick={() => {
            if (!inspectOnly) {
              onModelChange(model.id);
            }
          }}
        >
          <span className="dock-popover-item-label">{model.displayName}</span>
          <span className="dock-popover-item-hint">
            {model.modelName}
            {model.capabilities.includes('reasoning') ? ' · 推理' : ''}
          </span>
        </button>
      ))}
      <p className="dock-popover-section-title">推理强度</p>
      {catalog.efforts.map((item) => (
        <button
          key={item.id}
          type="button"
          className={`dock-popover-item${item.id === activeEffort ? ' active' : ''}${
            inspectOnly ? ' model-popover-item-readonly' : ''
          }`}
          disabled={inspectOnly}
          onClick={() => {
            if (!inspectOnly) {
              onEffortChange(item.id as ModelEffort);
            }
          }}
        >
          <span className="dock-popover-item-label">{item.label}</span>
        </button>
      ))}
      {inspectOnly && (
        <p className="model-popover-readonly-hint">
          当前会话模型已固定。服务端默认：<code>{catalog.defaultModelId}</code>
        </p>
      )}
    </div>
  );
}

export function ModelPopover({
  catalog,
  modelId,
  effort,
  disabled,
  readOnly,
  inspectOnly = false,
  onModelChange,
  onEffortChange,
}: ModelPopoverProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const activeModelId = modelId || catalog?.defaultModelId || '';
  const activeEffort = effort || 'AUTO';
  const canInspect = Boolean(catalog) && (inspectOnly || readOnly);

  useEffect(() => {
    if (!open) {
      return;
    }
    const onDocClick = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [open]);

  if (readOnly && !canInspect) {
    return (
      <span className="dock-pill dock-pill-static" title="会话模型（创建时固定）">
        <span className="dock-pill-icon" aria-hidden>🤖</span>
        {modelLabel(catalog, activeModelId)}
      </span>
    );
  }

  if (inspectOnly || readOnly) {
    return (
      <div className="dock-popover-anchor" ref={rootRef}>
        <button
          type="button"
          className={`dock-pill${open ? ' open' : ''}`}
          disabled={disabled || !catalog}
          aria-expanded={open}
          title="查看当前模型配置"
          onClick={() => setOpen((prev) => !prev)}
        >
          <span className="dock-pill-icon" aria-hidden>🤖</span>
          {modelLabel(catalog, activeModelId)} ▾
        </button>
        {open && catalog && (
          <ModelPopoverPanel
            catalog={catalog}
            activeModelId={activeModelId}
            activeEffort={activeEffort}
            inspectOnly
            onModelChange={onModelChange}
            onEffortChange={onEffortChange}
          />
        )}
      </div>
    );
  }

  return (
    <div className="dock-popover-anchor" ref={rootRef}>
      <button
        type="button"
        className={`dock-pill${open ? ' open' : ''}`}
        disabled={disabled || !catalog}
        aria-expanded={open}
        title="选择模型与推理强度"
        onClick={() => setOpen((prev) => !prev)}
      >
        <span className="dock-pill-icon" aria-hidden>🤖</span>
        {modelLabel(catalog, activeModelId)} · {effortLabel(catalog, activeEffort)} ▾
      </button>
      {open && catalog && (
        <ModelPopoverPanel
          catalog={catalog}
          activeModelId={activeModelId}
          activeEffort={activeEffort}
          inspectOnly={false}
          onModelChange={onModelChange}
          onEffortChange={onEffortChange}
        />
      )}
    </div>
  );
}

interface FilePathPromptModalProps {
  open: boolean;
  title: string;
  label: string;
  initialValue: string;
  placeholder?: string;
  confirmLabel?: string;
  busy?: boolean;
  onConfirm: (value: string) => void;
  onCancel: () => void;
}

/** W35 — inline modal replacing window.prompt for file rename/move. */
export function FilePathPromptModal({
  open,
  title,
  label,
  initialValue,
  placeholder,
  confirmLabel = '确定',
  busy = false,
  onConfirm,
  onCancel,
}: FilePathPromptModalProps) {
  if (!open) {
    return null;
  }

  return (
    <div className="modal-backdrop" role="presentation" onClick={onCancel}>
      <div
        className="modal file-path-prompt-modal"
        role="dialog"
        aria-labelledby="file-path-prompt-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="modal-header">
          <h3 id="file-path-prompt-title">{title}</h3>
          <button type="button" className="btn ghost" onClick={onCancel} aria-label="关闭">
            ×
          </button>
        </header>
        <form
          className="modal-body"
          onSubmit={(event) => {
            event.preventDefault();
            const form = event.currentTarget;
            const input = form.elements.namedItem('pathValue') as HTMLInputElement;
            const value = input.value.trim();
            if (value) {
              onConfirm(value);
            }
          }}
        >
          <label className="file-path-prompt-field">
            <span className="file-path-prompt-label">{label}</span>
            <input
              name="pathValue"
              type="text"
              className="file-path-prompt-input"
              defaultValue={initialValue}
              placeholder={placeholder}
              autoFocus
              disabled={busy}
            />
          </label>
          <footer className="modal-footer">
            <button type="button" className="btn ghost" disabled={busy} onClick={onCancel}>
              取消
            </button>
            <button type="submit" className="btn primary" disabled={busy}>
              {busy ? '处理中…' : confirmLabel}
            </button>
          </footer>
        </form>
      </div>
    </div>
  );
}

interface ConnectorSwitchProps {
  checked: boolean;
  disabled?: boolean;
  busy?: boolean;
  compact?: boolean;
  label?: string;
  onChange: (next: boolean) => void;
}

/** S08 — connector card header toggle (connect / disconnect). */
export function ConnectorSwitch({
  checked,
  disabled = false,
  busy = false,
  compact = false,
  label,
  onChange,
}: ConnectorSwitchProps) {
  const stateLabel = label ?? (checked ? '已连接' : '未连接');

  return (
    <label
      className={`connector-switch${checked ? ' on' : ''}${disabled || busy ? ' disabled' : ''}${compact ? ' compact' : ''}`}
      title={stateLabel}
    >
      <input
        type="checkbox"
        role="switch"
        aria-checked={checked}
        aria-label={stateLabel}
        checked={checked}
        disabled={disabled || busy}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span className="connector-switch-track" aria-hidden />
      <span className="connector-switch-label">{stateLabel}</span>
    </label>
  );
}

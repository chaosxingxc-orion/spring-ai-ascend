import { useMemo, useState } from 'react';

interface AskUserQuestionCardProps {
  question: string;
  options: string[];
  allowFreeText: boolean;
  multiSelect: boolean;
  status: 'pending' | 'answered' | 'skipped' | 'cancelled';
  selections?: string[];
  answerText?: string;
  busy?: boolean;
  onSubmit?: (selections: string[], text?: string) => void;
  onSkip?: () => void;
}

function statusLabel(status: AskUserQuestionCardProps['status']): string {
  switch (status) {
    case 'answered':
      return '已确认';
    case 'skipped':
      return '已跳过';
    case 'cancelled':
      return '已超时';
    default:
      return '待确认';
  }
}

export function AskUserQuestionCard({
  question,
  options,
  allowFreeText,
  multiSelect,
  status,
  selections = [],
  answerText,
  busy = false,
  onSubmit,
  onSkip,
}: AskUserQuestionCardProps) {
  const [selected, setSelected] = useState<string[]>([]);
  const [text, setText] = useState('');
  const resolved = status !== 'pending';

  const toggleOption = (option: string) => {
    if (multiSelect) {
      setSelected((current) =>
        current.includes(option) ? current.filter((item) => item !== option) : [...current, option],
      );
      return;
    }
    setSelected([option]);
  };

  const canSubmit = useMemo(() => {
    if (resolved) {
      return false;
    }
    const hasSelection = selected.length > 0;
    const hasText = text.trim().length > 0;
    if (hasSelection || (allowFreeText && hasText)) {
      return true;
    }
    return options.length === 0 && allowFreeText && hasText;
  }, [allowFreeText, options.length, resolved, selected.length, text]);

  const resolvedSelections = resolved ? selections : selected;
  const showResolvedOptions = resolved && (options.length > 0 || resolvedSelections.length > 0);
  const showPendingOptions = !resolved && options.length > 0;
  const pickedOptions = options.filter((option) => resolvedSelections.includes(option));
  const extraSelections = resolvedSelections.filter((option) => !options.includes(option));

  return (
    <article className={`ask-question-card status-${status}`} aria-live={resolved ? 'off' : 'polite'}>
      <header className="ask-question-header">
        <span className="ask-question-icon" aria-hidden>
          {resolved ? (status === 'answered' ? '✓' : status === 'cancelled' ? '⏱' : '—') : '?'}
        </span>
        <div className="ask-question-head-text">
          <h4 className="ask-question-title">
            {resolved ? statusLabel(status) : '需要你的确认'}
          </h4>
        </div>
        {resolved && (
          <span className={`ask-question-status status-${status}`}>{statusLabel(status)}</span>
        )}
      </header>
      <p className="ask-question-prompt">{question}</p>
      {showPendingOptions && (
        <div className="ask-question-options" role={multiSelect ? 'group' : 'radiogroup'}>
          {options.map((option) => {
            const active = selected.includes(option);
            return (
              <button
                key={option}
                type="button"
                className={`ask-question-option${active ? ' active' : ''}`}
                aria-pressed={active}
                disabled={busy}
                onClick={() => toggleOption(option)}
              >
                {option}
              </button>
            );
          })}
        </div>
      )}
      {showResolvedOptions && (
        <div className="ask-question-options ask-question-options-resolved" aria-label="你的选择">
          {pickedOptions.map((option) => (
            <span
              key={option}
              className="ask-question-option ask-question-option-readonly active"
            >
              {option}
            </span>
          ))}
          {extraSelections.map((option) => (
            <span
              key={option}
              className="ask-question-option ask-question-option-readonly active"
            >
              {option}
            </span>
          ))}
        </div>
      )}
      {!resolved && allowFreeText && (
        <textarea
          className="ask-question-textarea"
          placeholder="补充说明（可选）"
          value={text}
          disabled={busy}
          rows={3}
          onChange={(event) => setText(event.target.value)}
        />
      )}
      {resolved && answerText && (
        <p className="ask-question-answer-text">{answerText}</p>
      )}
      {resolved && status === 'answered' && resolvedSelections.length === 0 && !answerText && (
        <p className="ask-question-result muted">已回答</p>
      )}
      {resolved && status === 'cancelled' && (
        <p className="ask-question-result muted">等待超时，已自动继续</p>
      )}
      {!resolved && (
        <footer className="ask-question-actions">
          {onSkip && (
            <button type="button" className="btn ghost" disabled={busy} onClick={onSkip}>
              跳过
            </button>
          )}
          {onSubmit && (
            <button
              type="button"
              className="btn primary"
              disabled={busy || !canSubmit}
              onClick={() => onSubmit(selected, text.trim() || undefined)}
            >
              {busy ? '提交中…' : '提交'}
            </button>
          )}
        </footer>
      )}
    </article>
  );
}

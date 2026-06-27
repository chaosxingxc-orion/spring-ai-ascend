import { useMemo, useState } from 'react';
import type { Session } from '../types/api';
import { formatDateTime } from '../lib/formatLocale';
import { pickArchiveCandidates, sessionsOverLimit } from '../lib/sessionLimits';
import { t } from '../lib/i18n';

interface MaxSessionsDialogProps {
  activeCount: number;
  maxActive: number;
  archivableCount: number;
  autoArchiveEnabled: boolean;
  sessions: Session[];
  onArchiveSession: (sessionId: string) => Promise<void>;
  onAutoArchiveBulk: (count: number) => Promise<void>;
  onDismiss: () => void;
}

/** F6 — dialog when active (non-archived) session limit is reached. */
export function MaxSessionsDialog({
  activeCount,
  maxActive,
  archivableCount,
  autoArchiveEnabled,
  sessions,
  onArchiveSession,
  onAutoArchiveBulk,
  onDismiss,
}: MaxSessionsDialogProps) {
  const [archivingId, setArchivingId] = useState<string | null>(null);
  const [bulkArchiving, setBulkArchiving] = useState(false);
  const over = sessionsOverLimit(activeCount, maxActive);
  const bulkCount = Math.min(over, archivableCount);
  const candidates = useMemo(
    () => pickArchiveCandidates(sessions, Math.min(5, Math.max(over, 1))),
    [over, sessions],
  );

  const handleArchive = async (sessionId: string) => {
    setArchivingId(sessionId);
    try {
      await onArchiveSession(sessionId);
    } finally {
      setArchivingId(null);
    }
  };

  const handleBulkArchive = async () => {
    if (bulkCount <= 0) {
      return;
    }
    setBulkArchiving(true);
    try {
      await onAutoArchiveBulk(bulkCount);
    } finally {
      setBulkArchiving(false);
    }
  };

  return (
    <div className="modal-backdrop" role="presentation" onClick={onDismiss}>
      <div
        className="modal max-sessions-modal"
        role="dialog"
        aria-labelledby="max-sessions-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="modal-header">
          <h3 id="max-sessions-title">{t('session.maxSessionsTitle')}</h3>
          <button type="button" className="btn ghost" onClick={onDismiss} aria-label={t('session.maxSessionsDismiss')}>
            ×
          </button>
        </header>
        <div className="modal-body max-sessions-body">
          <p>{t('session.maxSessionsBody', { active: activeCount, max: maxActive })}</p>
          {autoArchiveEnabled && (
            <p className="max-sessions-policy">{t('session.autoArchivePolicy')}</p>
          )}
          {over > 0 && (
            <p className="max-sessions-over">
              {t('session.maxSessionsOver', { over })}
            </p>
          )}
          {bulkCount > 0 && (
            <button
              type="button"
              className="btn primary max-sessions-bulk-btn"
              disabled={bulkArchiving || archivingId !== null}
              onClick={() => void handleBulkArchive()}
            >
              {bulkArchiving
                ? t('session.autoArchiveBulkRunning')
                : t('session.autoArchiveBulk', { count: bulkCount })}
            </button>
          )}
          <ol className="max-sessions-steps">
            <li>{t('session.maxSessionsStep1')}</li>
            <li>{t('session.maxSessionsStep2')}</li>
            <li>{t('session.maxSessionsStep3')}</li>
          </ol>
          {candidates.length > 0 && (
            <section className="max-sessions-candidates" aria-label={t('session.maxSessionsCandidatesTitle')}>
              <h4>{t('session.maxSessionsCandidatesTitle')}</h4>
              <ul className="max-sessions-candidate-list">
                {candidates.map((session) => (
                  <li key={session.id} className="max-sessions-candidate">
                    <div className="max-sessions-candidate-meta">
                      <span className="max-sessions-candidate-title">{session.title}</span>
                      <span className="muted max-sessions-candidate-time">
                        {formatDateTime(session.updatedAt)}
                      </span>
                    </div>
                    <button
                      type="button"
                      className="btn ghost sm"
                      disabled={archivingId !== null || bulkArchiving}
                      onClick={() => void handleArchive(session.id)}
                    >
                      {archivingId === session.id ? t('session.maxSessionsArchiving') : t('session.maxSessionsArchive')}
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>
        <footer className="modal-footer">
          <button type="button" className="btn ghost" onClick={onDismiss}>
            {t('session.maxSessionsDismiss')}
          </button>
        </footer>
      </div>
    </div>
  );
}

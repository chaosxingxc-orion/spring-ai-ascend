import type { AutoArchivedSession } from '../types/api';
import { t } from './i18n';

export function formatAutoArchiveNotice(archived: AutoArchivedSession[]): string {
  if (archived.length === 0) {
    return '';
  }
  if (archived.length === 1) {
    return t('session.autoArchivedOne', { title: archived[0].title });
  }
  return t('session.autoArchivedMany', { count: archived.length });
}

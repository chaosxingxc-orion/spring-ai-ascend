import type { UserAttachment } from '../types/events';
import type { MentionRef } from '../types/mention';

export interface SendMessageExtras {
  mentions?: MentionRef[];
  attachments?: UserAttachment[];
  files?: File[];
}

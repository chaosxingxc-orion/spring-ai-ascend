export type MentionType = 'file' | 'skill' | 'member' | 'connector';

export interface MentionRef {
  type: MentionType;
  id: string;
  path?: string;
  label: string;
}

export interface MentionApiPayload {
  type: MentionType;
  id: string;
  path?: string;
  label?: string;
}

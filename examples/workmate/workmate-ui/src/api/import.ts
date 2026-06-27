import type { Expert } from '../types/api';
import type { ImportValidation, SkillInfo } from '../types/market';

const API_BASE = import.meta.env.VITE_API_BASE ?? '';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `HTTP ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export interface ExpertImportPayload {
  id: string;
  name: string;
  description: string;
  expertType?: string;
  category?: string;
  tags?: string[];
  promptContent: string;
  defaultInitPrompt?: string;
}

export interface SkillUploadPayload {
  id: string;
  name: string;
  description: string;
  category?: string;
  tags?: string[];
  skillContent: string;
  install?: boolean;
}

export function validateExpertImport(payload: ExpertImportPayload): Promise<ImportValidation> {
  return request<ImportValidation>('/api/v1/experts/import/validate', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function importExpert(payload: ExpertImportPayload): Promise<Expert> {
  return request<Expert>('/api/v1/experts/import', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

/** W47-C2 — multipart zip with expert.yaml + prompt.md */
export async function importExpertZip(file: File): Promise<Expert> {
  const form = new FormData();
  form.append('file', file);
  const response = await fetch(`${API_BASE}/api/v1/experts/import/zip`, {
    method: 'POST',
    body: form,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `HTTP ${response.status}`);
  }
  return response.json() as Promise<Expert>;
}

export function validateSkillUpload(payload: SkillUploadPayload): Promise<ImportValidation> {
  return request<ImportValidation>('/api/v1/skills/upload/validate', {
    method: 'POST',
    body: JSON.stringify({ ...payload, install: payload.install ?? false }),
  });
}

export function uploadSkill(payload: SkillUploadPayload): Promise<SkillInfo> {
  return request<SkillInfo>('/api/v1/skills/upload', {
    method: 'POST',
    body: JSON.stringify({ ...payload, install: payload.install ?? false }),
  });
}

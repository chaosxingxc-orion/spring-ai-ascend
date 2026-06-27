import { describe, expect, it } from 'vitest';
import { isClientHttpError, readErrorMessage } from './httpError';

describe('readErrorMessage', () => {
  it('extracts message from JSON body', () => {
    expect(readErrorMessage('{"message":"Session busy"}')).toBe('Session busy');
  });

  it('maps bare HTTP 409', () => {
    expect(readErrorMessage('HTTP 409')).toBe('会话正在运行中，请稍后再试');
  });
});

describe('isClientHttpError', () => {
  it('detects HTTP 4xx markers', () => {
    expect(isClientHttpError('HTTP 409')).toBe(true);
    expect(isClientHttpError('network drop')).toBe(false);
  });
});

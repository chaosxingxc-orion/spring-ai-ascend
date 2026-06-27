import { useCallback, useEffect, useRef, useState, type ChangeEvent } from 'react';
import {
  canRelayStreamableSidecar,
  importAcpNdjsonFile,
  relayStreamableSidecar,
  type AcpSidecarImportResult,
} from '../lib/acpSidecarImport';

interface UseAcpSidecarImportOptions {
  sessionId?: string | null;
  onImported?: (sessionId: string, result: AcpSidecarImportResult) => void | Promise<void>;
  onError?: (message: string) => void;
}

export function useAcpSidecarImport({
  sessionId,
  onImported,
  onError,
}: UseAcpSidecarImportOptions) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const onImportedRef = useRef(onImported);
  const onErrorRef = useRef(onError);

  useEffect(() => {
    onImportedRef.current = onImported;
  }, [onImported]);

  useEffect(() => {
    onErrorRef.current = onError;
  }, [onError]);

  const pickNdjsonFile = useCallback(() => {
    if (!sessionId || busy) {
      return;
    }
    fileInputRef.current?.click();
  }, [busy, sessionId]);

  const handleFileChange = useCallback(
    async (event: ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0];
      event.target.value = '';
      if (!file || !sessionId) {
        return;
      }
      setBusy(true);
      try {
        const result = await importAcpNdjsonFile(sessionId, file);
        await onImportedRef.current?.(sessionId, result);
      } catch (err) {
        onErrorRef.current?.((err as Error).message || '导入失败');
      } finally {
        setBusy(false);
      }
    },
    [sessionId],
  );

  const relayStream = useCallback(async () => {
    if (!sessionId || busy || !canRelayStreamableSidecar()) {
      return;
    }
    setBusy(true);
    try {
      const result = await relayStreamableSidecar(sessionId);
      await onImportedRef.current?.(sessionId, result);
    } catch (err) {
      onErrorRef.current?.((err as Error).message || '拉取 sidecar 失败');
    } finally {
      setBusy(false);
    }
  }, [busy, sessionId]);

  return {
    fileInputRef,
    busy,
    canRelayStream: canRelayStreamableSidecar(),
    pickNdjsonFile,
    handleFileChange,
    relayStream,
  };
}

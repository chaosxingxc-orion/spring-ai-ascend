import { detectStructuredTable } from '../../lib/structuredPreview';
import { StructuredDataCard } from './StructuredDataCard';

interface MemoryPreviewBlockProps {
  section?: string;
  preview?: string;
}

/** 记忆/黑板 preview：优先结构化表格，否则纯文本。 */
export function MemoryPreviewBlock({ section, preview }: MemoryPreviewBlockProps) {
  if (!preview?.trim()) {
    return null;
  }
  const table = detectStructuredTable(preview);
  if (table) {
    return <StructuredDataCard title={section} table={table} />;
  }
  return (
    <p className="memory-preview-text">{preview}</p>
  );
}

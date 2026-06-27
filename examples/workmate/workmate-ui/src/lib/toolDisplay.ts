/** Strip ADR-005 session suffix for UI display: workmate_write__<uuid> → workmate_write */
export function formatToolName(toolName: string): string {
  const idx = toolName.indexOf('__');
  if (idx > 0 && toolName.startsWith('workmate_')) {
    return toolName.slice(0, idx);
  }
  return toolName;
}

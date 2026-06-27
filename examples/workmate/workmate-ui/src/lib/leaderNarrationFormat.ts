/**
 * When the leader narrates "waiting for member…" and the outcome in one bubble,
 * insert a markdown paragraph break after ellipsis clusters before the next clause.
 */
export function formatLeaderNarrationText(text: string): string {
  if (!text || (!text.includes('...') && !text.includes('…'))) {
    return text;
  }
  return text.replace(/([.。…]{2,})(?=[\u4e00-\u9fff「【“"])/gu, '$1\n\n');
}

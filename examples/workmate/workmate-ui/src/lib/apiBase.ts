/** Resolve mock OAuth authorize path against API base (Web / Electron packaged). */
export function resolveApiAuthorizeUrl(authorizePath: string): string {
  if (authorizePath.startsWith('http://') || authorizePath.startsWith('https://')) {
    return authorizePath;
  }
  const base = (import.meta.env.VITE_API_BASE ?? '').replace(/\/$/, '');
  return `${base}${authorizePath.startsWith('/') ? '' : '/'}${authorizePath}`;
}

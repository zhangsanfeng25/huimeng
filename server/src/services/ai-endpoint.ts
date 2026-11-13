export function completionUrl(endpoint: string) {
  const url = new URL(endpoint);
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) {
    throw new Error('INVALID_AI_ENDPOINT');
  }
  const path = url.pathname.replace(/\/+$/, '');
  url.pathname = path.endsWith('/chat/completions') ? path : path + '/chat/completions';
  return url;
}

export function sameAiEndpoint(first: string, second: string) {
  if (!first || !second) return false;
  try { return completionUrl(first).href === completionUrl(second).href; }
  catch { return false; }
}

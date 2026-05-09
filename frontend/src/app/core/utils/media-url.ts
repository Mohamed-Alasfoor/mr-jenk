const MANAGED_MEDIA_PATH_PATTERN = /^\/(?:api\/)?media\/images\/([^/?#]+)$/i;
const LOCALHOST_NAMES = new Set(['localhost', '127.0.0.1']);
const FALLBACK_ORIGIN = 'http://buy01.local';

function extractManagedMediaId(url: string): string | null {
  try {
    const parsed = new URL(url, FALLBACK_ORIGIN);
    const pathMatch = parsed.pathname.match(MANAGED_MEDIA_PATH_PATTERN);

    if (!pathMatch) {
      return null;
    }

    if (parsed.origin === FALLBACK_ORIGIN) {
      return pathMatch[1];
    }

    if (LOCALHOST_NAMES.has(parsed.hostname.toLowerCase())) {
      return pathMatch[1];
    }

    if (typeof window !== 'undefined' && parsed.origin === window.location.origin) {
      return pathMatch[1];
    }

    return null;
  } catch {
    return null;
  }
}

export function normalizeManagedMediaUrl(url?: string | null): string | undefined {
  const normalizedUrl = url?.trim();
  if (!normalizedUrl) {
    return undefined;
  }

  const mediaId = extractManagedMediaId(normalizedUrl);
  return mediaId ? `/api/media/images/${mediaId}` : normalizedUrl;
}

export function normalizeManagedMediaUrls(urls?: readonly (string | null | undefined)[] | null): string[] {
  return (urls ?? []).flatMap((url) => {
    const normalizedUrl = normalizeManagedMediaUrl(url);
    return normalizedUrl ? [normalizedUrl] : [];
  });
}

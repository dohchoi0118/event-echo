import { parsePageView } from './types';
import type {
  Gender, GuestbookEntry, OwnerGuestbookEntry, OwnerPageDetail, OwnerPageSummary,
  PageCreatePayload, PageStats, PageView,
} from './types';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: unknown,
  ) {
    super(`API request failed with status ${status}`);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    credentials: 'same-origin',
    ...init,
    headers: { Accept: 'application/json', ...init.headers },
  });
  if (!response.ok) {
    let body: unknown = null;
    try {
      body = await response.json();
    } catch {
      // Non-JSON error body: keep null.
    }
    throw new ApiError(response.status, body);
  }
  // Empty-body success responses (204 No Content from PATCH/DELETE/logout, and 202 Accepted from
  // the magic-link request) have nothing for response.json() to parse — read as text first and
  // only parse when non-empty, rather than special-casing status 204 alone.
  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  try {
    return JSON.parse(text) as T;
  } catch {
    throw new ApiError(response.status, null);
  }
}

const pagePath = (slug: string) => `/api/pages/${encodeURIComponent(slug)}`;

function postJson<T>(path: string, payload: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: payload === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: payload === undefined ? undefined : JSON.stringify(payload),
  });
}

export async function getPage(slug: string): Promise<PageView> {
  return parsePageView(await request<unknown>(pagePath(slug), { cache: 'no-store' }));
}

/**
 * Posts a guess. A duplicate (409 carrying the earlier `guessedGender`) is not an error for the UI:
 * the guest simply proceeds with the guess they already made. A 409 without one means the page is
 * not open and is rethrown.
 */
export async function submitGuess(
  slug: string,
  gender: Gender,
): Promise<{ guessedGender: Gender; alreadyGuessed: boolean }> {
  try {
    // Backend route is plural: GuessController's @RequestMapping is "/api/pages/{slug}/guesses".
    const created = await postJson<{ guessedGender: Gender }>(`${pagePath(slug)}/guesses`, { guessedGender: gender });
    return { guessedGender: created.guessedGender, alreadyGuessed: false };
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      const earlier = (error.body as { guessedGender?: unknown } | null)?.guessedGender;
      if (earlier === 'boy' || earlier === 'girl') {
        return { guessedGender: earlier, alreadyGuessed: true };
      }
    }
    throw error;
  }
}

export function getGuestbook(slug: string): Promise<GuestbookEntry[]> {
  return request<GuestbookEntry[]>(`${pagePath(slug)}/guestbook`, { cache: 'no-store' });
}

export function postGuestbook(
  slug: string,
  entry: { nickname: string; message: string },
): Promise<GuestbookEntry> {
  return postJson<GuestbookEntry>(`${pagePath(slug)}/guestbook`, entry);
}

export function requestMagicLink(email: string): Promise<void> {
  return postJson<void>('/api/auth/magic-link', { email });
}

export function getMe(): Promise<{ email: string }> {
  return request<{ email: string }>('/api/auth/me', { cache: 'no-store' });
}

export function logout(): Promise<void> {
  return postJson<void>('/api/auth/logout', undefined);
}

export function listOwnerPages(): Promise<OwnerPageSummary[]> {
  return request<OwnerPageSummary[]>('/api/owner/pages', { cache: 'no-store' });
}

export function getOwnerPageDetail(slug: string): Promise<OwnerPageDetail> {
  return request<OwnerPageDetail>(`/api/owner/pages/${encodeURIComponent(slug)}`, { cache: 'no-store' });
}

export function getOwnerStats(slug: string): Promise<PageStats> {
  return request<PageStats>(`/api/owner/pages/${encodeURIComponent(slug)}/stats`, { cache: 'no-store' });
}

export function extendPage(slug: string): Promise<OwnerPageSummary> {
  return postJson<OwnerPageSummary>(`/api/owner/pages/${encodeURIComponent(slug)}/extend`, undefined);
}

export function createPage(payload: PageCreatePayload): Promise<{ slug: string }> {
  return postJson<{ slug: string }>('/api/pages', payload);
}

export function getOwnerGuestbook(slug: string): Promise<OwnerGuestbookEntry[]> {
  return request<OwnerGuestbookEntry[]>(`/api/owner/pages/${encodeURIComponent(slug)}/guestbook`, { cache: 'no-store' });
}

export function setGuestbookHidden(slug: string, entryId: number, hidden: boolean): Promise<void> {
  return request<void>(`/api/owner/pages/${encodeURIComponent(slug)}/guestbook/${entryId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ hidden }),
  });
}

export function deleteGuestbookEntry(slug: string, entryId: number): Promise<void> {
  return request<void>(`/api/owner/pages/${encodeURIComponent(slug)}/guestbook/${entryId}`, { method: 'DELETE' });
}

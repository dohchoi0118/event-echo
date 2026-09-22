import { parsePageView } from './types';
import type { Gender, GuestbookEntry, PageView } from './types';

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
  return (await response.json()) as T;
}

const pagePath = (slug: string) => `/api/pages/${encodeURIComponent(slug)}`;

function postJson<T>(path: string, payload: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
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

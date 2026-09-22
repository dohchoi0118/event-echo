export type Gender = 'boy' | 'girl';
export type Theme = 'box' | 'cake' | 'balloon';

export type OpenPageView = {
  status: 'open';
  nickname: string;
  actualGender: Gender;
  dueDate: string | null;
  message: string | null;
  theme: Theme;
};

export type PageView =
  | { status: 'secret'; nickname: string }
  | { status: 'expired'; nickname: string }
  | OpenPageView;

export type GuestbookEntry = {
  nickname: string;
  message: string;
  createdAt: string;
};

export type OwnerPageSummary = {
  slug: string;
  nickname: string;
  status: 'secret' | 'open' | 'expired';
  revealAt: string;
  expiresAt: string;
  extended: boolean;
  theme: Theme;
};

export type OwnerPageDetail = {
  slug: string;
  nickname: string;
  actualGender: Gender;
  dueDate: string | null;
  message: string | null;
  theme: Theme;
  bgmEnabled: boolean;
  status: 'secret' | 'open' | 'expired';
  revealAt: string;
  createdAt: string;
  expiresAt: string;
  extended: boolean;
};

export type PageStats = {
  visitors: number;
  guessers: number;
  boyGuesses: number;
  girlGuesses: number;
};

export type OwnerGuestbookEntry = {
  id: number;
  nickname: string;
  message: string;
  createdAt: string;
  hidden: boolean;
  guessedGender?: Gender;
  guessCorrect?: boolean;
};

export type PageCreatePayload = {
  nickname: string;
  actualGender: Gender;
  revealAt: string;
  dueDate: string | null;
  message: string | null;
  theme: Theme;
  bgmEnabled: boolean;
  slug?: string;
};

/** Validates the API payload; secret/expired pages must never carry gender-bearing fields into the UI. */
export function parsePageView(raw: unknown): PageView {
  if (typeof raw !== 'object' || raw === null) throw new Error('Unexpected page payload');
  const r = raw as Record<string, unknown>;
  const nickname = typeof r.nickname === 'string' ? r.nickname : '';
  if (r.status === 'secret') return { status: 'secret', nickname };
  if (r.status === 'expired') return { status: 'expired', nickname };
  if (r.status === 'open') {
    if (r.actualGender !== 'boy' && r.actualGender !== 'girl') throw new Error('Unexpected page payload');
    if (r.theme !== 'box' && r.theme !== 'cake' && r.theme !== 'balloon') throw new Error('Unexpected page payload');
    return {
      status: 'open',
      nickname,
      actualGender: r.actualGender,
      dueDate: typeof r.dueDate === 'string' ? r.dueDate : null,
      message: typeof r.message === 'string' && r.message.trim() ? r.message : null,
      theme: r.theme,
    };
  }
  throw new Error('Unexpected page status');
}

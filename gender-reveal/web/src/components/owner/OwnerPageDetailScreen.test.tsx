import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OwnerPageDetailScreen, slugFromSearch } from './OwnerPageDetailScreen';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return {
    ...actual,
    getOwnerPageDetail: vi.fn(),
    getOwnerStats: vi.fn(),
    extendPage: vi.fn(),
    getOwnerGuestbook: vi.fn(),
  };
});

const getOwnerPageDetail = vi.mocked(api.getOwnerPageDetail);
const getOwnerStats = vi.mocked(api.getOwnerStats);
const extendPage = vi.mocked(api.extendPage);
const getOwnerGuestbook = vi.mocked(api.getOwnerGuestbook);

const detail = {
  slug: 'my-slug', nickname: '뽀튼이', actualGender: 'girl' as const, dueDate: '2026-11-03',
  message: null, theme: 'box' as const, bgmEnabled: false, status: 'open' as const,
  revealAt: '2026-10-01T09:00:00.000Z', createdAt: '2026-09-01T00:00:00.000Z',
  expiresAt: '2026-10-31T00:00:00.000Z', extended: false,
};
const stats = { visitors: 128, guessers: 64, boyGuesses: 32, girlGuesses: 32 };

beforeEach(() => {
  getOwnerPageDetail.mockReset();
  getOwnerStats.mockReset();
  extendPage.mockReset();
  getOwnerGuestbook.mockReset();
  getOwnerGuestbook.mockResolvedValue([]);
});

describe('slugFromSearch', () => {
  it('reads slug from a query string', () => {
    expect(slugFromSearch('?slug=my-slug')).toBe('my-slug');
    expect(slugFromSearch('?slug=a%20b')).toBe('a b');
  });

  it('returns null when there is no slug', () => {
    expect(slugFromSearch('')).toBeNull();
    expect(slugFromSearch('?other=1')).toBeNull();
  });
});

describe('OwnerPageDetailScreen', () => {
  it('shows the header, actual gender and stats', async () => {
    getOwnerPageDetail.mockResolvedValue(detail);
    getOwnerStats.mockResolvedValue(stats);

    render(<OwnerPageDetailScreen slug="my-slug" />);

    expect(await screen.findByText('뽀튼이의 페이지')).toBeInTheDocument();
    expect(screen.getByText('여아')).toBeInTheDocument();
    expect(screen.getByText('128')).toBeInTheDocument();
    expect(screen.getByText('64명')).toBeInTheDocument();
    expect(screen.getByText('32 / 32')).toBeInTheDocument();
  });

  it('shows extend as available and lets the owner extend once', async () => {
    getOwnerPageDetail.mockResolvedValue(detail);
    getOwnerStats.mockResolvedValue(stats);
    extendPage.mockResolvedValue({ slug: 'my-slug', nickname: '뽀튼이', status: 'open', revealAt: 'x', expiresAt: 'y', extended: true, theme: 'box' });
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    render(<OwnerPageDetailScreen slug="my-slug" />);

    expect(await screen.findByText('미사용 (1회 가능)')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '30일 연장하기' }));

    expect(window.confirm).toHaveBeenCalledWith('한 번 연장하면 되돌릴 수 없어요. 30일을 연장할까요?');
    await waitFor(() => expect(screen.getByText('사용함')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '30일 연장하기' })).not.toBeInTheDocument();
  });

  it('does not extend when the confirmation is declined', async () => {
    getOwnerPageDetail.mockResolvedValue(detail);
    getOwnerStats.mockResolvedValue(stats);
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    render(<OwnerPageDetailScreen slug="my-slug" />);

    await user.click(await screen.findByRole('button', { name: '30일 연장하기' }));

    expect(extendPage).not.toHaveBeenCalled();
  });

  it('already-extended pages show no extend button', async () => {
    getOwnerPageDetail.mockResolvedValue({ ...detail, extended: true });
    getOwnerStats.mockResolvedValue(stats);

    render(<OwnerPageDetailScreen slug="my-slug" />);

    expect(await screen.findByText('사용함')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '30일 연장하기' })).not.toBeInTheDocument();
  });
});

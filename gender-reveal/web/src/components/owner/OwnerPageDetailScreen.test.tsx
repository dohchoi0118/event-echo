import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OwnerPageDetailScreen, slugFromSearch } from './OwnerPageDetailScreen';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return {
    ...actual,
    getMe: vi.fn(),
    getOwnerPageDetail: vi.fn(),
    getOwnerStats: vi.fn(),
    extendPage: vi.fn(),
    getOwnerGuestbook: vi.fn(),
    deletePage: vi.fn(),
  };
});

const getMe = vi.mocked(api.getMe);
const getOwnerPageDetail = vi.mocked(api.getOwnerPageDetail);
const getOwnerStats = vi.mocked(api.getOwnerStats);
const extendPage = vi.mocked(api.extendPage);
const getOwnerGuestbook = vi.mocked(api.getOwnerGuestbook);
const deletePage = vi.mocked(api.deletePage);

let assignedHref = '';

const detail = {
  slug: 'my-slug', nickname: '뽀튼이', actualGender: 'girl' as const, dueDate: '2026-11-03',
  message: null, theme: 'box' as const, bgmEnabled: false, status: 'open' as const,
  revealAt: '2026-10-01T09:00:00.000Z', createdAt: '2026-09-01T00:00:00.000Z',
  expiresAt: '2026-10-31T00:00:00.000Z', extended: false,
};
const stats = { visitors: 128, guessers: 64, boyGuesses: 32, girlGuesses: 32 };

beforeEach(() => {
  getMe.mockReset();
  getOwnerPageDetail.mockReset();
  getOwnerStats.mockReset();
  extendPage.mockReset();
  getOwnerGuestbook.mockReset();
  getOwnerGuestbook.mockResolvedValue([]);
  deletePage.mockReset();
  getMe.mockResolvedValue({ email: 'owner@example.com' });
  assignedHref = '';
  vi.spyOn(window, 'location', 'get').mockReturnValue({
    ...window.location,
    set href(v: string) { assignedHref = v; },
    get href() { return assignedHref; },
  } as unknown as Location);
});

afterEach(() => vi.restoreAllMocks());

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
  it('shows the header and stats, with the actual gender masked until clicked', async () => {
    getOwnerPageDetail.mockResolvedValue(detail);
    getOwnerStats.mockResolvedValue(stats);
    const user = userEvent.setup();

    render(<OwnerPageDetailScreen slug="my-slug" />);

    expect(await screen.findByText('뽀튼이의 페이지')).toBeInTheDocument();
    expect(screen.getByText('128')).toBeInTheDocument();
    expect(screen.getByText('64명')).toBeInTheDocument();
    expect(screen.getByText('32 / 32')).toBeInTheDocument();

    expect(screen.queryByText('여아')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '클릭해서 보기' }));
    expect(screen.getByText('여아')).toBeInTheDocument();
  });

  it('offers an edit link for the actual gender once it has been set', async () => {
    getOwnerPageDetail.mockResolvedValue(detail);
    getOwnerStats.mockResolvedValue(stats);

    render(<OwnerPageDetailScreen slug="my-slug" />);

    await screen.findByText('뽀튼이의 페이지');
    expect(screen.getByRole('link', { name: '수정하기' })).toHaveAttribute('href', '/gender-select?slug=my-slug');
  });

  it('offers a link to set the actual gender when it has not been set yet', async () => {
    getOwnerPageDetail.mockResolvedValue({ ...detail, actualGender: null, status: 'secret' as const });
    getOwnerStats.mockResolvedValue(stats);

    render(<OwnerPageDetailScreen slug="my-slug" />);

    await screen.findByText('뽀튼이의 페이지');
    expect(screen.getByRole('link', { name: '선택하기' })).toHaveAttribute('href', '/gender-select?slug=my-slug');
  });

  it('links to the visitor-facing page', async () => {
    getOwnerPageDetail.mockResolvedValue(detail);
    getOwnerStats.mockResolvedValue(stats);

    render(<OwnerPageDetailScreen slug="my-slug" />);

    expect(await screen.findByText('뽀튼이의 페이지')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /방문자 페이지 보기/ })).toHaveAttribute('href', '/g/my-slug');
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

  it('redirects to /login when not authenticated', async () => {
    getMe.mockReset();
    getMe.mockRejectedValue(new api.ApiError(401, null));

    render(<OwnerPageDetailScreen slug="my-slug" />);

    await waitFor(() => expect(assignedHref).toBe('/login'));
  });

  it('shows an error message and a link back to /dashboard when the fetch fails', async () => {
    getOwnerPageDetail.mockRejectedValue(new api.ApiError(404, null));
    getOwnerStats.mockResolvedValue(stats);

    render(<OwnerPageDetailScreen slug="my-slug" />);

    expect(await screen.findByRole('alert')).toHaveTextContent('페이지를 불러오지 못했어요');
    expect(screen.getByRole('link', { name: '← 내 페이지' })).toHaveAttribute('href', '/dashboard');
  });

  it('deletes the page after confirming and returns to the dashboard', async () => {
    getOwnerPageDetail.mockResolvedValue(detail);
    getOwnerStats.mockResolvedValue(stats);
    deletePage.mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    render(<OwnerPageDetailScreen slug="my-slug" />);

    await user.click(await screen.findByRole('button', { name: '페이지 삭제' }));

    expect(window.confirm).toHaveBeenCalledWith(
      '이 페이지와 모든 데이터(방문자·맞추기·방명록 기록)를 영구 삭제할까요? 되돌릴 수 없어요.',
    );
    await waitFor(() => expect(deletePage).toHaveBeenCalledWith('my-slug'));
    await waitFor(() => expect(assignedHref).toBe('/dashboard'));
  });

  it('does not delete when the confirmation is declined', async () => {
    getOwnerPageDetail.mockResolvedValue(detail);
    getOwnerStats.mockResolvedValue(stats);
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    render(<OwnerPageDetailScreen slug="my-slug" />);

    await user.click(await screen.findByRole('button', { name: '페이지 삭제' }));

    expect(deletePage).not.toHaveBeenCalled();
  });

  it('shows an error when deletion fails', async () => {
    getOwnerPageDetail.mockResolvedValue(detail);
    getOwnerStats.mockResolvedValue(stats);
    deletePage.mockRejectedValue(new api.ApiError(500, null));
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    render(<OwnerPageDetailScreen slug="my-slug" />);

    await user.click(await screen.findByRole('button', { name: '페이지 삭제' }));

    expect(await screen.findByText('잠시 후 다시 시도해 주세요')).toBeInTheDocument();
    expect(assignedHref).toBe('');
  });
});

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OwnerPageListScreen } from './OwnerPageListScreen';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, getMe: vi.fn(), listOwnerPages: vi.fn(), logout: vi.fn() };
});

const getMe = vi.mocked(api.getMe);
const listOwnerPages = vi.mocked(api.listOwnerPages);
const logout = vi.mocked(api.logout);

let assignedHref = '';

beforeEach(() => {
  getMe.mockReset();
  listOwnerPages.mockReset();
  logout.mockReset();
  assignedHref = '';
  vi.spyOn(window, 'location', 'get').mockReturnValue({
    ...window.location,
    set href(v: string) { assignedHref = v; },
    get href() { return assignedHref; },
  } as unknown as Location);
});

afterEach(() => vi.restoreAllMocks());

describe('OwnerPageListScreen', () => {
  it('redirects to /login when not authenticated', async () => {
    getMe.mockRejectedValue(new api.ApiError(401, null));

    render(<OwnerPageListScreen />);

    await waitFor(() => expect(assignedHref).toBe('/login'));
  });

  it('shows the owner email and their pages once authenticated', async () => {
    getMe.mockResolvedValue({ email: 'owner@example.com' });
    listOwnerPages.mockResolvedValue([
      { slug: 'a', nickname: '뽀튼이', status: 'open', revealAt: 'x', expiresAt: 'y', extended: false, theme: 'box' },
      { slug: 'b', nickname: '별튼이', status: 'secret', revealAt: 'x', expiresAt: 'y', extended: false, theme: 'cake' },
    ]);

    render(<OwnerPageListScreen />);

    expect(await screen.findByText('owner@example.com')).toBeInTheDocument();
    expect(screen.getByText('뽀튼이')).toBeInTheDocument();
    expect(screen.getByText('공개 중')).toBeInTheDocument();
    expect(screen.getByText('별튼이')).toBeInTheDocument();
    expect(screen.getByText('공개 대기')).toBeInTheDocument();
  });

  it('shows an empty state', async () => {
    getMe.mockResolvedValue({ email: 'owner@example.com' });
    listOwnerPages.mockResolvedValue([]);

    render(<OwnerPageListScreen />);

    expect(await screen.findByText('아직 만든 페이지가 없어요')).toBeInTheDocument();
  });

  it('navigates to the detail view on click, and to /create on the create button', async () => {
    getMe.mockResolvedValue({ email: 'owner@example.com' });
    listOwnerPages.mockResolvedValue([
      { slug: 'a', nickname: '뽀튼이', status: 'open', revealAt: 'x', expiresAt: 'y', extended: false, theme: 'box' },
    ]);
    const user = userEvent.setup();
    render(<OwnerPageListScreen />);

    await user.click(await screen.findByText('뽀튼이'));
    expect(assignedHref).toBe('/dashboard?slug=a');

    await user.click(screen.getByRole('button', { name: '새 페이지 만들기' }));
    expect(assignedHref).toBe('/create');
  });

  it('logs out and redirects to /login', async () => {
    getMe.mockResolvedValue({ email: 'owner@example.com' });
    listOwnerPages.mockResolvedValue([]);
    logout.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<OwnerPageListScreen />);

    await user.click(await screen.findByRole('button', { name: '로그아웃' }));

    expect(logout).toHaveBeenCalledTimes(1);
    expect(assignedHref).toBe('/login');
  });
});

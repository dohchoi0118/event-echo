import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GenderSelectScreen, slugFromSearch } from './GenderSelectScreen';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, getMe: vi.fn(), getOwnerPageDetail: vi.fn(), setActualGender: vi.fn() };
});

const getMe = vi.mocked(api.getMe);
const getOwnerPageDetail = vi.mocked(api.getOwnerPageDetail);
const setActualGender = vi.mocked(api.setActualGender);

let assignedHref = '';

const detail = {
  slug: 'my-slug', nickname: '뽀튼이', actualGender: null, dueDate: null,
  message: null, theme: 'box' as const, bgmEnabled: false, status: 'secret' as const,
  revealAt: '2026-10-01T09:00:00.000Z', createdAt: '2026-09-01T00:00:00.000Z',
  expiresAt: '2026-10-31T00:00:00.000Z', extended: false,
};

beforeEach(() => {
  getMe.mockReset();
  getOwnerPageDetail.mockReset();
  setActualGender.mockReset();
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
  });
});

describe('GenderSelectScreen', () => {
  it('disables the confirm button until an option is picked', async () => {
    getOwnerPageDetail.mockResolvedValue(detail);
    render(<GenderSelectScreen slug="my-slug" />);

    expect(await screen.findByRole('button', { name: '완료' })).toBeDisabled();
  });

  it('picks an image option, confirms, and masks the result until revealed', async () => {
    getOwnerPageDetail.mockResolvedValue(detail);
    setActualGender.mockResolvedValue({ ...detail, actualGender: 'girl' });
    const user = userEvent.setup();
    render(<GenderSelectScreen slug="my-slug" />);

    await user.click(await screen.findByRole('button', { name: '여아' }));
    await user.click(screen.getByRole('button', { name: '완료' }));

    await waitFor(() => expect(setActualGender).toHaveBeenCalledWith('my-slug', 'girl'));
    const savedNotice = await screen.findByText(/저장했어요/);
    expect(within(savedNotice).getByRole('button', { name: '클릭해서 보기' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '클릭해서 보기' }));
    expect(savedNotice).toHaveTextContent('여아');
  });

  it('shows the already-selected value masked when one already exists', async () => {
    getOwnerPageDetail.mockResolvedValue({ ...detail, actualGender: 'boy' });

    render(<GenderSelectScreen slug="my-slug" />);

    expect(await screen.findByText(/이미 선택됐어요/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '클릭해서 보기' })).toBeInTheDocument();
  });

  it('redirects to /login when not authenticated', async () => {
    getMe.mockReset();
    getMe.mockRejectedValue(new api.ApiError(401, null));

    render(<GenderSelectScreen slug="my-slug" />);

    await waitFor(() => expect(assignedHref).toBe('/login'));
  });
});

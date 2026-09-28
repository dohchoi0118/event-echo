import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GenderSelectScreen, slugFromSearch } from './GenderSelectScreen';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, getMe: vi.fn(), setActualGender: vi.fn() };
});

const getMe = vi.mocked(api.getMe);
const setActualGender = vi.mocked(api.setActualGender);

let assignedHref = '';

beforeEach(() => {
  getMe.mockReset();
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
  it('disables the confirm button until an image option is picked', async () => {
    render(<GenderSelectScreen slug="my-slug" />);

    expect(await screen.findByRole('button', { name: '완료' })).toBeDisabled();
  });

  it('picks an image option and returns to the dashboard after confirming', async () => {
    setActualGender.mockResolvedValue({
      slug: 'my-slug', nickname: '뽀튼이', actualGender: 'girl', dueDate: null,
      message: null, theme: 'box', bgmEnabled: false, status: 'secret',
      revealAt: 'x', createdAt: 'x', expiresAt: 'x', extended: false,
    });
    const user = userEvent.setup();
    render(<GenderSelectScreen slug="my-slug" />);

    await user.click(await screen.findByRole('button', { name: '여아' }));
    await user.click(screen.getByRole('button', { name: '완료' }));

    await waitFor(() => expect(setActualGender).toHaveBeenCalledWith('my-slug', 'girl'));
    await waitFor(() => expect(assignedHref).toBe('/dashboard?slug=my-slug'));
  });

  it('shows an error and stays on the page when saving fails', async () => {
    setActualGender.mockRejectedValue(new api.ApiError(500, null));
    const user = userEvent.setup();
    render(<GenderSelectScreen slug="my-slug" />);

    await user.click(await screen.findByRole('button', { name: '남아' }));
    await user.click(screen.getByRole('button', { name: '완료' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('잠시 후 다시 시도해 주세요');
    expect(assignedHref).toBe('');
  });

  it('redirects to /login when not authenticated', async () => {
    getMe.mockReset();
    getMe.mockRejectedValue(new api.ApiError(401, null));

    render(<GenderSelectScreen slug="my-slug" />);

    await waitFor(() => expect(assignedHref).toBe('/login'));
  });
});

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GuestPage, slugFromPathname } from './GuestPage';
import * as api from '@/lib/api';
import type { PageView } from '@/lib/types';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, getPage: vi.fn(), submitGuess: vi.fn(), getGuestbook: vi.fn(), postGuestbook: vi.fn() };
});

const getPage = vi.mocked(api.getPage);
const submitGuess = vi.mocked(api.submitGuess);
const getGuestbook = vi.mocked(api.getGuestbook);

const openPage: PageView = {
  status: 'open', nickname: '뽀튼이', actualGender: 'boy', dueDate: '2026-11-03', message: null, theme: 'box',
};

beforeEach(() => {
  getPage.mockReset();
  submitGuess.mockReset();
  getGuestbook.mockReset();
  getGuestbook.mockResolvedValue([]);
  window.history.pushState({}, '', '/g/my-slug');
});

describe('slugFromPathname', () => {
  it('extracts the slug from /g/<slug>', () => {
    expect(slugFromPathname('/g/my-slug')).toBe('my-slug');
    expect(slugFromPathname('/g/my-slug/')).toBe('my-slug');
  });

  it('returns null for other paths', () => {
    expect(slugFromPathname('/g')).toBeNull();
    expect(slugFromPathname('/g/')).toBeNull();
    expect(slugFromPathname('/x/y')).toBeNull();
    expect(slugFromPathname('/g/a/b')).toBeNull();
  });
});

describe('GuestPage', () => {
  it('loads the page by the slug in the URL', async () => {
    getPage.mockResolvedValue({ status: 'secret', nickname: '뽀튼이' });

    render(<GuestPage />);

    expect(await screen.findByText('Coming Soon')).toBeInTheDocument();
    expect(getPage).toHaveBeenCalledWith('my-slug');
  });

  it('shows the expired screen', async () => {
    getPage.mockResolvedValue({ status: 'expired', nickname: '뽀튼이' });

    render(<GuestPage />);

    expect(await screen.findByText('열람이 제한된 페이지예요')).toBeInTheDocument();
  });

  it('tells the visitor when the page does not exist', async () => {
    getPage.mockRejectedValue(new api.ApiError(404, { error: 'Page not found' }));

    render(<GuestPage />);

    expect(await screen.findByText('존재하지 않는 페이지예요')).toBeInTheDocument();
  });

  it('offers a retry after a server error', async () => {
    const user = userEvent.setup();
    getPage.mockRejectedValueOnce(new api.ApiError(500, null));
    getPage.mockResolvedValueOnce({ status: 'secret', nickname: '뽀튼이' });

    render(<GuestPage />);
    await screen.findByText('잠시 후 다시 시도해 주세요');
    await user.click(screen.getByRole('button', { name: '다시 시도' }));

    expect(await screen.findByText('Coming Soon')).toBeInTheDocument();
  });

  it('walks intro → select → result → guestbook', async () => {
    const user = userEvent.setup();
    getPage.mockResolvedValue(openPage);
    submitGuess.mockResolvedValue({ guessedGender: 'girl', alreadyGuessed: false });

    render(<GuestPage />);

    await user.click(await screen.findByRole('button', { name: '탭해서 계속하기' }));
    await user.click(await screen.findByRole('button', { name: '여자 아기' }));
    expect(submitGuess).toHaveBeenCalledWith('my-slug', 'girl');

    await user.click(await screen.findByRole('button', { name: '선물상자를 열어보세요' }));
    expect(await screen.findByText('왕자님이 찾아왔어요!', {}, { timeout: 3000 })).toBeInTheDocument();
    expect(screen.getByText('내 예측: 여아 → 결과: 오답')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다음: 축하글 남기기 ▶' }));
    expect(await screen.findByText('축하 메시지 남기기')).toBeInTheDocument();
    expect(getGuestbook).toHaveBeenCalledWith('my-slug');
  });

  it('uses the earlier guess when the guest already participated', async () => {
    const user = userEvent.setup();
    getPage.mockResolvedValue(openPage);
    submitGuess.mockResolvedValue({ guessedGender: 'boy', alreadyGuessed: true });

    render(<GuestPage />);
    await user.click(await screen.findByRole('button', { name: '탭해서 계속하기' }));
    await user.click(await screen.findByRole('button', { name: '여자 아기' }));
    await user.click(await screen.findByRole('button', { name: '선물상자를 열어보세요' }));

    expect(await screen.findByText('내 예측: 남아 → 결과: 정답', {}, { timeout: 3000 })).toBeInTheDocument();
  });

  it('shows an error on the select screen when guessing fails', async () => {
    const user = userEvent.setup();
    getPage.mockResolvedValue(openPage);
    submitGuess.mockRejectedValue(new api.ApiError(500, null));

    render(<GuestPage />);
    await user.click(await screen.findByRole('button', { name: '탭해서 계속하기' }));
    await user.click(await screen.findByRole('button', { name: '여자 아기' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('잠시 후 다시 시도해 주세요');
    expect(screen.getByRole('button', { name: '여자 아기' })).toBeEnabled();
  });

  it('reloads the page when the guess is rejected because the page is no longer open', async () => {
    const user = userEvent.setup();
    getPage.mockResolvedValueOnce(openPage);
    getPage.mockResolvedValueOnce({ status: 'expired', nickname: '뽀튼이' });
    submitGuess.mockRejectedValue(new api.ApiError(409, { error: 'Page is not open' }));

    render(<GuestPage />);
    await user.click(await screen.findByRole('button', { name: '탭해서 계속하기' }));
    await user.click(await screen.findByRole('button', { name: '여자 아기' }));

    expect(await screen.findByText('열람이 제한된 페이지예요')).toBeInTheDocument();
    await waitFor(() => expect(getPage).toHaveBeenCalledTimes(2));
  });
});

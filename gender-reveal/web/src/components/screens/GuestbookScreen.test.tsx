import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GuestbookScreen } from './GuestbookScreen';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, getGuestbook: vi.fn(), postGuestbook: vi.fn() };
});

const getGuestbook = vi.mocked(api.getGuestbook);
const postGuestbook = vi.mocked(api.postGuestbook);

const NOW = Date.parse('2026-09-21T12:00:00.000Z');
const entry = (nickname: string, message: string, msAgo: number) => ({
  nickname, message, createdAt: new Date(NOW - msAgo).toISOString(),
});

beforeEach(() => {
  getGuestbook.mockReset();
  postGuestbook.mockReset();
});

describe('GuestbookScreen', () => {
  it('lists existing messages with relative times', async () => {
    getGuestbook.mockResolvedValue([entry('이모', '축하해요!', 10_000), entry('삼촌', '고생 많으셨어요', 3 * 3_600_000)]);

    render(<GuestbookScreen slug="s" nowMs={NOW} />);

    expect(await screen.findByText('이모 · 방금 전')).toBeInTheDocument();
    expect(screen.getByText('축하해요!')).toBeInTheDocument();
    expect(screen.getByText('삼촌 · 3시간 전')).toBeInTheDocument();
    expect(getGuestbook).toHaveBeenCalledWith('s');
  });

  it('shows an empty state', async () => {
    getGuestbook.mockResolvedValue([]);

    render(<GuestbookScreen slug="s" nowMs={NOW} />);

    expect(await screen.findByText('아직 등록된 메시지가 없어요')).toBeInTheDocument();
  });

  it('renders message text literally, never as HTML', async () => {
    getGuestbook.mockResolvedValue([entry('<b>해커</b>', '<img src=x onerror=alert(1)>', 1000)]);

    const { container } = render(<GuestbookScreen slug="s" nowMs={NOW} />);

    expect(await screen.findByText('<img src=x onerror=alert(1)>')).toBeInTheDocument();
    expect(container.querySelector('img[src="x"]')).toBeNull();
    expect(container.querySelector('b')).toBeNull();
  });

  it('posts a trimmed entry, refreshes the list and clears the message', async () => {
    const user = userEvent.setup();
    getGuestbook.mockResolvedValueOnce([]);
    postGuestbook.mockResolvedValue(entry('이모', '축하해요', 0));
    getGuestbook.mockResolvedValueOnce([entry('이모', '축하해요', 0)]);
    render(<GuestbookScreen slug="s" nowMs={NOW} />);
    await screen.findByText('아직 등록된 메시지가 없어요');

    await user.type(screen.getByLabelText('닉네임'), '  이모 ');
    await user.type(screen.getByLabelText('메시지'), ' 축하해요 ');
    await user.click(screen.getByRole('button', { name: '등록' }));

    await waitFor(() => expect(postGuestbook).toHaveBeenCalledWith('s', { nickname: '이모', message: '축하해요' }));
    expect(await screen.findByText('이모 · 방금 전')).toBeInTheDocument();
    expect(screen.getByLabelText('메시지')).toHaveValue('');
    expect(screen.getByLabelText('닉네임')).toHaveValue('  이모 ');
  });

  it('refuses to submit blank fields', async () => {
    const user = userEvent.setup();
    getGuestbook.mockResolvedValue([]);
    render(<GuestbookScreen slug="s" nowMs={NOW} />);
    await screen.findByText('아직 등록된 메시지가 없어요');

    await user.click(screen.getByRole('button', { name: '등록' }));

    expect(screen.getByRole('alert')).toHaveTextContent('닉네임과 메시지를 입력해 주세요');
    expect(postGuestbook).not.toHaveBeenCalled();
  });

  it('shows a generic error when posting fails', async () => {
    const user = userEvent.setup();
    getGuestbook.mockResolvedValue([]);
    postGuestbook.mockRejectedValue(new api.ApiError(500, null));
    render(<GuestbookScreen slug="s" nowMs={NOW} />);
    await screen.findByText('아직 등록된 메시지가 없어요');

    await user.type(screen.getByLabelText('닉네임'), '이모');
    await user.type(screen.getByLabelText('메시지'), '축하해요');
    await user.click(screen.getByRole('button', { name: '등록' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('잠시 후 다시 시도해 주세요');
  });

  it('shows a load error without crashing', async () => {
    getGuestbook.mockRejectedValue(new api.ApiError(500, null));

    render(<GuestbookScreen slug="s" nowMs={NOW} />);

    expect(await screen.findByText('메시지를 불러오지 못했어요')).toBeInTheDocument();
  });
});

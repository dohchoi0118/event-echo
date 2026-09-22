import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GuestbookModerationTable } from './GuestbookModerationTable';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, getOwnerGuestbook: vi.fn(), setGuestbookHidden: vi.fn(), deleteGuestbookEntry: vi.fn() };
});

const getOwnerGuestbook = vi.mocked(api.getOwnerGuestbook);
const setGuestbookHidden = vi.mocked(api.setGuestbookHidden);
const deleteGuestbookEntry = vi.mocked(api.deleteGuestbookEntry);

const NOW = Date.parse('2026-09-21T12:00:00.000Z');

beforeEach(() => {
  getOwnerGuestbook.mockReset();
  setGuestbookHidden.mockReset();
  deleteGuestbookEntry.mockReset();
});

describe('GuestbookModerationTable', () => {
  it('lists entries with guess badges and renders text literally', async () => {
    getOwnerGuestbook.mockResolvedValue([
      { id: 1, nickname: '이모', message: '<b>축하해요</b>', createdAt: new Date(NOW - 1000).toISOString(), hidden: false, guessedGender: 'boy', guessCorrect: true },
      { id: 2, nickname: '삼촌', message: '고생하셨어요', createdAt: new Date(NOW - 2000).toISOString(), hidden: true },
    ]);

    const { container } = render(<GuestbookModerationTable slug="s" nowMs={NOW} />);

    expect(await screen.findByText('이모')).toBeInTheDocument();
    expect(screen.getByText('<b>축하해요</b>')).toBeInTheDocument();
    expect(container.querySelector('b')).toBeNull();
    expect(screen.getByText('예측: 남아 · 정답')).toBeInTheDocument();
    expect(screen.getByText('삼촌')).toBeInTheDocument();
    // Scoped to the status-badge <span> (selector: 'span'): the row action button for a
    // visible entry is also labelled literally '숨김' ("hide" the verb, vs. the badge's "hidden"
    // status noun), so an unscoped getAllByText would double-count across rows in a mixed-state
    // list. Deviation from the brief's literal assertion, noted in the task report.
    expect(screen.getAllByText('노출', { selector: 'span' })).toHaveLength(1);
    expect(screen.getAllByText('숨김', { selector: 'span' })).toHaveLength(1);
  });

  it('shows an empty state', async () => {
    getOwnerGuestbook.mockResolvedValue([]);

    render(<GuestbookModerationTable slug="s" nowMs={NOW} />);

    expect(await screen.findByText('아직 등록된 메시지가 없어요')).toBeInTheDocument();
  });

  it('hides a visible entry and shows 노출 전환 afterwards', async () => {
    getOwnerGuestbook.mockResolvedValue([
      { id: 1, nickname: '이모', message: '축하해요', createdAt: new Date(NOW).toISOString(), hidden: false },
    ]);
    setGuestbookHidden.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<GuestbookModerationTable slug="s" nowMs={NOW} />);

    await user.click(await screen.findByRole('button', { name: '숨김' }));

    expect(setGuestbookHidden).toHaveBeenCalledWith('s', 1, true);
    await waitFor(() => expect(screen.getByRole('button', { name: '노출 전환' })).toBeInTheDocument());
  });

  it('deletes an entry after confirmation and removes it from the list', async () => {
    getOwnerGuestbook.mockResolvedValue([
      { id: 1, nickname: '스팸', message: '광고', createdAt: new Date(NOW).toISOString(), hidden: false },
    ]);
    deleteGuestbookEntry.mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    render(<GuestbookModerationTable slug="s" nowMs={NOW} />);

    await user.click(await screen.findByRole('button', { name: '삭제' }));

    expect(window.confirm).toHaveBeenCalledWith('이 메시지를 삭제할까요? 되돌릴 수 없어요.');
    expect(deleteGuestbookEntry).toHaveBeenCalledWith('s', 1);
    await waitFor(() => expect(screen.queryByText('스팸')).not.toBeInTheDocument());
  });

  it('does not delete when confirmation is declined', async () => {
    getOwnerGuestbook.mockResolvedValue([
      { id: 1, nickname: '스팸', message: '광고', createdAt: new Date(NOW).toISOString(), hidden: false },
    ]);
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    render(<GuestbookModerationTable slug="s" nowMs={NOW} />);

    await user.click(await screen.findByRole('button', { name: '삭제' }));

    expect(deleteGuestbookEntry).not.toHaveBeenCalled();
  });
});

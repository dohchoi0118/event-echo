import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ResultScreen } from './ResultScreen';
import type { OpenPageView } from '@/lib/types';

const basePage: OpenPageView = {
  status: 'open', nickname: '뽀튼이', actualGender: 'boy', dueDate: null, message: null, theme: 'box',
};

beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
afterEach(() => vi.useRealTimers());

async function reveal(theme: string) {
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  await user.click(screen.getByRole('button', { name: theme }));
  // Longer than every theme's REVEAL_DELAY_MS (box, the slowest, is 1400ms).
  act(() => { vi.advanceTimersByTime(1500); });
}

describe('ResultScreen', () => {
  it('hides the result until the guest opens the box', async () => {
    render(<ResultScreen page={basePage} onNext={() => {}} />);

    expect(screen.queryByText('왕자님이 찾아왔어요!')).not.toBeInTheDocument();
    expect(screen.queryByRole('img', { name: /남아/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '선물상자를 열어보세요' })).toBeInTheDocument();

    await reveal('선물상자를 열어보세요');

    expect(screen.getByText('축하합니다')).toBeInTheDocument();
    expect(screen.getByText('왕자님이 찾아왔어요!')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '젠더 리빌 결과: 남아' })).toHaveAttribute(
      'src', '/illustrations/baby/boy.svg',
    );
  });

  it('shows the theme prop small and beside the baby image', async () => {
    render(<ResultScreen page={basePage} onNext={() => {}} />);

    await reveal('선물상자를 열어보세요');

    const propImage = document.querySelector('img[src="/illustrations/reveal/box-boy.svg"]');
    expect(propImage).not.toBeNull();
    expect(propImage).toHaveAttribute('aria-hidden');
  });

  it('uses the cake scene and princess copy for a girl', async () => {
    render(<ResultScreen page={{ ...basePage, actualGender: 'girl', theme: 'cake' }} onNext={() => {}} />);

    await reveal('케이크를 잘라보세요');

    expect(screen.getByText('공주님이 찾아왔어요!')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '젠더 리빌 결과: 여아' })).toHaveAttribute(
      'src', '/illustrations/baby/girl.svg',
    );
  });

  it('uses the balloon scene', async () => {
    render(<ResultScreen page={{ ...basePage, theme: 'balloon' }} onNext={() => {}} />);

    await reveal('풍선을 터뜨려보세요');

    expect(screen.getByRole('img', { name: '젠더 리빌 결과: 남아' })).toHaveAttribute(
      'src', '/illustrations/baby/boy.svg',
    );
  });

  it('shows the zodiac line and the owner message when present', async () => {
    render(
      <ResultScreen
        page={{ ...basePage, dueDate: '2026-11-03', message: '건강하게 만나요' }}
        onNext={() => {}}
      />,
    );

    await reveal('선물상자를 열어보세요');

    expect(screen.getByText('말띠둥이가 찾아왔어요!')).toBeInTheDocument();
    expect(screen.getByText('건강하게 만나요')).toBeInTheDocument();
  });

  it('omits the zodiac line without a due date', async () => {
    render(<ResultScreen page={basePage} onNext={() => {}} />);

    await reveal('선물상자를 열어보세요');

    expect(screen.queryByText(/둥이가 찾아왔어요/)).not.toBeInTheDocument();
  });

  it('offers the guestbook only after the reveal', async () => {
    const onNext = vi.fn();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<ResultScreen page={basePage} onNext={onNext} />);

    expect(screen.queryByRole('button', { name: 'Next' })).not.toBeInTheDocument();
    await reveal('선물상자를 열어보세요');
    await user.click(screen.getByRole('button', { name: 'Next' }));

    expect(onNext).toHaveBeenCalledTimes(1);
  });
});

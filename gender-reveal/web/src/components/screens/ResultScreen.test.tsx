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
  act(() => { vi.advanceTimersByTime(1000); });
}

describe('ResultScreen', () => {
  it('hides the result until the guest opens the box', async () => {
    render(<ResultScreen page={basePage} guess="girl" onNext={() => {}} />);

    expect(screen.queryByText('왕자님이 찾아왔어요!')).not.toBeInTheDocument();
    expect(screen.queryByRole('img', { name: /남아/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '선물상자를 열어보세요' })).toBeInTheDocument();

    await reveal('선물상자를 열어보세요');

    expect(screen.getByText('축하합니다')).toBeInTheDocument();
    expect(screen.getByText('왕자님이 찾아왔어요!')).toBeInTheDocument();
    expect(screen.getByText('내 예측: 여아 → 결과: 오답')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '젠더 리빌 결과: 남아' })).toHaveAttribute(
      'src', '/illustrations/reveal/box-boy.svg',
    );
  });

  it('uses the cake scene and princess copy for a girl', async () => {
    render(<ResultScreen page={{ ...basePage, actualGender: 'girl', theme: 'cake' }} guess="girl" onNext={() => {}} />);

    await reveal('케이크를 잘라보세요');

    expect(screen.getByText('공주님이 찾아왔어요!')).toBeInTheDocument();
    expect(screen.getByText('내 예측: 여아 → 결과: 정답')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '젠더 리빌 결과: 여아' })).toHaveAttribute(
      'src', '/illustrations/reveal/cake-girl.svg',
    );
  });

  it('uses the balloon scene', async () => {
    render(<ResultScreen page={{ ...basePage, theme: 'balloon' }} guess="boy" onNext={() => {}} />);

    await reveal('풍선을 터뜨려보세요');

    expect(screen.getByRole('img', { name: '젠더 리빌 결과: 남아' })).toHaveAttribute(
      'src', '/illustrations/reveal/balloon-boy.svg',
    );
  });

  it('shows the zodiac line and the owner message when present', async () => {
    render(
      <ResultScreen
        page={{ ...basePage, dueDate: '2026-11-03', message: '건강하게 만나요' }}
        guess="boy"
        onNext={() => {}}
      />,
    );

    await reveal('선물상자를 열어보세요');

    expect(screen.getByText('말띠둥이가 찾아왔어요!')).toBeInTheDocument();
    expect(screen.getByText('건강하게 만나요')).toBeInTheDocument();
  });

  it('omits the zodiac line without a due date', async () => {
    render(<ResultScreen page={basePage} guess="boy" onNext={() => {}} />);

    await reveal('선물상자를 열어보세요');

    expect(screen.queryByText(/둥이가 찾아왔어요/)).not.toBeInTheDocument();
  });

  it('offers the guestbook only after the reveal', async () => {
    const onNext = vi.fn();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<ResultScreen page={basePage} guess="boy" onNext={onNext} />);

    expect(screen.queryByRole('button', { name: '다음: 축하글 남기기 ▶' })).not.toBeInTheDocument();
    await reveal('선물상자를 열어보세요');
    await user.click(screen.getByRole('button', { name: '다음: 축하글 남기기 ▶' }));

    expect(onNext).toHaveBeenCalledTimes(1);
  });

  it('shows a notice when the guest already participated before', async () => {
    render(<ResultScreen page={basePage} guess="boy" onNext={() => {}} alreadyGuessed />);

    await reveal('선물상자를 열어보세요');

    expect(screen.getByText('이미 참여하셨어요 · 이전 예측을 보여드려요')).toBeInTheDocument();
  });

  it('omits the notice for a first-time guess', async () => {
    render(<ResultScreen page={basePage} guess="boy" onNext={() => {}} />);

    await reveal('선물상자를 열어보세요');

    expect(screen.queryByText('이미 참여하셨어요 · 이전 예측을 보여드려요')).not.toBeInTheDocument();
  });
});

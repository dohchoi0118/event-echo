import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginScreen } from './LoginScreen';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, requestMagicLink: vi.fn() };
});

const requestMagicLink = vi.mocked(api.requestMagicLink);

beforeEach(() => {
  requestMagicLink.mockReset();
  window.history.pushState({}, '', '/login');
});

describe('LoginScreen', () => {
  it('requests a magic link and shows the sent state', async () => {
    requestMagicLink.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<LoginScreen />);

    await user.type(screen.getByLabelText('이메일'), 'owner@example.com');
    await user.click(screen.getByRole('button', { name: '로그인 링크 받기' }));

    expect(requestMagicLink).toHaveBeenCalledWith('owner@example.com');
    expect(await screen.findByText('메일함을 확인해 주세요')).toBeInTheDocument();
    expect(screen.getByText('owner@example.com로 로그인 링크를 보냈어요. 15분 동안 유효해요.')).toBeInTheDocument();
  });

  it('rejects an obviously invalid email without calling the API', async () => {
    const user = userEvent.setup();
    render(<LoginScreen />);

    await user.type(screen.getByLabelText('이메일'), 'not-an-email');
    await user.click(screen.getByRole('button', { name: '로그인 링크 받기' }));

    expect(screen.getByRole('alert')).toHaveTextContent('올바른 이메일을 입력해 주세요');
    expect(requestMagicLink).not.toHaveBeenCalled();
  });

  it('shows the sent state even if the request technically fails (no enumeration signal)', async () => {
    requestMagicLink.mockRejectedValue(new api.ApiError(500, null));
    const user = userEvent.setup();
    render(<LoginScreen />);

    await user.type(screen.getByLabelText('이메일'), 'owner@example.com');
    await user.click(screen.getByRole('button', { name: '로그인 링크 받기' }));

    expect(await screen.findByText('메일함을 확인해 주세요')).toBeInTheDocument();
  });

  it('shows an error banner when the URL carries ?error=invalid', () => {
    window.history.pushState({}, '', '/login?error=invalid');

    render(<LoginScreen />);

    expect(screen.getByRole('alert')).toHaveTextContent('링크가 만료되었거나 이미 사용됐어요');
  });
});

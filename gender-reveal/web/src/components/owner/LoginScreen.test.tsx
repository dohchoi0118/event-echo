import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginScreen } from './LoginScreen';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, requestMagicLink: vi.fn(), getMe: vi.fn() };
});

const requestMagicLink = vi.mocked(api.requestMagicLink);
const getMe = vi.mocked(api.getMe);

beforeEach(() => {
  window.history.pushState({}, '', '/login');
  // Default: not logged in, so the ordinary login form renders (matches every pre-existing test
  // below, which exercises the form). Tests that care about the authenticated redirect override this.
  getMe.mockRejectedValue(new api.ApiError(401, null));
});

// afterEach (not beforeEach), mirroring useOwnerSession.test.ts: resetting these mocks *before* a
// test whose mock rejects made Vitest report the rejection as an unhandled rejection even though
// the hook's own .catch() genuinely handles it. Resetting after each test instead avoids that.
afterEach(() => {
  requestMagicLink.mockReset();
  getMe.mockReset();
});

describe('LoginScreen', () => {
  it('requests a magic link and shows the sent state', async () => {
    requestMagicLink.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<LoginScreen />);

    await user.type(await screen.findByLabelText('이메일'), 'owner@example.com');
    await user.click(screen.getByRole('button', { name: '로그인 링크 받기' }));

    expect(requestMagicLink).toHaveBeenCalledWith('owner@example.com');
    expect(await screen.findByText('메일함을 확인해 주세요')).toBeInTheDocument();
    expect(screen.getByText('owner@example.com로 로그인 링크를 보냈어요. 15분 동안 유효해요.')).toBeInTheDocument();
  });

  it('rejects an obviously invalid email without calling the API', async () => {
    const user = userEvent.setup();
    render(<LoginScreen />);

    await user.type(await screen.findByLabelText('이메일'), 'not-an-email');
    await user.click(screen.getByRole('button', { name: '로그인 링크 받기' }));

    expect(screen.getByRole('alert')).toHaveTextContent('올바른 이메일을 입력해 주세요');
    expect(requestMagicLink).not.toHaveBeenCalled();
  });

  it('shows the sent state even if the request technically fails (no enumeration signal)', async () => {
    requestMagicLink.mockRejectedValue(new api.ApiError(500, null));
    const user = userEvent.setup();
    render(<LoginScreen />);

    await user.type(await screen.findByLabelText('이메일'), 'owner@example.com');
    await user.click(screen.getByRole('button', { name: '로그인 링크 받기' }));

    expect(await screen.findByText('메일함을 확인해 주세요')).toBeInTheDocument();
  });

  it('shows an error banner when the URL carries ?error=invalid', async () => {
    window.history.pushState({}, '', '/login?error=invalid');

    render(<LoginScreen />);

    expect(await screen.findByRole('alert')).toHaveTextContent('링크가 만료되었거나 이미 사용됐어요');
  });

  it('redirects to /dashboard when a session already exists, without flashing the form', async () => {
    getMe.mockReset();
    getMe.mockResolvedValue({ email: 'owner@example.com' });
    const originalLocation = window.location;
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...originalLocation, href: '' },
    });

    try {
      render(<LoginScreen />);

      await waitFor(() => expect(window.location.href).toBe('/dashboard'));
      expect(screen.queryByLabelText('이메일')).not.toBeInTheDocument();
    } finally {
      Object.defineProperty(window, 'location', { configurable: true, value: originalLocation });
    }
  });

  it('renders the ordinary login form and does not redirect when there is no session', async () => {
    getMe.mockReset();
    getMe.mockRejectedValue(new api.ApiError(401, null));
    const originalLocation = window.location;
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...originalLocation, href: '' },
    });

    try {
      render(<LoginScreen />);

      expect(await screen.findByLabelText('이메일')).toBeInTheDocument();
      expect(window.location.href).toBe('');
    } finally {
      Object.defineProperty(window, 'location', { configurable: true, value: originalLocation });
    }
  });
});

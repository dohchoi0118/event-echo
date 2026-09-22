import { renderHook, waitFor } from '@testing-library/react';
import { useOwnerSession } from './useOwnerSession';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, getMe: vi.fn() };
});

const getMe = vi.mocked(api.getMe);

// afterEach (not beforeEach, as the brief originally had it): resetting getMe *before* a test whose
// mock rejects made that test spuriously fail here — Vitest reported the ApiError itself as an
// "Unhandled Rejection" even though the hook's own .catch() genuinely handles it (state does flip to
// 'unauthenticated'); the fix isn't about the hook's promise chaining (verified with several
// equivalent chaining styles), reproduces with 2+ tests sharing this mock regardless of test order,
// and stops reproducing once the reset moves to run after each test instead of before.
afterEach(() => getMe.mockReset());

describe('useOwnerSession', () => {
  it('starts loading, then reports the authenticated email', async () => {
    getMe.mockResolvedValue({ email: 'owner@example.com' });

    const { result } = renderHook(() => useOwnerSession());

    expect(result.current.status).toBe('loading');
    await waitFor(() => expect(result.current.status).toBe('authenticated'));
    expect(result.current.email).toBe('owner@example.com');
  });

  it('reports unauthenticated on a 401', async () => {
    getMe.mockRejectedValue(new api.ApiError(401, { error: 'Unauthorized' }));

    const { result } = renderHook(() => useOwnerSession());

    await waitFor(() => expect(result.current.status).toBe('unauthenticated'));
    expect(result.current.email).toBeNull();
  });

  it('treats any other failure as unauthenticated too (fail closed)', async () => {
    getMe.mockRejectedValue(new api.ApiError(500, null));

    const { result } = renderHook(() => useOwnerSession());

    await waitFor(() => expect(result.current.status).toBe('unauthenticated'));
  });
});

import { act, renderHook } from '@testing-library/react';
import { usePrefersReducedMotion } from './usePrefersReducedMotion';

function mockMatchMedia(matches: boolean) {
  let changeListener: ((event: MediaQueryListEvent) => void) | null = null;
  const mql = {
    matches,
    media: '(prefers-reduced-motion: reduce)',
    addEventListener: vi.fn((event: string, listener: (event: MediaQueryListEvent) => void) => {
      if (event === 'change') changeListener = listener;
    }),
    removeEventListener: vi.fn(),
  };
  window.matchMedia = vi.fn().mockReturnValue(mql) as unknown as typeof window.matchMedia;
  return {
    mql,
    fireChange(next: boolean) {
      mql.matches = next;
      changeListener?.({ matches: next } as MediaQueryListEvent);
    },
  };
}

describe('usePrefersReducedMotion', () => {
  it('returns true when the media query matches', () => {
    mockMatchMedia(true);

    const { result } = renderHook(() => usePrefersReducedMotion());

    expect(result.current).toBe(true);
  });

  it('returns false when the media query does not match', () => {
    mockMatchMedia(false);

    const { result } = renderHook(() => usePrefersReducedMotion());

    expect(result.current).toBe(false);
  });

  it('reacts to a change event fired on the media query list', () => {
    const { fireChange } = mockMatchMedia(false);

    const { result } = renderHook(() => usePrefersReducedMotion());
    expect(result.current).toBe(false);

    act(() => { fireChange(true); });

    expect(result.current).toBe(true);
  });
});

import { act, renderHook } from '@testing-library/react';
import { useTypewriter } from './useTypewriter';

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe('useTypewriter', () => {
  it('reveals the text one character per tick and reports done', () => {
    const { result } = renderHook(() => useTypewriter('abc', { speedMs: 50 }));

    expect(result.current).toEqual({ shown: '', done: false });
    act(() => { vi.advanceTimersByTime(50); });
    expect(result.current.shown).toBe('a');
    act(() => { vi.advanceTimersByTime(100); });
    expect(result.current).toEqual({ shown: 'abc', done: true });
  });

  it('shows everything at once when instant', () => {
    const { result } = renderHook(() => useTypewriter('abc', { instant: true }));

    expect(result.current).toEqual({ shown: 'abc', done: true });
  });
});

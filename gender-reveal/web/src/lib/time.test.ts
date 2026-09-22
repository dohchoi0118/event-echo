import { relativeTime } from './time';

const NOW = Date.parse('2026-09-21T12:00:00.000Z');
const ago = (ms: number) => new Date(NOW - ms).toISOString();

describe('relativeTime', () => {
  it('says 방금 전 under a minute', () => {
    expect(relativeTime(ago(10_000), NOW)).toBe('방금 전');
  });

  it('counts minutes, hours and days', () => {
    expect(relativeTime(ago(5 * 60_000), NOW)).toBe('5분 전');
    expect(relativeTime(ago(3 * 3_600_000), NOW)).toBe('3시간 전');
    expect(relativeTime(ago(26 * 3_600_000), NOW)).toBe('1일 전');
    expect(relativeTime(ago(10 * 86_400_000), NOW)).toBe('10일 전');
  });

  it('treats future or invalid timestamps as 방금 전', () => {
    expect(relativeTime(new Date(NOW + 60_000).toISOString(), NOW)).toBe('방금 전');
    expect(relativeTime('garbage', NOW)).toBe('방금 전');
  });
});

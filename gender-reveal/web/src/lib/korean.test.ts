import { hasFinalConsonant, topic } from './korean';

describe('hasFinalConsonant', () => {
  it('detects a trailing consonant (받침)', () => {
    expect(hasFinalConsonant('별')).toBe(true);
    expect(hasFinalConsonant('뽀튼이')).toBe(false);
  });

  it('is false for empty or non-Hangul input', () => {
    expect(hasFinalConsonant('')).toBe(false);
    expect(hasFinalConsonant('Jun')).toBe(false);
  });
});

describe('topic', () => {
  it('attaches 은/는 by final consonant', () => {
    expect(topic('뽀튼이')).toBe('뽀튼이는');
    expect(topic('별')).toBe('별은');
  });
});

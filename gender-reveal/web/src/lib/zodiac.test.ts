import { zodiacFromDueDate, zodiacFromYear, zodiacLabelKo } from './zodiac';

describe('zodiacFromYear', () => {
  it.each([
    [2020, 'rat'],
    [2021, 'ox'],
    [2022, 'tiger'],
    [2023, 'rabbit'],
    [2024, 'dragon'],
    [2025, 'snake'],
    [2026, 'horse'],
    [2027, 'sheep'],
    [2028, 'monkey'],
    [2029, 'rooster'],
    [2030, 'dog'],
    [2031, 'pig'],
    [2032, 'rat'],
  ])('%i is %s', (year, expected) => {
    expect(zodiacFromYear(year)).toBe(expected);
  });
});

describe('zodiacFromDueDate', () => {
  it('reads the year from an ISO date', () => {
    expect(zodiacFromDueDate('2026-11-03')).toBe('horse');
  });

  it('returns null when there is no usable due date', () => {
    expect(zodiacFromDueDate(null)).toBeNull();
    expect(zodiacFromDueDate(undefined)).toBeNull();
    expect(zodiacFromDueDate('')).toBeNull();
    expect(zodiacFromDueDate('not-a-date')).toBeNull();
  });
});

describe('zodiacLabelKo', () => {
  it('appends 띠 to the animal name', () => {
    expect(zodiacLabelKo('horse')).toBe('말띠');
    expect(zodiacLabelKo('rooster')).toBe('닭띠');
    expect(zodiacLabelKo('pig')).toBe('돼지띠');
  });
});

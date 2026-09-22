export type ZodiacKey =
  | 'rat' | 'ox' | 'tiger' | 'rabbit' | 'dragon' | 'snake'
  | 'horse' | 'sheep' | 'monkey' | 'rooster' | 'dog' | 'pig';

// Index = year % 12 (2020 % 12 === 4 → rat). Gregorian-year based; the lunar new year boundary is ignored.
const BY_YEAR_MOD_12: ZodiacKey[] = [
  'monkey', 'rooster', 'dog', 'pig', 'rat', 'ox',
  'tiger', 'rabbit', 'dragon', 'snake', 'horse', 'sheep',
];

export const ZODIAC_KEYS: ZodiacKey[] = [
  'rat', 'ox', 'tiger', 'rabbit', 'dragon', 'snake',
  'horse', 'sheep', 'monkey', 'rooster', 'dog', 'pig',
];

const LABEL_KO: Record<ZodiacKey, string> = {
  rat: '쥐', ox: '소', tiger: '호랑이', rabbit: '토끼', dragon: '용', snake: '뱀',
  horse: '말', sheep: '양', monkey: '원숭이', rooster: '닭', dog: '개', pig: '돼지',
};

export function zodiacFromYear(year: number): ZodiacKey {
  return BY_YEAR_MOD_12[((year % 12) + 12) % 12];
}

export function zodiacFromDueDate(dueDate: string | null | undefined): ZodiacKey | null {
  if (!dueDate) return null;
  const match = /^(\d{4})-\d{2}-\d{2}$/.exec(dueDate);
  if (!match) return null;
  return zodiacFromYear(Number(match[1]));
}

export function zodiacLabelKo(key: ZodiacKey): string {
  return `${LABEL_KO[key]}띠`;
}

const HANGUL_START = 0xac00;
const HANGUL_END = 0xd7a3;

export function hasFinalConsonant(word: string): boolean {
  const last = word.trim().slice(-1);
  if (!last) return false;
  const code = last.charCodeAt(0);
  if (code < HANGUL_START || code > HANGUL_END) return false;
  return (code - HANGUL_START) % 28 !== 0;
}

/** Attaches the topic particle 은/는 to a word ("뽀튼이" → "뽀튼이는", "별" → "별은"). */
export function topic(word: string): string {
  return word + (hasFinalConsonant(word) ? '은' : '는');
}

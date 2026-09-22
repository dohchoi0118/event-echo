import fs from 'node:fs';
import path from 'node:path';
import { babySrc, revealSrc, zodiacSrc } from './illustrations';
import { ZODIAC_KEYS } from './zodiac';

const publicDir = path.resolve(__dirname, '../../public');
const exists = (src: string) => fs.existsSync(path.join(publicDir, src));

describe('illustration paths', () => {
  it('has a file for every zodiac animal', () => {
    for (const key of ZODIAC_KEYS) {
      expect(exists(zodiacSrc(key))).toBe(true);
    }
  });

  it('has baby art for both genders', () => {
    expect(exists(babySrc('boy'))).toBe(true);
    expect(exists(babySrc('girl'))).toBe(true);
  });

  it('has reveal art for every theme and gender', () => {
    for (const theme of ['box', 'cake', 'balloon'] as const) {
      for (const gender of ['boy', 'girl'] as const) {
        expect(exists(revealSrc(theme, gender))).toBe(true);
      }
    }
  });
});

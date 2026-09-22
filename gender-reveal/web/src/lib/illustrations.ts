import type { ZodiacKey } from './zodiac';
import type { Gender, Theme } from './types';

export const zodiacSrc = (key: ZodiacKey) => `/illustrations/zodiac/${key}.svg`;
export const babySrc = (gender: Gender) => `/illustrations/baby/${gender}.svg`;
export const revealSrc = (theme: Theme, gender: Gender) => `/illustrations/reveal/${theme}-${gender}.svg`;

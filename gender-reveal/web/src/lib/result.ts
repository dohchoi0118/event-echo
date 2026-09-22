import type { Gender, Theme } from './types';

export const genderKo = (gender: Gender) => (gender === 'boy' ? '남아' : '여아');

export const revealHeadline = (gender: Gender) =>
  gender === 'boy' ? '왕자님이 찾아왔어요!' : '공주님이 찾아왔어요!';

export const guessSummary = (guess: Gender, actual: Gender) =>
  `내 예측: ${genderKo(guess)} → 결과: ${guess === actual ? '정답' : '오답'}`;

export const REVEAL_PROMPT: Record<Theme, string> = {
  box: '선물상자를 열어보세요',
  cake: '케이크를 잘라보세요',
  balloon: '풍선을 터뜨려보세요',
};

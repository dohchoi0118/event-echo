import { genderKo, revealHeadline } from './result';

describe('result copy', () => {
  it('names the genders in Korean', () => {
    expect(genderKo('boy')).toBe('남아');
    expect(genderKo('girl')).toBe('여아');
  });

  it('announces the prince or princess', () => {
    expect(revealHeadline('boy')).toBe('왕자님이 찾아왔어요!');
    expect(revealHeadline('girl')).toBe('공주님이 찾아왔어요!');
  });
});

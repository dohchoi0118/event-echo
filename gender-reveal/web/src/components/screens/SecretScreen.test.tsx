import { render, screen } from '@testing-library/react';
import { SecretScreen } from './SecretScreen';

describe('SecretScreen', () => {
  it('teases without hinting at gender', () => {
    render(<SecretScreen nickname="뽀튼이" />);

    expect(screen.getByText('Coming Soon')).toBeInTheDocument();
    expect(screen.getByText('아직 뽀튼이의 성별은')).toBeInTheDocument();
    expect(screen.getByText('비밀이에요')).toBeInTheDocument();
    expect(screen.getByText('공개 시간이 되면 이 페이지가')).toBeInTheDocument();
    expect(screen.getByText('바뀔 예정이에요')).toBeInTheDocument();
  });
});

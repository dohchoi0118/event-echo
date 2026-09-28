import { render, screen } from '@testing-library/react';
import { ThankYouScreen } from './ThankYouScreen';

describe('ThankYouScreen', () => {
  it('shows a closing thank-you message', () => {
    render(<ThankYouScreen />);

    expect(screen.getByText('함께 축하해주셔서 감사해요')).toBeInTheDocument();
  });
});

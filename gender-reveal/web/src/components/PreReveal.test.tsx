import { render, screen } from '@testing-library/react';
import { PreReveal } from './PreReveal';

describe('PreReveal', () => {
  describe('box theme', () => {
    it('hides the balloon/topper and keeps the lid still while ready', () => {
      render(<PreReveal theme="box" gender="boy" stage="ready" />);

      expect(screen.getByTestId('box-balloon')).toHaveClass('opacity-0');
      expect(screen.getByTestId('box-topper')).toHaveClass('opacity-0');
      expect(screen.getByTestId('box-lid')).not.toHaveClass('motion-safe:animate-lid-open');
    });

    it('opens the lid and floats the balloon/topper out while revealing', () => {
      render(<PreReveal theme="box" gender="girl" stage="revealing" />);

      expect(screen.getByTestId('box-lid')).toHaveClass('motion-safe:animate-lid-open');
      expect(screen.getByTestId('box-balloon')).toHaveClass('motion-safe:animate-balloon-rise');
      expect(screen.getByTestId('box-balloon')).toHaveAttribute('fill', '#E37AA6');
      expect(screen.getByTestId('box-topper')).toHaveClass('motion-safe:animate-topper-rise');
    });
  });

  describe('cake theme', () => {
    it('keeps the white top layer whole (no slide) while ready', () => {
      render(<PreReveal theme="cake" gender="boy" stage="ready" />);

      expect(screen.getByTestId('cake-left')).not.toHaveClass('motion-safe:animate-cake-slide-left');
      expect(screen.getByTestId('cake-right')).not.toHaveClass('motion-safe:animate-cake-slide-right');
      expect(screen.getByTestId('cake-filling')).toHaveAttribute('fill', '#6F86E6');
    });

    it('slides the two halves apart to reveal the gendered filling while revealing', () => {
      render(<PreReveal theme="cake" gender="girl" stage="revealing" />);

      expect(screen.getByTestId('cake-left')).toHaveClass('motion-safe:animate-cake-slide-left');
      expect(screen.getByTestId('cake-right')).toHaveClass('motion-safe:animate-cake-slide-right');
      expect(screen.getByTestId('cake-filling')).toHaveAttribute('fill', '#E37AA6');
    });
  });

  describe('balloon theme', () => {
    it('renders no confetti and a still balloon while ready', () => {
      render(<PreReveal theme="balloon" gender="boy" stage="ready" />);

      expect(screen.queryAllByTestId('confetti-piece')).toHaveLength(0);
      expect(screen.getByTestId('balloon-body')).not.toHaveClass('motion-safe:animate-balloon-pop');
    });

    it('pops the balloon and bursts confetti outward while revealing', () => {
      render(<PreReveal theme="balloon" gender="boy" stage="revealing" />);

      expect(screen.getByTestId('balloon-body')).toHaveClass('motion-safe:animate-balloon-pop');
      const confetti = screen.getAllByTestId('confetti-piece');
      expect(confetti.length).toBeGreaterThan(0);
      for (const piece of confetti) {
        expect(piece).toHaveClass('motion-safe:animate-confetti-burst');
      }
    });
  });
});

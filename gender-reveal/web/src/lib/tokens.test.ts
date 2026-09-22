import config from '../../tailwind.config';

const colors = config.theme?.extend?.colors as Record<string, string>;

describe('design tokens in tailwind config', () => {
  it('carries the CTA and gender accent colors from tokens.json', () => {
    expect(colors['accent-primary']).toBe('#F2793A');
    expect(colors['accent-boy']).toBe('#6F86E6');
    expect(colors['accent-girl']).toBe('#E37AA6');
    expect(colors['surface-50']).toBe('#FFFBF6');
  });

  it('defines the display and body font families and radius tokens', () => {
    const extend = config.theme?.extend as Record<string, Record<string, unknown>>;
    expect(extend.fontFamily.display).toEqual(['var(--font-hi-melody)', 'system-ui', 'sans-serif']);
    expect(extend.borderRadius['radius-md']).toBe('14px');
  });
});

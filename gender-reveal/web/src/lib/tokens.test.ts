import fs from 'node:fs';
import path from 'node:path';
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

  it('matches every color token in the design-system source of truth', () => {
    const tokensPath = path.resolve(__dirname, '../../../planning/design-system/project/tokens.json');
    const tokens = JSON.parse(fs.readFileSync(tokensPath, 'utf-8'));
    const sourceColors: { name: string; value: string }[] = tokens.color.tokens;

    for (const { name, value } of sourceColors) {
      if (name.startsWith('illus-')) continue; // illustration-only tokens are not ported to Tailwind
      expect(colors[name], `tailwind.config.ts is missing or wrong for token "${name}"`).toBe(value);
    }
  });
});

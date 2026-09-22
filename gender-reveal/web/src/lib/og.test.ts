import fs from 'node:fs';
import path from 'node:path';

describe('open graph image', () => {
  it('is a 1200x630 PNG', () => {
    const file = path.resolve(__dirname, '../../public/og.png');
    const bytes = fs.readFileSync(file);

    expect(bytes.subarray(0, 8).toString('hex')).toBe('89504e470d0a1a0a');
    expect(bytes.readUInt32BE(16)).toBe(1200);
    expect(bytes.readUInt32BE(20)).toBe(630);
  });
});

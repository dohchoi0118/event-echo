import sharp from 'sharp';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const WIDTH = 1200;
const HEIGHT = 630;
const ART_HEIGHT = 420;

async function art(file) {
  const rendered = await sharp(path.join(root, 'public/illustrations/baby', file), { density: 300 })
    .resize({ height: ART_HEIGHT })
    .png()
    .toBuffer();
  // Each source SVG's viewBox has its own empty headroom/footroom around the
  // character (not itself vertically centered on the visible art), so the
  // nominal resized box isn't a reliable centering reference. Trim to the
  // actual visible-content bounding box and center from that instead.
  const buffer = await sharp(rendered).trim().png().toBuffer();
  const { width, height } = await sharp(buffer).metadata();
  return { buffer, width, height };
}

const boy = await art('boy.svg');
const girl = await art('girl.svg');
const gap = 80;
const totalWidth = boy.width + gap + girl.width;
const left = Math.round((WIDTH - totalWidth) / 2);
const boyTop = Math.round((HEIGHT - boy.height) / 2);
const girlTop = Math.round((HEIGHT - girl.height) / 2);

await sharp({ create: { width: WIDTH, height: HEIGHT, channels: 4, background: '#FFFBF6' } })
  .composite([
    { input: boy.buffer, left, top: boyTop },
    { input: girl.buffer, left: left + boy.width + gap, top: girlTop },
  ])
  .png()
  .toFile(path.join(root, 'public/og.png'));

console.log('wrote public/og.png');

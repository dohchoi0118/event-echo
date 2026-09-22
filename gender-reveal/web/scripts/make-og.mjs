import sharp from 'sharp';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const WIDTH = 1200;
const HEIGHT = 630;
const ART_HEIGHT = 420;

async function art(file) {
  const buffer = await sharp(path.join(root, 'public/illustrations/baby', file), { density: 300 })
    .resize({ height: ART_HEIGHT })
    .png()
    .toBuffer();
  const { width } = await sharp(buffer).metadata();
  return { buffer, width };
}

const boy = await art('boy.svg');
const girl = await art('girl.svg');
const gap = 80;
const totalWidth = boy.width + gap + girl.width;
const left = Math.round((WIDTH - totalWidth) / 2);
const top = Math.round((HEIGHT - ART_HEIGHT) / 2);

await sharp({ create: { width: WIDTH, height: HEIGHT, channels: 4, background: '#FFFBF6' } })
  .composite([
    { input: boy.buffer, left, top },
    { input: girl.buffer, left: left + boy.width + gap, top },
  ])
  .png()
  .toFile(path.join(root, 'public/og.png'));

console.log('wrote public/og.png');

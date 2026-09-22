import type { Metadata } from 'next';
import { Hi_Melody, Noto_Sans_KR } from 'next/font/google';
import './globals.css';

const hiMelody = Hi_Melody({
  weight: '400',
  variable: '--font-hi-melody',
  display: 'swap',
  preload: false,
});

const notoSansKr = Noto_Sans_KR({
  weight: ['400', '500', '600'],
  variable: '--font-noto-sans-kr',
  display: 'swap',
  preload: false,
});

// Static export = one OG card for every shared link, so nothing here may depend on a page
// (and must never hint at gender or reveal state). NEXT_PUBLIC_SITE_URL must be the public
// origin at build time so og:image resolves to an absolute URL for crawlers.
const siteUrl = process.env.NEXT_PUBLIC_SITE_URL ?? 'http://localhost:3000';
const title = '젠더리빌 — 우리 아기는 딸일까요, 아들일까요?';
const description = '아기의 성별을 맞춰보고 함께 축하해 주세요.';

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title,
  description,
  openGraph: {
    title,
    description,
    type: 'website',
    locale: 'ko_KR',
    images: [{ url: '/og.png', width: 1200, height: 630 }],
  },
  twitter: { card: 'summary_large_image', title, description, images: ['/og.png'] },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko" className={`${hiMelody.variable} ${notoSansKr.variable}`}>
      <body>{children}</body>
    </html>
  );
}

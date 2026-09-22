const isDev = process.env.NODE_ENV === 'development';
const apiOrigin = process.env.API_ORIGIN ?? 'http://localhost:8080';

/**
 * Production: static export served by nginx (see nginx/gender-reveal.conf).
 * Development: `next dev` cannot export, so rewrites stand in for nginx —
 * /api/* goes to Spring, and every /g/<slug> gets the single /g page shell
 * (the slug is read client-side from window.location).
 * @type {import('next').NextConfig}
 */
const nextConfig = isDev
  ? {
      async rewrites() {
        return [
          { source: '/api/:path*', destination: `${apiOrigin}/api/:path*` },
          { source: '/g/:slug', destination: '/g' },
        ];
      },
    }
  : { output: 'export', trailingSlash: false };

export default nextConfig;

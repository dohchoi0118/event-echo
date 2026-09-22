import type { Metadata } from 'next';
import { GuestPage } from '@/components/GuestPage';

// Guest pages are private-by-link; keep them out of search indexes.
export const metadata: Metadata = { robots: { index: false, follow: false } };

export default function Page() {
  return <GuestPage />;
}

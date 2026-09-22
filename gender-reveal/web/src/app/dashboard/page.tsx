import type { Metadata } from 'next';
import { OwnerPageListScreen } from '@/components/owner/OwnerPageListScreen';

export const metadata: Metadata = { robots: { index: false, follow: false } };

export default function Page() {
  return <OwnerPageListScreen />;
}

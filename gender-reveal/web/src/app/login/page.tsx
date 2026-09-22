import type { Metadata } from 'next';
import { LoginScreen } from '@/components/owner/LoginScreen';

export const metadata: Metadata = { robots: { index: false, follow: false } };

export default function Page() {
  return <LoginScreen />;
}

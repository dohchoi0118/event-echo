'use client';

import { useEffect, useState } from 'react';
import { OwnerPageDetailScreen, slugFromSearch } from '@/components/owner/OwnerPageDetailScreen';
import { OwnerPageListScreen } from '@/components/owner/OwnerPageListScreen';

export default function Page() {
  const [slug, setSlug] = useState<string | null | undefined>(undefined);

  useEffect(() => {
    setSlug(slugFromSearch(window.location.search));
  }, []);

  if (slug === undefined) {
    return null;
  }
  return slug ? <OwnerPageDetailScreen slug={slug} /> : <OwnerPageListScreen />;
}

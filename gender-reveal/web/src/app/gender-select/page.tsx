'use client';

import { useEffect, useState } from 'react';
import { GenderSelectScreen, slugFromSearch } from '@/components/owner/GenderSelectScreen';

export default function Page() {
  const [slug, setSlug] = useState<string | null | undefined>(undefined);

  useEffect(() => {
    setSlug(slugFromSearch(window.location.search));
  }, []);

  if (!slug) {
    return null;
  }
  return <GenderSelectScreen slug={slug} />;
}

import type { ReactNode } from 'react';

export function Screen({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <main
      className={`relative mx-auto flex min-h-screen w-full max-w-[420px] flex-col items-center justify-center gap-space-4 overflow-hidden px-space-3 py-space-5 text-center ${className}`}
    >
      {children}
    </main>
  );
}

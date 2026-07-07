import type { ReactNode } from 'react'
import logo from '@/assets/logo.svg'

// Minimal guest-facing shell: a branded header, no navigation. The admin
// Navbar (with auth-gated links) deliberately does not belong here.
export default function PublicLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <header className="w-full rounded-b-2xl border-b border-border bg-bg shadow-[var(--shadow)]">
        <div className="mx-auto flex h-14 w-full max-w-6xl items-center px-6">
          <span className="flex items-center gap-2 text-base font-semibold text-text-h">
            <img src={logo} alt="" className="h-4 w-auto" />
            FoundFlow
          </span>
        </div>
      </header>
      <div className="flex flex-1 flex-col">{children}</div>
    </>
  )
}

import { useCallback, useState, type ReactNode } from 'react'
import { uid } from '@/lib/uid'
import { ToastContext, type Toast, type ToastOptions, type ToastVariant } from './toast-context'

const DURATION_MS = 4000

// Per-variant accent stripe + icon. Colours reuse Tailwind's default palette,
// matching the inline status colours elsewhere in the app (MatchCard, intake).
const variantStyles: Record<ToastVariant, { accent: string; icon: string }> = {
  success: { accent: 'border-l-green-500', icon: '✓' },
  error: { accent: 'border-l-red-500', icon: '✕' },
  warning: { accent: 'border-l-amber-500', icon: '!' },
}

function ToastItem({ toast }: { toast: Toast }) {
  const { accent, icon } = variantStyles[toast.variant]

  return (
    <div
      // role=alert announces errors assertively; success politely.
      role={toast.variant === 'error' ? 'alert' : 'status'}
      className={`flex items-start gap-3 rounded-lg border border-border border-l-4 ${accent} bg-bg px-4 py-3 shadow-[var(--shadow)]`}
    >
      <span aria-hidden className="mt-0.5 font-semibold text-text-h">
        {icon}
      </span>
      <p className="flex-1 text-sm text-text-h">{toast.message}</p>
    </div>
  )
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])

  const show = useCallback((message: string, options: ToastOptions = {}) => {
    const { variant = 'success' } = options
    const id = uid()

    setToasts((prev) => [...prev, { id, variant, message }])
    // Toasts only auto-dismiss — there's no manual close.
    setTimeout(() => {
      setToasts((prev) => prev.filter((toast) => toast.id !== id))
    }, DURATION_MS)
  }, [])

  return (
    <ToastContext.Provider value={{ show }}>
      {children}
      {/* Fixed live region, stacked top-right, above app chrome. */}
      <div
        aria-live="polite"
        className="pointer-events-none fixed right-4 top-4 z-50 flex w-full max-w-sm flex-col gap-2"
      >
        {toasts.map((toast) => (
          <ToastItem key={toast.id} toast={toast} />
        ))}
      </div>
    </ToastContext.Provider>
  )
}

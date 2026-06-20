import { createContext, useContext } from 'react'

// Variants map to colour + icon. Kept small on purpose — these are the only
// two feedback states the staff app needs (mirrors the inline status colours
// already used in FoundItemIntake / MatchCard).
export type ToastVariant = 'success' | 'error'

export type Toast = {
  id: string
  variant: ToastVariant
  message: string
}

// What callers may pass to show(): everything except the id, which the
// provider generates so two identical messages never collide.
export type ToastOptions = {
  variant?: ToastVariant
}

export type ToastContextValue = {
  show: (message: string, options?: ToastOptions) => void
}

export const ToastContext = createContext<ToastContextValue | undefined>(undefined)

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext)
  if (!ctx) {
    throw new Error('useToast must be used within a ToastProvider')
  }
  return ctx
}

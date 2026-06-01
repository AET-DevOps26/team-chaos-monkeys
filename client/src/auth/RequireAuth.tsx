import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuth } from './useAuth'

export default function RequireAuth({ children }: { children: ReactNode }) {
  const { accessToken, status } = useAuth()
  const location = useLocation()

  // Wait for the boot-time silent refresh to resolve before deciding, so a
  // reload doesn't flash the login page for an authenticated user.
  if (status === 'loading') {
    return null
  }

  if (!accessToken) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <>{children}</>
}

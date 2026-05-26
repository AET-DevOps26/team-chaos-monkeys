import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { AuthContext, type AuthContextValue, type AuthUser } from './auth-context'
import { onUnauthorized, setCurrentToken } from './token-store'

function decodeJwt(token: string): AuthUser | null {
  try {
    const payload = token.split('.')[1]
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4)
    const claims = JSON.parse(atob(padded)) as { sub?: string; roles?: string[]; venue_id?: string }
    return {
      sub: claims.sub ?? '',
      roles: claims.roles ?? [],
      venueId: claims.venue_id ?? null,
    }
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [accessToken, setAccessToken] = useState<string | null>(null)

  const login = useCallback((token: string) => {
    setCurrentToken(token)
    setAccessToken(token)
  }, [])

  const logout = useCallback(() => {
    setCurrentToken(null)
    setAccessToken(null)
  }, [])

  useEffect(() => onUnauthorized(logout), [logout])

  const user = useMemo(() => (accessToken ? decodeJwt(accessToken) : null), [accessToken])

  const value = useMemo<AuthContextValue>(
    () => ({ accessToken, user, login, logout }),
    [accessToken, user, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { logout as logoutRequest } from '@/api/auth/auth-controller/auth-controller'
import type { TokenResponse } from '@/api/auth/model'
import { AuthContext, type AuthContextValue, type AuthStatus, type AuthUser } from './auth-context'
import {
  getRefreshToken,
  onTokenRefreshed,
  onUnauthorized,
  setCurrentToken,
  setRefreshToken,
} from './token-store'
import { refreshAccessToken } from './refresh'

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
  // Start in 'loading' only when there's a persisted refresh token to rehydrate
  // from; otherwise render the unauthenticated state synchronously.
  const [status, setStatus] = useState<AuthStatus>(() =>
    getRefreshToken() ? 'loading' : 'unauthenticated',
  )

  const login = useCallback((tokens: TokenResponse) => {
    setRefreshToken(tokens.refreshToken ?? null)
    setCurrentToken(tokens.accessToken ?? null)
    setAccessToken(tokens.accessToken ?? null)
    setStatus('authenticated')
  }, [])

  const logout = useCallback(() => {
    const refreshToken = getRefreshToken()
    if (refreshToken) {
      // Best-effort server-side revocation; local state is cleared regardless.
      logoutRequest({ refreshToken }).catch(() => {})
    }
    setCurrentToken(null)
    setRefreshToken(null)
    setAccessToken(null)
    setStatus('unauthenticated')
  }, [])

  // Rehydrate the session on boot iff a refresh token was persisted at mount.
  // The ref guard plus refresh.ts' single-flight keep React StrictMode's
  // double-invoked effect from rotating the token twice.
  const bootstrapped = useRef(false)
  const hadRefreshTokenAtMount = useRef(getRefreshToken() !== null)
  useEffect(() => {
    if (bootstrapped.current) return
    bootstrapped.current = true
    if (!hadRefreshTokenAtMount.current) return

    refreshAccessToken()
      .then((token) => {
        setAccessToken(token)
        setStatus('authenticated')
      })
      .catch(() => {
        setAccessToken(null)
        setStatus('unauthenticated')
      })
  }, [])

  // A 401 anywhere that fails to refresh tears the session down.
  useEffect(() => onUnauthorized(logout), [logout])

  // Keep React state in sync with interceptor-driven refreshes so the decoded
  // user (roles/venue) reflects the latest access token.
  useEffect(
    () =>
      onTokenRefreshed((token) => {
        setAccessToken(token)
        setStatus('authenticated')
      }),
    [],
  )

  const user = useMemo(() => (accessToken ? decodeJwt(accessToken) : null), [accessToken])

  const value = useMemo<AuthContextValue>(
    () => ({ accessToken, user, status, login, logout }),
    [accessToken, user, status, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

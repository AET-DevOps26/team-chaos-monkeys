import { describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { http, HttpResponse } from 'msw'
import { server } from '@test/server'
import { refreshFailure, refreshSuccess } from '@test/handlers'
import { AuthProvider } from '@/auth/AuthContext'
import { useAuth } from '@/auth/useAuth'
import {
  dispatchUnauthorized,
  getCurrentToken,
  getRefreshToken,
  setRefreshToken,
} from '@/auth/token-store'
import { makeFakeJwt } from '@test/jwt'

const TOKEN = makeFakeJwt({ sub: 'user-1', roles: ['staff'], venue_id: 'venue-1' })

const tokens = (accessToken = TOKEN, refreshToken = 'refresh-1') => ({
  accessToken,
  refreshToken,
  tokenType: 'Bearer',
  expiresIn: 3600,
})

const wrapper = ({ children }: { children: ReactNode }) => <AuthProvider>{children}</AuthProvider>

describe('useAuth', () => {
  it('starts unauthenticated', () => {
    const { result } = renderHook(() => useAuth(), { wrapper })
    expect(result.current.accessToken).toBeNull()
    expect(result.current.user).toBeNull()
    expect(result.current.status).toBe('unauthenticated')
  })

  it('login() updates state, the token store, and persists the refresh token', () => {
    const { result } = renderHook(() => useAuth(), { wrapper })
    act(() => result.current.login(tokens()))
    expect(result.current.accessToken).toBe(TOKEN)
    expect(result.current.user).toEqual({ sub: 'user-1', roles: ['staff'], venueId: 'venue-1' })
    expect(result.current.status).toBe('authenticated')
    expect(getCurrentToken()).toBe(TOKEN)
    expect(getRefreshToken()).toBe('refresh-1')
  })

  it('logout() revokes server-side, then clears state and stored tokens', async () => {
    const logoutBody = vi.fn<(body: unknown) => void>()
    server.use(
      http.post('*/api/auth/logout', async ({ request }) => {
        logoutBody(await request.json())
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const { result } = renderHook(() => useAuth(), { wrapper })
    act(() => result.current.login(tokens()))
    act(() => result.current.logout())

    expect(result.current.accessToken).toBeNull()
    expect(result.current.user).toBeNull()
    expect(result.current.status).toBe('unauthenticated')
    expect(getCurrentToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
    await waitFor(() => expect(logoutBody).toHaveBeenCalledWith({ refreshToken: 'refresh-1' }))
  })

  it('logs out when a 401 dispatches the unauthorized event', () => {
    const { result } = renderHook(() => useAuth(), { wrapper })
    act(() => result.current.login(tokens()))
    act(() => dispatchUnauthorized())
    expect(result.current.accessToken).toBeNull()
    expect(getCurrentToken()).toBeNull()
  })

  it('rehydrates the session on boot from a stored refresh token', async () => {
    setRefreshToken('persisted-refresh')
    server.use(refreshSuccess(TOKEN, 'rotated-refresh'))

    const { result } = renderHook(() => useAuth(), { wrapper })
    expect(result.current.status).toBe('loading')

    await waitFor(() => expect(result.current.status).toBe('authenticated'))
    expect(result.current.accessToken).toBe(TOKEN)
    expect(getCurrentToken()).toBe(TOKEN)
    expect(getRefreshToken()).toBe('rotated-refresh')
  })

  it('ends unauthenticated when the stored refresh token is rejected on boot', async () => {
    setRefreshToken('stale-refresh')
    server.use(refreshFailure())

    const { result } = renderHook(() => useAuth(), { wrapper })
    expect(result.current.status).toBe('loading')

    await waitFor(() => expect(result.current.status).toBe('unauthenticated'))
    expect(result.current.accessToken).toBeNull()
    expect(getRefreshToken()).toBeNull()
  })

  it('ignores a boot refresh that resolves after the user logs out', async () => {
    setRefreshToken('persisted-refresh')

    let releaseRefresh!: () => void
    const refreshGate = new Promise<void>((resolve) => {
      releaseRefresh = resolve
    })
    server.use(
      http.post('*/api/auth/refresh', async () => {
        await refreshGate
        return HttpResponse.json({
          accessToken: TOKEN,
          refreshToken: 'rotated-refresh',
          tokenType: 'Bearer',
          expiresIn: 3600,
        })
      }),
    )

    const { result } = renderHook(() => useAuth(), { wrapper })
    expect(result.current.status).toBe('loading')

    act(() => result.current.logout())
    expect(result.current.status).toBe('unauthenticated')

    releaseRefresh()
    await waitFor(() => expect(getCurrentToken()).toBeNull())
    expect(result.current.status).toBe('unauthenticated')
    expect(result.current.accessToken).toBeNull()
    expect(getRefreshToken()).toBeNull()
  })

  it('throws when used outside an AuthProvider', () => {
    expect(() => renderHook(() => useAuth())).toThrow(/AuthProvider/)
  })
})

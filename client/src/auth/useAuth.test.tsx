import { describe, expect, it } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { AuthProvider } from './AuthContext'
import { useAuth } from './useAuth'
import { dispatchUnauthorized, getCurrentToken } from './token-store'
import { makeFakeJwt } from '@/test/jwt'

const TOKEN = makeFakeJwt({ sub: 'user-1', roles: ['staff'], venue_id: 'venue-1' })

const wrapper = ({ children }: { children: ReactNode }) => <AuthProvider>{children}</AuthProvider>

describe('useAuth', () => {
  it('starts unauthenticated', () => {
    const { result } = renderHook(() => useAuth(), { wrapper })
    expect(result.current.accessToken).toBeNull()
    expect(result.current.user).toBeNull()
  })

  it('login() updates context state and the module-level token store', () => {
    const { result } = renderHook(() => useAuth(), { wrapper })
    act(() => result.current.login(TOKEN))
    expect(result.current.accessToken).toBe(TOKEN)
    expect(result.current.user).toEqual({ sub: 'user-1', roles: ['staff'], venueId: 'venue-1' })
    expect(getCurrentToken()).toBe(TOKEN)
  })

  it('logout() clears state and the token store', () => {
    const { result } = renderHook(() => useAuth(), { wrapper })
    act(() => result.current.login(TOKEN))
    act(() => result.current.logout())
    expect(result.current.accessToken).toBeNull()
    expect(result.current.user).toBeNull()
    expect(getCurrentToken()).toBeNull()
  })

  it('logs out when a 401 dispatches the unauthorized event', () => {
    const { result } = renderHook(() => useAuth(), { wrapper })
    act(() => result.current.login(TOKEN))
    act(() => dispatchUnauthorized())
    expect(result.current.accessToken).toBeNull()
    expect(getCurrentToken()).toBeNull()
  })

  it('throws when used outside an AuthProvider', () => {
    expect(() => renderHook(() => useAuth())).toThrow(/AuthProvider/)
  })
})

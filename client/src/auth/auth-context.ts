import { createContext } from 'react'
import type { TokenResponse } from '@/api/auth/model'

export type AuthUser = {
  sub: string
  roles: string[]
  venueId: string | null
}

// 'loading' covers the boot window while we silently refresh from a persisted
// refresh token, so route guards can wait instead of flashing the login page.
export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

export type AuthContextValue = {
  accessToken: string | null
  user: AuthUser | null
  status: AuthStatus
  login: (tokens: TokenResponse) => void
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)

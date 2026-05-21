import { createContext } from 'react'

export type AuthUser = {
  sub: string
  roles: string[]
  venueId: string | null
}

export type AuthContextValue = {
  accessToken: string | null
  user: AuthUser | null
  login: (token: string) => void
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)

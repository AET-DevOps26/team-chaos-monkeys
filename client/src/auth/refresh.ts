import axios from 'axios'
import type { TokenResponse } from '@/api/auth/model'
import {
  dispatchTokenRefreshed,
  dispatchUnauthorized,
  getRefreshToken,
  setCurrentToken,
  setRefreshToken,
} from './token-store'

// Single in-flight refresh shared by every caller (the 401 interceptor and the
// boot rehydration). A burst of 401s, or React StrictMode's double-invoked boot
// effect, collapse to one network call and one rotation of the refresh token.
let inFlight: Promise<string> | null = null

// Deliberately uses a bare axios call rather than the orval-generated `refresh()`
// / shared `axiosInstance`: that keeps the response interceptor from re-entering
// itself when the refresh itself 401s, and avoids a custom-instance ⇄
// auth-controller import cycle.
async function performRefresh(): Promise<string> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new Error('No refresh token')
  }

  try {
    const { data } = await axios.post<TokenResponse>('/api/auth/refresh', { refreshToken })
    if (!data.accessToken || !data.refreshToken) {
      throw new Error('Malformed refresh response')
    }
    setRefreshToken(data.refreshToken)
    setCurrentToken(data.accessToken)
    dispatchTokenRefreshed(data.accessToken)
    return data.accessToken
  } catch (error) {
    // Refresh failed (expired/revoked/rotated token, or network). This is the
    // single place a session is torn down automatically.
    setCurrentToken(null)
    setRefreshToken(null)
    dispatchUnauthorized()
    throw error
  }
}

export const refreshAccessToken = (): Promise<string> => {
  if (!inFlight) {
    inFlight = performRefresh().finally(() => {
      inFlight = null
    })
  }
  return inFlight
}

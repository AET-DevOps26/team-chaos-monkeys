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

// Only a 401/403 from the refresh endpoint means the refresh token itself is
// dead (expired/revoked/rotated) — the one case where retrying can never help.
// Network errors, 5xx, and a malformed response are transient: the token may
// still be valid, so we keep it and let the next request (or reload) retry.
const isSessionDead = (error: unknown): boolean =>
  axios.isAxiosError(error) &&
  (error.response?.status === 401 || error.response?.status === 403)

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
    if (isSessionDead(error)) {
      // Refresh token rejected (expired/revoked/rotated). This is the single
      // place a session is torn down automatically.
      setCurrentToken(null)
      setRefreshToken(null)
      dispatchUnauthorized()
    } else {
      // Transient failure: drop the now-stale access token but keep the refresh
      // token so the next request — or a reload — can recover the session.
      setCurrentToken(null)
    }
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

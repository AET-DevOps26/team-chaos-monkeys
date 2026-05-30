// Token holders shared between React (AuthContext) and non-React code (axios
// interceptors, the silent-refresh helper).
//
// - Access token: in memory only. Short-lived and discarded on reload.
// - Refresh token: persisted to localStorage so the session survives a page
//   reload. AuthContext rehydrates the access token from it on boot.
//
// TODO: this poses at the moment XSS risk, but is not solvable without backend changes. 
// The robust fix is to have auth-service issue the refresh token as
// an httpOnly+Secure cookie (invisible to JS); 

let currentToken: string | null = null

export const getCurrentToken = () => currentToken
export const setCurrentToken = (token: string | null) => {
  currentToken = token
}

const REFRESH_TOKEN_KEY = 'foundflow.refreshToken'

export const getRefreshToken = (): string | null => {
  try {
    return localStorage.getItem(REFRESH_TOKEN_KEY)
  } catch {
    return null
  }
}

export const setRefreshToken = (token: string | null) => {
  try {
    if (token) {
      localStorage.setItem(REFRESH_TOKEN_KEY, token)
    } else {
      localStorage.removeItem(REFRESH_TOKEN_KEY)
    }
  } catch {
    // Storage unavailable (private mode, quota). Session degrades to in-memory.
  }
}

const UNAUTHORIZED_EVENT = 'auth:unauthorized'

export const dispatchUnauthorized = () => {
  window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
}

export const onUnauthorized = (handler: () => void): (() => void) => {
  window.addEventListener(UNAUTHORIZED_EVENT, handler)
  return () => window.removeEventListener(UNAUTHORIZED_EVENT, handler)
}

const TOKEN_REFRESHED_EVENT = 'auth:token-refreshed'

// Lets the refresh helper (non-React) push a freshly-minted access token back
// into React state so the gate and decoded user claims stay in sync.
export const dispatchTokenRefreshed = (token: string) => {
  window.dispatchEvent(new CustomEvent(TOKEN_REFRESHED_EVENT, { detail: token }))
}

export const onTokenRefreshed = (handler: (token: string) => void): (() => void) => {
  const listener = (event: Event) => handler((event as CustomEvent<string>).detail)
  window.addEventListener(TOKEN_REFRESHED_EVENT, listener)
  return () => window.removeEventListener(TOKEN_REFRESHED_EVENT, listener)
}

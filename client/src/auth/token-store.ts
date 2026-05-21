// Module-level token holder so non-React code (axios interceptors) can read
// the current token synchronously. AuthContext keeps this in sync with state.

let currentToken: string | null = null

export const getCurrentToken = () => currentToken
export const setCurrentToken = (token: string | null) => {
  currentToken = token
}

const UNAUTHORIZED_EVENT = 'auth:unauthorized'

export const dispatchUnauthorized = () => {
  window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
}

export const onUnauthorized = (handler: () => void): (() => void) => {
  window.addEventListener(UNAUTHORIZED_EVENT, handler)
  return () => window.removeEventListener(UNAUTHORIZED_EVENT, handler)
}

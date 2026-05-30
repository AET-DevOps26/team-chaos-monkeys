import axios, { type AxiosRequestConfig, type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { getCurrentToken } from '../../auth/token-store'
import { refreshAccessToken } from '../../auth/refresh'

export const axiosInstance = axios.create({
  // No baseURL: generated client paths already include the `/api` prefix
  // (e.g. `/api/found-items`), which nginx proxies straight to the gateway.
  headers: { 'Content-Type': 'application/json' },
})

// Auth endpoints whose own 401s must not trigger a refresh/retry: a bad password
// on login, or an expired/revoked refresh token. These are handled locally (the
// Login form, or refresh.ts) — never by tearing the session down here.
const isAuthBypass = (url?: string) =>
  !!url && (url.includes('/api/auth/refresh') || url.includes('/api/auth/login'))

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean }

axiosInstance.interceptors.request.use((config) => {
  const token = getCurrentToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

axiosInstance.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetriableConfig | undefined

    // Only attempt a silent refresh for a genuine "access token expired" 401:
    // not for auth-endpoint 401s, and not if we've already retried this request.
    if (error.response?.status !== 401 || !config || config._retried || isAuthBypass(config.url)) {
      return Promise.reject(error)
    }

    try {
      await refreshAccessToken()
    } catch {
      // refreshAccessToken already dispatched unauthorized → logout.
      return Promise.reject(error)
    }

    config._retried = true
    // The request interceptor re-attaches the freshly minted bearer token.
    return axiosInstance(config)
  },
)

export const customInstance = <T>(config: AxiosRequestConfig): Promise<T> => {
  if (config.data instanceof FormData) {
    config.headers = { ...config.headers, 'Content-Type': undefined }
  }
  return axiosInstance(config).then(({ data }) => data)
}

export type ErrorType<E> = AxiosError<E>
